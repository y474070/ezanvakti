package com.colak.ezanvakti

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// YENİ ÖZELLİK: Bildirim çubuğunda (notification shade) günün vakitlerini
// gösteren kalıcı (ongoing) bir bildirim. Ayarlar'dan "Bildirim Çubuğunda
// Vakitleri Göster" anahtarıyla açılıp kapatılabilir. Daraltılmışken bir
// sonraki vaktin adı/saati ve geri sayımı; genişletilmişken günün 7 vaktinin
// tamamı gösterilir (referans "Ezan Vakti Pro" uygulamasındaki gibi).
//
// Veri kaynağı olarak HomeScreen'in zaten doldurduğu SharedPreferences
// önbelleğini (diyanet_vakitler_ilce_{kod}) kullanıyor — API'yi tekrar
// çağırmıyor, sadece mevcut veriyi okuyup 30 saniyede bir ekranı tazeliyor.
class EzanBildirimServisi : Service() {

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    companion object {
        const val KANAL_ID = "ezan_vakti_bildirim_cubugu"
        const val BILDIRIM_ID = 5001

        fun baslat(context: Context) {
            val intent = Intent(context, EzanBildirimServisi::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun durdur(context: Context) {
            context.stopService(Intent(context, EzanBildirimServisi::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        kanalOlustur()

        val ilkBildirim = bildirimOlustur()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(BILDIRIM_ID, ilkBildirim, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(BILDIRIM_ID, ilkBildirim)
        }

        job = scope.launch {
            while (true) {
                try {
                    val bildirim = bildirimOlustur()
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(BILDIRIM_ID, bildirim)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                // DÜZELTME: 30sn -> 10sn. Android'in bildirim sıralaması büyük
                // ölçüde "en son ne zaman güncellendi"ye dayanıyor — daha sık
                // notify() çağırmak, bildirimin diğer (daha az sık güncellenen)
                // bildirimlerin arasında daha üstte/taze kalma ihtimalini
                // ciddi şekilde artırır. Chronometer zaten saniyeleri görsel
                // olarak akıttığı için, bu artış pil açısından hâlâ makul.
                delay(10_000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }

    private fun kanalOlustur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val kanal = NotificationChannel(
                KANAL_ID,
                "Vakit Bildirim Çubuğu",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
                description = "Günün namaz vakitlerini bildirim çubuğunda gösterir"
            }
            nm.createNotificationChannel(kanal)
        }
    }

    private data class VakitVerisi(
        val il: String, val ilce: String,
        val imsak: String, val sabah: String, val gunes: String,
        val ogle: String, val ikindi: String, val aksam: String, val yatsi: String,
        val miladiTarih: String, val hicriTarih: String
    )

    // HomeScreen.kt'deki aynı hesaplama kuralı: Sabah ezanı, Güneş'ten 60 dk önce.
    private fun saatCikar(saatStr: String, dakika: Int): String {
        val parca = saatStr.split(":")
        if (parca.size != 2) return saatStr
        val h = parca[0].toIntOrNull() ?: return saatStr
        val m = parca[1].toIntOrNull() ?: return saatStr
        var toplamDakika = h * 60 + m - dakika
        while (toplamDakika < 0) toplamDakika += 24 * 60
        val yeniSaat = (toplamDakika / 60) % 24
        val yeniDakika = toplamDakika % 60
        return String.format("%02d:%02d", yeniSaat, yeniDakika)
    }

    private fun vakitVerisiniOku(): VakitVerisi? {
        val prefs = getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE)
        val il = prefs.getString("SECILEN_IL", "") ?: ""
        val ilce = prefs.getString("SECILEN_ILCE", "") ?: ""
        val kod = prefs.getString("SECILEN_ILCE_KODU", "") ?: ""
        if (kod.isBlank()) return null

        val cacheKey = "diyanet_vakitler_ilce_$kod"
        val cachedJson = prefs.getString(cacheKey, null) ?: return null

        return try {
            val gson = Gson()
            val type = object : TypeToken<List<DiyanetVakitModel>>() {}.type
            val liste: List<DiyanetVakitModel> = gson.fromJson(cachedJson, type)

            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
            val bugunStr = dateFormat.format(Date())
            val bugun = liste.find { it.miladiTarihKisa == bugunStr } ?: liste.firstOrNull() ?: return null

            val gunes = bugun.gunes?.take(5) ?: "--:--"

            VakitVerisi(
                il = il, ilce = ilce,
                imsak = bugun.imsak?.take(5) ?: "--:--",
                sabah = saatCikar(gunes, 60),
                gunes = gunes,
                ogle = bugun.ogle?.take(5) ?: "--:--",
                ikindi = bugun.ikindi?.take(5) ?: "--:--",
                aksam = bugun.aksam?.take(5) ?: "--:--",
                yatsi = bugun.yatsi?.take(5) ?: "--:--",
                miladiTarih = bugun.miladiTarihKisa ?: "",
                hicriTarih = bugun.hicriTarihKisa ?: ""
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // HomeScreen.kt'deki aynı Hicri ay ismi çevirisi — "22.02.1448" -> "22 Safer 1448"
    private fun hicriTarihiFormatla(hamHicri: String): String {
        if (!hamHicri.contains(".")) return hamHicri.ifEmpty { "Hicri Takvim" }
        val parcalar = hamHicri.split(".")
        if (parcalar.size != 3) return hamHicri
        val gun = parcalar[0]
        val aySayi = parcalar[1]
        val yil = parcalar[2]
        val ayAdi = when (aySayi.trimStart('0').ifEmpty { "0" }) {
            "1" -> "Muharrem"; "2" -> "Safer"; "3" -> "Rebiülevvel"
            "4" -> "Rebiülahir"; "5" -> "Cemaziyelevvel"; "6" -> "Cemaziyelahir"
            "7" -> "Recep"; "8" -> "Şaban"; "9" -> "Ramazan"
            "10" -> "Şevval"; "11" -> "Zilkade"; "12" -> "Zilhicce"
            else -> aySayi
        }
        return "$gun $ayAdi $yil"
    }

    // YENİ: "Şu an İÇİNDE bulunduğumuz vakit" — en son BAŞLAMIŞ olan vakit.
    // HomeScreen.kt'deki "aktifMi" mantığıyla aynı. Bu, "sıradaki/gelecek
    // vakit" (siradakiVaktiBul — geri sayımın hedefi) ile KARIŞTIRILMAMALI.
    // Örnek: saat 10:00, Güneş 05:50'de başladı, Öğle 13:01'de henüz
    // başlamadı -> aktif olan Güneş'tir (Öğle değil).
    private fun aktifVaktiBul(veri: VakitVerisi): String {
        val simdi = Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul"))
        val simdikiDakika = simdi.get(Calendar.HOUR_OF_DAY) * 60 + simdi.get(Calendar.MINUTE)

        val vakitler = listOf(
            "İmsak" to veri.imsak, "Sabah" to veri.sabah, "Güneş" to veri.gunes,
            "Öğle" to veri.ogle, "İkindi" to veri.ikindi, "Akşam" to veri.aksam, "Yatsı" to veri.yatsi
        )

        // Gün başından önceyse (İmsak'tan önce), önceki günün Yatsı'sı hâlâ aktif kabul edilir.
        var enSonGecenIsim = vakitler.last().first
        var maxGecmisDakika = -1

        for ((isim, saat) in vakitler) {
            val parca = saat.split(":")
            if (parca.size == 2) {
                val sh = parca[0].toIntOrNull()
                val sm = parca[1].toIntOrNull()
                if (sh != null && sm != null) {
                    val vakitDakika = sh * 60 + sm
                    if (vakitDakika <= simdikiDakika && vakitDakika > maxGecmisDakika) {
                        maxGecmisDakika = vakitDakika
                        enSonGecenIsim = isim
                    }
                }
            }
        }
        return enSonGecenIsim
    }

    private fun siradakiVaktiBul(veri: VakitVerisi): Triple<String, String, Long> {
        val simdi = Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul"))
        val simdikiDakika = simdi.get(Calendar.HOUR_OF_DAY) * 60 + simdi.get(Calendar.MINUTE)
        val simdikiSaniye = simdi.get(Calendar.SECOND)

        val vakitler = listOf(
            "İmsak" to veri.imsak, "Sabah" to veri.sabah, "Güneş" to veri.gunes,
            "Öğle" to veri.ogle, "İkindi" to veri.ikindi, "Akşam" to veri.aksam, "Yatsı" to veri.yatsi
        )

        var enYakinIsim = vakitler.first().first
        var enYakinSaat = vakitler.first().second
        var minFarkSaniye = Long.MAX_VALUE

        for ((isim, saat) in vakitler) {
            val parca = saat.split(":")
            if (parca.size == 2) {
                val sh = parca[0].toIntOrNull()
                val sm = parca[1].toIntOrNull()
                if (sh != null && sm != null) {
                    val vakitDakika = sh * 60 + sm
                    var farkSaniye = (vakitDakika * 60L) - (simdikiDakika * 60L + simdikiSaniye)
                    if (farkSaniye <= 0) farkSaniye += 24 * 3600L
                    if (farkSaniye < minFarkSaniye) {
                        minFarkSaniye = farkSaniye
                        enYakinIsim = isim
                        enYakinSaat = saat
                    }
                }
            }
        }
        return Triple(enYakinIsim, enYakinSaat, minFarkSaniye)
    }

    private fun bildirimOlustur(): Notification {
        val veri = vakitVerisiniOku()

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val kompaktView = RemoteViews(packageName, R.layout.notification_vakit_kompakt)
        val genisView = RemoteViews(packageName, R.layout.notification_vakit_genis)

        // DÜZELTME: Bildirimdeki TÜM metin renkleri artık sistemin gece/gündüz
        // durumuna göre programatik olarak ayarlanıyor. Önceden sadece vakit
        // saatleri (saat_imsak vb.) bu şekilde ayarlanıyordu; başlık, alt
        // başlık ve gün isimleri (İmsak/Sabah/...) XML'deki sabit renkte
        // kalıyordu — özellikle "genis_baslik" tamamen BEYAZ (#FFFFFF) olduğu
        // için Gündüz sistem temasında (beyaz bildirim zemini) tamamen
        // görünmez oluyordu.
        val sistemGeceModuMu = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val vurguRengi = Color.parseColor("#34D399")
        val normalRengi = if (sistemGeceModuMu) Color.parseColor("#E2E8F0") else Color.parseColor("#334155")
        val baslikRengi = if (sistemGeceModuMu) Color.parseColor("#FFFFFF") else Color.parseColor("#0F172A")
        val altBaslikRengi = if (sistemGeceModuMu) Color.parseColor("#94A3B8") else Color.parseColor("#475569")
        val etiketRengi = altBaslikRengi

        // Başlık/alt başlık/etiket renklerini her iki görünüme de uyguluyoruz
        // (veri null olsa da olmasa da bu satırlar hep aynı renkleri kullanır).
        kompaktView.setTextColor(R.id.bildirim_baslik, baslikRengi)
        kompaktView.setTextColor(R.id.bildirim_bilgi_metni, altBaslikRengi)
        kompaktView.setTextColor(R.id.bildirim_sayac, vurguRengi)
        // DÜZELTME: Genişletilmiş bildirim, büyük "Diyanet tarzı" tasarımdan
        // geri alınıp kompaktın sadeliğine yakın hale getirildi —
        // "genis_baslik" tekrar ANA başlık (şehir + sıradaki vakit).
        genisView.setTextColor(R.id.genis_baslik, baslikRengi)
        genisView.setTextColor(R.id.genis_hicri_tarih, altBaslikRengi)
        val etiketIdleri = listOf(
            R.id.etiket_imsak, R.id.etiket_gunes,
            R.id.etiket_ogle, R.id.etiket_ikindi, R.id.etiket_aksam, R.id.etiket_yatsi
        )
        for (etiketId in etiketIdleri) {
            genisView.setTextColor(etiketId, etiketRengi)
        }

        if (veri == null) {
            kompaktView.setTextViewText(R.id.bildirim_baslik, "Ezan Vakti")
            kompaktView.setViewVisibility(R.id.bildirim_sayac, android.view.View.GONE)
            kompaktView.setViewVisibility(R.id.bildirim_bilgi_metni, android.view.View.VISIBLE)
            kompaktView.setTextViewText(R.id.bildirim_bilgi_metni, "Lütfen Ayarlar'dan şehir seçin")

            genisView.setTextViewText(R.id.genis_baslik, "Ezan Vakti")
            genisView.setTextViewText(R.id.genis_hicri_tarih, "Lütfen Ayarlar'dan şehir seçin")
            genisView.setViewVisibility(R.id.genis_sayac, android.view.View.GONE)
        } else {
            val (siradakiIsim, siradakiSaat, kalanSaniye) = siradakiVaktiBul(veri)
            val aktifIsim = aktifVaktiBul(veri)
            val sehirMetni = "${veri.il}, ${veri.ilce}"

            // DÜZELTME: Sayaç artık statik metin (30sn'de bir güncellenen)
            // yerine Chronometer widget'ı ile kuruluyor — bu, sistem tarafından
            // her saniye OTOMATİK render edilir. "base" değeri, geri sayımın
            // 0'a ulaşacağı gerçek zaman anıdır; countDown=true ile widget o
            // ana doğru geriye sayar.
            val sayacBase = android.os.SystemClock.elapsedRealtime() + (kalanSaniye * 1000L)

            kompaktView.setTextViewText(R.id.bildirim_baslik, "$sehirMetni • $siradakiIsim: $siradakiSaat")
            kompaktView.setViewVisibility(R.id.bildirim_bilgi_metni, android.view.View.GONE)
            kompaktView.setViewVisibility(R.id.bildirim_sayac, android.view.View.VISIBLE)
            kompaktView.setChronometer(R.id.bildirim_sayac, sayacBase, "%s kaldı", true)
            // DÜZELTME: setChronometer()'ın 4. parametresi "geri sayım" değil
            // "başlatıldı mı" anlamına geliyor. Geri sayım modu AYRI bir
            // çağrıyla açılmalı — aksi halde widget "base" (gelecekteki bir
            // an) değerinden geriye doğru negatif sayı gösteriyordu ("-31:28").
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                kompaktView.setChronometerCountDown(R.id.bildirim_sayac, true)
            }

            genisView.setTextViewText(R.id.genis_baslik, "$sehirMetni\n$siradakiIsim: $siradakiSaat")
            genisView.setTextViewText(R.id.genis_hicri_tarih, hicriTarihiFormatla(veri.hicriTarih))
            genisView.setViewVisibility(R.id.genis_sayac, android.view.View.VISIBLE)
            genisView.setChronometer(R.id.genis_sayac, sayacBase, "%s", true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                genisView.setChronometerCountDown(R.id.genis_sayac, true)
            }

            // DÜZELTME: Referans tasarımda "Sabah" ayrı gösterilmiyor —
            // İmsak/Güneş/Öğle/İkindi/Akşam/Yatsı olmak üzere 6 vakit, tek
            // satırlık kompakt bir sırada. Yatsı diğer vakitler gibi
            // aktif/pasif durumuna göre renkleniyor (sabit kırmızı değil).
            val eslesmeler = listOf(
                Triple("İmsak", veri.imsak, R.id.saat_imsak),
                Triple("Güneş", veri.gunes, R.id.saat_gunes),
                Triple("Öğle", veri.ogle, R.id.saat_ogle),
                Triple("İkindi", veri.ikindi, R.id.saat_ikindi),
                Triple("Akşam", veri.aksam, R.id.saat_aksam),
                Triple("Yatsı", veri.yatsi, R.id.saat_yatsi)
            )
            for ((isim, saat, viewId) in eslesmeler) {
                genisView.setTextViewText(viewId, saat)
                // DÜZELTME: Vurgu artık "içinde bulunduğumuz vakit" (aktifIsim)
                // kullanıyor, "sıradaki/gelecek vakit" (siradakiIsim) değil.
                genisView.setTextColor(viewId, if (isim == aktifIsim) vurguRengi else normalRengi)
            }

        }

        return NotificationCompat.Builder(this, KANAL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // DÜZELTME: Bildirim her 30 saniyede bir notify() ile yenilendiği
            // için Android otomatik olarak "şimdi" zaman damgası gösteriyordu —
            // bu da bildirimin sürekli "yeni gelmiş" gibi görünmesine yol
            // açıyordu. Zaman damgasını tamamen gizliyoruz; referans
            // uygulamada da bu yazı görünmüyor.
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setCustomContentView(kompaktView)
            .setCustomBigContentView(genisView)
            // DÜZELTME: DecoratedCustomViewStyle kaldırıldı. Bu stil, sistemin
            // kendi başlık satırını (uygulama adı + zaman damgası — "şimdi"
            // yazısının asıl kaynağı) özel içeriğimizin ÜSTÜNE ekliyordu.
            // Bu stil olmadan özel içeriğimiz (kompaktView/genisView)
            // bildirimin TAMAMINI kaplıyor, sistemin zorla eklediği üst satır
            // hiç oluşmuyor — referans uygulamadaki temiz görünüme daha yakın.
            .build()
    }
}