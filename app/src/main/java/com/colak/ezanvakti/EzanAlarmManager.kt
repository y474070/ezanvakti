package com.colak.ezanvakti

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object EzanAlarmManager {

    fun alarmKur(context: Context, vakitAdi: String, hedefSaat: Int, hedefDakika: Int, dosyaAdi: String, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, EzanAlarmReceiver::class.java).apply {
            putExtra("VAKIT_ADI", vakitAdi)
            putExtra("DOSYA_ADI", dosyaAdi)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Alarm çalınca ekrana bildirim/uygulama arayüzünü getirecek intent
        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode + 1000,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hedefSaat)
            set(Calendar.MINUTE, hedefDakika)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0) // Milimetrik hassasiyet

            // Eğer seçilen saat bugün geçtiyse yarına kur
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            // Android'in en keskin, batarya optimizasyonlarından etkilenmeyen çalar saat modu
            val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            // Alternatif güvenli geçiş
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        }
    }

    // YENİ: Sabah ezanı, Ramazan dışında Güneş'ten 60 dakika önce okunur —
    // EzanVaktiHomeScreen'deki AYNI kural.
    private const val SABAH_EZANI_GUNESTEN_ONCE_DAKIKA = 60

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

    // YENİ ÖZELLİK: Alarmların günlük olarak yenilenmediği (kod hatası değil,
    // eksik bir mekanizma) tespit edildi — bir vakit için bir kez alarm
    // kurulduktan sonra, uygulama tekrar açılmadıkça/telefon yeniden
    // başlamadıkça BİR DAHA YENİLENMİYORDU. Günler geçtikçe bu, bazı
    // vakitlerin (rastgele hangisi denk gelirse) yanlış/eski bir tarihte
    // kalmasına yol açabiliyordu. Bu fonksiyon HER GECE YARISI kendini
    // yeniden kurarak (self-perpetuating) tüm alarmları o günün taze
    // verisiyle günceller — uygulama hiç açılmasa bile çalışır.
    private const val GUNLUK_YENILEME_REQUEST_CODE = 999999
    private const val GUNLUK_YENILEME_SAATI = 0
    private const val GUNLUK_YENILEME_DAKIKASI = 5

    fun gunlukYenilemeAlarmiKur(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, EzanAlarmReceiver::class.java).apply {
                putExtra("ISLEM", "GUNLUK_YENILE")
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                GUNLUK_YENILEME_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, GUNLUK_YENILEME_SAATI)
                set(Calendar.MINUTE, GUNLUK_YENILEME_DAKIKASI)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * DÜZELTME: BootReceiver artık uygulamayı ekrana getirmiyor (startActivity
     * kaldırıldı) — telefon yeniden başladığında/uygulama güncellendiğinde
     * kullanıcıya HİÇBİR ŞEY GÖSTERMEDEN, önbellekteki (en son çekilmiş) vakit
     * verisini kullanarak tüm ezan alarmlarını arka planda SESSİZCE yeniden
     * kurar. EzanVaktiHomeScreen'deki verileriIsleVeGoster() ile AYNI
     * hesaplama mantığını (offset'ler, ses dosyaları, Ramazan/İmsak istisnası)
     * kullanır — sadece UI state'i güncellemez, sadece alarmları kurar.
     */
    fun tumAlarmlariSessizceYenidenKur(context: Context) {
        try {
            val prefs = context.getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE)
            val ilceKodu = prefs.getString("SECILEN_ILCE_KODU", "") ?: ""
            if (ilceKodu.isBlank()) return

            val cacheKey = "diyanet_vakitler_ilce_$ilceKodu"
            val cachedJson = prefs.getString(cacheKey, null) ?: return

            val liste: List<DiyanetVakitModel> = try {
                val type = object : TypeToken<List<DiyanetVakitModel>>() {}.type
                Gson().fromJson(cachedJson, type)
            } catch (e: Exception) {
                e.printStackTrace()
                return
            }
            if (liste.isEmpty()) return

            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
            val bugunTarihStr = dateFormat.format(Date())
            val bugunVakti = (liste.find { it.miladiTarihKisa == bugunTarihStr } ?: liste.firstOrNull()) ?: return

            val gosterilenImsak = bugunVakti.imsak?.take(5) ?: "00:00"
            val gosterilenGunes = bugunVakti.gunes?.take(5) ?: "00:00"
            val gosterilenOgle = bugunVakti.ogle?.take(5) ?: "00:00"
            val gosterilenIkindi = bugunVakti.ikindi?.take(5) ?: "00:00"
            val gosterilenAksam = bugunVakti.aksam?.take(5) ?: "00:00"
            val gosterilenYatsi = bugunVakti.yatsi?.take(5) ?: "00:00"
            val gosterilenSabah = saatCikar(gosterilenGunes, SABAH_EZANI_GUNESTEN_ONCE_DAKIKA)

            val imsakSure = prefs.getInt("IMSAK_SURE", 45)
            val sabahSure = prefs.getInt("SABAH_SURE", 45)
            val gunesSure = prefs.getInt("GUNES_SURE", 30)
            val ogleSure = prefs.getInt("OGLE_SURE", 45)
            val ikindiSure = prefs.getInt("IKINDI_SURE", 45)
            val aksamSure = prefs.getInt("AKSAM_SURE", 45)
            val yatsiSure = prefs.getInt("YATSI_SURE", 45)

            val imsakSes = prefs.getString("IMSAK_SES", "melodi_1") ?: "melodi_1"
            val sabahSes = prefs.getString("SABAH_SES", "melodi_1") ?: "melodi_1"
            val gunesSes = prefs.getString("GUNES_SES", "melodi_1") ?: "melodi_1"
            val ogleSes = prefs.getString("OGLE_SES", "melodi_1") ?: "melodi_1"
            val ikindiSes = prefs.getString("IKINDI_SES", "melodi_1") ?: "melodi_1"
            val aksamSes = prefs.getString("AKSAM_SES", "melodi_1") ?: "melodi_1"
            val yatsiSes = prefs.getString("YATSI_SES", "melodi_1") ?: "melodi_1"

            val sabahImsakteOku = prefs.getBoolean("SABAH_IMSAKTE_OKU", false)
            val tamSabahSes = prefs.getString("TAM_SABAH_SES", "melodi_1") ?: "melodi_1"
            val tamOgleSes = prefs.getString("TAM_OGLE_SES", "melodi_1") ?: "melodi_1"
            val tamIkindiSes = prefs.getString("TAM_IKINDI_SES", "melodi_1") ?: "melodi_1"
            val tamAksamSes = prefs.getString("TAM_AKSAM_SES", "melodi_1") ?: "melodi_1"
            val tamYatsiSes = prefs.getString("TAM_YATSI_SES", "melodi_1") ?: "melodi_1"

            fun ayarla(vakitAdi: String, saatStr: String, dakikaOncesi: Int, sesDosyasi: String, requestCode: Int) {
                val p = saatStr.split(":")
                if (p.size == 2) {
                    val vakitSaat = p[0].toIntOrNull() ?: return
                    val vakitDakika = p[1].toIntOrNull() ?: return
                    var toplamDakika = (vakitSaat * 60 + vakitDakika) - dakikaOncesi
                    while (toplamDakika < 0) toplamDakika += 24 * 60
                    val h = (toplamDakika / 60) % 24
                    val m = toplamDakika % 60
                    alarmKur(context, vakitAdi, h, m, sesDosyasi, requestCode)
                }
            }

            ayarla("İmsak", gosterilenImsak, imsakSure, imsakSes, 100)
            ayarla("Sabah", gosterilenSabah, sabahSure, sabahSes, 106)
            ayarla("Güneş", gosterilenGunes, gunesSure, gunesSes, 101)
            ayarla("Öğle", gosterilenOgle, ogleSure, ogleSes, 102)
            ayarla("İkindi", gosterilenIkindi, ikindiSure, ikindiSes, 103)
            ayarla("Akşam", gosterilenAksam, aksamSure, aksamSes, 104)
            ayarla("Yatsı", gosterilenYatsi, yatsiSure, yatsiSes, 105)

            if (sabahImsakteOku) ayarla("Sabah Ezanı", gosterilenImsak, 0, tamSabahSes, 200)
            else ayarla("Sabah Ezanı", gosterilenSabah, 0, tamSabahSes, 200)
            ayarla("Öğle Ezanı", gosterilenOgle, 0, tamOgleSes, 201)
            ayarla("İkindi Ezanı", gosterilenIkindi, 0, tamIkindiSes, 202)
            ayarla("Akşam Ezanı", gosterilenAksam, 0, tamAksamSes, 203)
            ayarla("Yatsı Ezanı", gosterilenYatsi, 0, tamYatsiSes, 204)

            // YENİ: Bu yenileme sırasında, ertesi gece yarısı için de kendini
            // tekrar kuruyoruz — zincir hiç kopmadan sürüyor.
            gunlukYenilemeAlarmiKur(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}