package com.colak.ezanvakti

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class VakitItem(
    val isim: String,
    val saat: String,
    val siradakiMi: Boolean = false,
    // "siradakiMi" geri sayım kartının hedefi (bir SONRAKİ vakit) için kullanılır.
    // "aktifMi" ise şu an İÇİNDE bulunduğumuz vakittir (listede yeşil vurgu bunu
    // kullanır) — ikisi farklı kavramlar, birbirine karışmamalı.
    val aktifMi: Boolean = false
)

// DÜZELTME: Sabah ezanının Ramazan dışında Güneş'ten 60 dakika önce okunması
// fıkhi/geleneksel bir kuraldır (isfar müstehabı). Bu değeri tek yerden
// yönetmek için sabit tanımlıyoruz.
private const val SABAH_EZANI_GUNESTEN_ONCE_DAKIKA = 60

/**
 * "HH:mm" formatındaki bir saatten belirtilen dakika kadar geriye gider.
 * Gün başını (00:00) geçerse bir önceki güne sarar (24 saatlik döngü).
 */
// YENİ ÖZELLİK: Hicri tarihi (API'nin "gün.ay.yıl" formatındaki) ayrıştırır.
// Arife günü tespiti için kullanılıyor.
private fun hicriGunAyYil(hicriTarih: String): Triple<Int, Int, Int>? {
    val p = hicriTarih.split(".")
    if (p.size != 3) return null
    val g = p[0].toIntOrNull() ?: return null
    val a = p[1].toIntOrNull() ?: return null
    val y = p[2].toIntOrNull() ?: return null
    return Triple(g, a, y)
}

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

@Composable
fun EzanVaktiHomeScreen() {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE)

    // DÜZELTME: Artık "Konya"/"Karatay" sabit varsayılanı kullanılmıyor —
    // hiçbir şehir seçilmemişken üst barda kafa karıştırıcı, hayali bir şehir
    // adı göstermek yerine "Şehir Seçilmedi" gösteriliyor.
    val kaydedilenIl = sharedPreferences.getString("SECILEN_IL", "") ?: ""
    val kaydedilenIlce = sharedPreferences.getString("SECILEN_ILCE", "") ?: ""

    val gosterilecekSehir = if (kaydedilenIl.isBlank()) "Şehir Seçilmedi" else "$kaydedilenIl, $kaydedilenIlce"

    // DÜZELTME: Ana ekran artık "TEMA_SECIMI" (Sistem/Gece/Gündüz) tercihini
    // SettingsScreen ve QiblaCompassScreen ile AYNI mantıkla okuyup uyguluyor.
    // Önceden bu ekran hep sabit koyu renklerle çiziliyordu, Ayarlar'dan Gündüz
    // seçilse bile değişmiyordu.
    var temaSecimi by remember { mutableStateOf(sharedPreferences.getString("TEMA_SECIMI", "Sistem") ?: "Sistem") }
    val isDarkMode = when (temaSecimi) {
        "Gece" -> true
        "Gündüz" -> false
        else -> isSystemInDarkTheme()
    }
    // YENİ ÖZELLİK: Vurgu rengi artık sabit değil, Ayarlar'dan seçilen renk
    // temasından okunuyor (varsayılan: Altın).
    val vurguRengi = TemaYardimcisi.vurguRenginiOku(context)

    val arkaPlanKatmanRengi = if (isDarkMode) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.65f)
    // DÜZELTME: Kart arka planları artık sabit lacivert değil — seçili tema
    // rengine göre koyulaştırılmış/açıklaştırılmış bir ton kullanıyor.
    val kartArkaPlani = TemaYardimcisi.kartArkaPlaniniHesapla(vurguRengi, isDarkMode).copy(alpha = 0.75f)
    val kartCerceveRengi = if (isDarkMode) Color(0xFF334155) else Color(0xFFCBD5E1)
    val yaziRengi = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val ikincilYaziRengi = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val vakitPasifArkaPlan = TemaYardimcisi.kartArkaPlaniniHesapla(vurguRengi, isDarkMode).copy(alpha = 0.75f)
    val vakitPasifCerceve = if (isDarkMode) Color(0xFF334155) else Color(0xFFCBD5E1)
    val vakitPasifYazi = if (isDarkMode) Color.White else Color(0xFF0F172A)

    var vakitlerListesi by remember { mutableStateOf(listOf<VakitItem>()) }
    // DÜZELTME: Ham vakit listesi (isim-saat çiftleri) artık ayrı bir state'te
    // saklanıyor — böylece "sıradaki/aktif vakit" indeksleri her saniye taze
    // hesaplanabiliyor. Önceden bu indeksler sadece veri ilk yüklendiğinde
    // (ya da API'den yeniden çekildiğinde) hesaplanıyordu; gün içinde zaman
    // ilerleyip bir vakit geçtiğinde indeksler bayatlıyor, geri sayım yanlışlıkla
    // "yarının aynı vaktine" 24 saatlik sarmaya girip 23:59:59'dan saymaya
    // başlıyordu.
    var hamVakitlerState by remember { mutableStateOf(listOf<Pair<String, String>>()) }

    // YENİ ÖZELLİK: Kerahat vakti — namaz kılmanın mekruh sayıldığı 3 dönem
    // (Sabah/Öğle-İstiva/Akşam). Aktifse "asansör gibi" aşağı inen bir panel
    // gösterilip kalan süre canlı geri sayılıyor, bitince panel yukarı çıkıp
    // kayboluyor.
    var aktifKerahatAdi by remember { mutableStateOf<String?>(null) }
    var kerahatKalanSaniye by remember { mutableStateOf(0L) }
    // YENİ: Kerahat henüz başlamadıysa, başlamasına ne kadar kaldığı.
    var kerahateKalanSaniye by remember { mutableStateOf(0L) }
    // DÜZELTME: Rozet artık her zaman görünmüyor — sadece istenen 2 geri
    // sayım penceresinde (Sabah ezanı->Güneş ve İkindi->Akşam Kerahatı) veya
    // aktif kerahat sırasında gösteriliyor. Öğle-İkindi arası gibi ara
    // dönemlerde rozet tamamen gizleniyor.
    var kerahatGostergesiGorunurMu by remember { mutableStateOf(false) }

    // YENİ ÖZELLİK: Bayram Namazı — Arife günü, seçili yerin bayram namazı
    // saatini gösterir. null = bugün Arife değil ya da veri henüz yok.
    var bayramNamaziSaati by remember { mutableStateOf<String?>(null) }
    var bayramAdi by remember { mutableStateOf<String?>(null) }
    // YENİ: Bugün bayramın kendisi mi (Arife değil, gerçek bayram günü)?
    // Bu true ise, saniyelik döngüde "vakit geçti mi" kontrolü yapılıp
    // geçtiyse rozet otomatik gizlenir.
    var bugunBayramGunuMu by remember { mutableStateOf(false) }
    var bayramRozetiGorunurMu by remember { mutableStateOf(false) }
    var kalanSureMetni by remember { mutableStateOf("00:00:00") }
    var siradakiVakitAdi by remember { mutableStateOf("Vakit Bekleniyor") }
    var miladiTarihMetni by remember { mutableStateOf("") }
    var hicriTarihMetni by remember { mutableStateOf("Hicri Takvim") }

    // DÜZELTME: Artık sabit bir tahmini kod ("9541") varsayılan olarak
    // kullanılmıyor — bu değerin yanlış şehre ait olduğu doğrulandı.
    // Kod hiç seçilmemişse boş bırakılır; aşağıdaki LaunchedEffect bunu
    // kontrol edip kullanıcıyı şehir seçmeye yönlendirir.
    val kaydedilenIlceKodu = sharedPreferences.getString("SECILEN_ILCE_KODU", "") ?: ""

    fun verileriIsleVeGoster(vakitlerListesiApi: List<DiyanetVakitModel>) {
        if (vakitlerListesiApi.isNotEmpty()) {
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
            val bugunTarihStr = dateFormat.format(Date())

            val bugunVakti = vakitlerListesiApi.find { it.miladiTarihKisa == bugunTarihStr } ?: vakitlerListesiApi.firstOrNull()

            if (bugunVakti != null) {

                val hamMiladi = bugunVakti.miladiTarihKisa ?: ""
                miladiTarihMetni = if (hamMiladi.contains(".")) {
                    val parcalar = hamMiladi.split(".")
                    if (parcalar.size == 3) {
                        val gun = parcalar[0]
                        val aySayi = parcalar[1]
                        val yil = parcalar[2]
                        val ayAdi = when (aySayi) {
                            "01", "1" -> "Ocak"; "02", "2" -> "Şubat"; "03", "3" -> "Mart"
                            "04", "4" -> "Nisan"; "05", "5" -> "Mayıs"; "06", "6" -> "Haziran"
                            "07", "7" -> "Temmuz"; "08", "8" -> "Ağustos"; "09", "9" -> "Eylül"
                            "10" -> "Ekim"; "11" -> "Kasım"; "12" -> "Aralık"
                            else -> aySayi
                        }
                        "$gun $ayAdi $yil"
                    } else hamMiladi
                } else hamMiladi.ifEmpty { "Tarih Yok" }

                val hamHicri = bugunVakti.hicriTarihKisa ?: ""
                hicriTarihMetni = if (hamHicri.contains(".")) {
                    val parcalar = hamHicri.split(".")
                    if (parcalar.size == 3) {
                        val gun = parcalar[0]
                        val aySayi = parcalar[1]
                        val yil = parcalar[2]
                        val ayAdi = when (aySayi) {
                            "1" -> "Muharrem"; "2" -> "Safer"; "3" -> "Rebiülevvel"
                            "4" -> "Rebiülahir"; "5" -> "Cemaziyelevvel"; "6" -> "Cemaziyelahir"
                            "7" -> "Recep"; "8" -> "Şaban"; "9" -> "Ramazan"
                            "10" -> "Şevval"; "11" -> "Zilkade"; "12" -> "Zilhicce"
                            else -> aySayi
                        }
                        "$gun $ayAdi $yil"
                    } else hamHicri
                } else hamHicri.ifEmpty { "Hicri Takvim" }

                // SIFIR MATEMATİK, %100 SAF DİYANET
                val gosterilenImsak = bugunVakti.imsak?.take(5) ?: "00:00"
                val gosterilenGunes = bugunVakti.gunes?.take(5) ?: "00:00"
                val gosterilenOgle = bugunVakti.ogle?.take(5) ?: "00:00"
                val gosterilenIkindi = bugunVakti.ikindi?.take(5) ?: "00:00"
                val gosterilenAksam = bugunVakti.aksam?.take(5) ?: "00:00"
                val gosterilenYatsi = bugunVakti.yatsi?.take(5) ?: "00:00"

                // Sabah ezanı, Ramazan dışında güneşin doğuşundan 60 dakika önce
                // okunur (isfar müstehabı). Ramazan ayında ise SABAH_IMSAKTE_OKU
                // anahtarı açılarak ezanın İmsak vaktinde okunması sağlanıyor —
                // bu mantık aşağıdaki alarm kurulumunda (sabahImsakteOku kontrolü).
                val gosterilenSabah = saatCikar(gosterilenGunes, SABAH_EZANI_GUNESTEN_ONCE_DAKIKA)

                val hamVakitler = listOf(
                    Pair("İmsak", gosterilenImsak),
                    Pair("Sabah", gosterilenSabah),
                    Pair("Güneş", gosterilenGunes),
                    Pair("Öğle", gosterilenOgle),
                    Pair("İkindi", gosterilenIkindi),
                    Pair("Akşam", gosterilenAksam),
                    Pair("Yatsı", gosterilenYatsi)
                )
                // DÜZELTME: Artık burada sadece ham liste kaydediliyor.
                // "Sıradaki/aktif vakit" indeksleri, aşağıdaki saniyelik
                // döngüde (guncelleVakitListesi) her seferinde TAZE
                // hesaplanıyor — bu sayede bir vakit geçtiğinde uygulama
                // hemen bir sonrakine geçiyor, 24 saatlik yanlış sarmaya
                // girmiyor.
                hamVakitlerState = hamVakitler

                // ALARM SİSTEMİ
                val imsakSure = sharedPreferences.getInt("IMSAK_SURE", 45)
                val sabahSure = sharedPreferences.getInt("SABAH_SURE", 45)
                val gunesSure = sharedPreferences.getInt("GUNES_SURE", 30)
                val ogleSure = sharedPreferences.getInt("OGLE_SURE", 45)
                val ikindiSure = sharedPreferences.getInt("IKINDI_SURE", 45)
                val aksamSure = sharedPreferences.getInt("AKSAM_SURE", 45)
                val yatsiSure = sharedPreferences.getInt("YATSI_SURE", 45)

                val imsakSes = sharedPreferences.getString("IMSAK_SES", "melodi_1") ?: "melodi_1"
                val sabahSes = sharedPreferences.getString("SABAH_SES", "melodi_1") ?: "melodi_1"
                val gunesSes = sharedPreferences.getString("GUNES_SES", "melodi_1") ?: "melodi_1"
                val ogleSes = sharedPreferences.getString("OGLE_SES", "melodi_1") ?: "melodi_1"
                val ikindiSes = sharedPreferences.getString("IKINDI_SES", "melodi_1") ?: "melodi_1"
                val aksamSes = sharedPreferences.getString("AKSAM_SES", "melodi_1") ?: "melodi_1"
                val yatsiSes = sharedPreferences.getString("YATSI_SES", "melodi_1") ?: "melodi_1"

                val sabahImsakteOku = sharedPreferences.getBoolean("SABAH_IMSAKTE_OKU", false)
                val tamSabahSes = sharedPreferences.getString("TAM_SABAH_SES", "melodi_1") ?: "melodi_1"
                val tamOgleSes = sharedPreferences.getString("TAM_OGLE_SES", "melodi_1") ?: "melodi_1"
                val tamIkindiSes = sharedPreferences.getString("TAM_IKINDI_SES", "melodi_1") ?: "melodi_1"
                val tamAksamSes = sharedPreferences.getString("TAM_AKSAM_SES", "melodi_1") ?: "melodi_1"
                val tamYatsiSes = sharedPreferences.getString("TAM_YATSI_SES", "melodi_1") ?: "melodi_1"

                fun alarmAyarla(vakitAdi: String, saatStr: String, dakikaOncesi: Int, sesDosyasi: String, requestCode: Int) {
                    val p = saatStr.split(":")
                    if (p.size == 2) {
                        val vakitSaat = p[0].toInt()
                        val vakitDakika = p[1].toInt()
                        var toplamDakika = (vakitSaat * 60 + vakitDakika) - dakikaOncesi
                        while (toplamDakika < 0) {
                            toplamDakika += 24 * 60
                        }
                        val h = (toplamDakika / 60) % 24
                        val m = toplamDakika % 60
                        EzanAlarmManager.alarmKur(context, vakitAdi, h, m, sesDosyasi, requestCode)
                    }
                }

                alarmAyarla("İmsak", gosterilenImsak, imsakSure, imsakSes, 100)
                alarmAyarla("Sabah", gosterilenSabah, sabahSure, sabahSes, 106)
                alarmAyarla("Güneş", gosterilenGunes, gunesSure, gunesSes, 101)
                alarmAyarla("Öğle", gosterilenOgle, ogleSure, ogleSes, 102)
                alarmAyarla("İkindi", gosterilenIkindi, ikindiSure, ikindiSes, 103)
                alarmAyarla("Akşam", gosterilenAksam, aksamSure, aksamSes, 104)
                alarmAyarla("Yatsı", gosterilenYatsi, yatsiSure, yatsiSes, 105)

                // Ramazan'da (SABAH_IMSAKTE_OKU açıkken) sabah ezanı İmsak vaktinde,
                // aksi halde (normal aylarda) Güneş'ten 60 dk önce (gosterilenSabah) okunur.
                if (sabahImsakteOku) alarmAyarla("Sabah Ezanı", gosterilenImsak, 0, tamSabahSes, 200)
                else alarmAyarla("Sabah Ezanı", gosterilenSabah, 0, tamSabahSes, 200)
                alarmAyarla("Öğle Ezanı", gosterilenOgle, 0, tamOgleSes, 201)
                alarmAyarla("İkindi Ezanı", gosterilenIkindi, 0, tamIkindiSes, 202)
                alarmAyarla("Akşam Ezanı", gosterilenAksam, 0, tamAksamSes, 203)
                alarmAyarla("Yatsı Ezanı", gosterilenYatsi, 0, tamYatsiSes, 204)

                // YENİ ÖZELLİK: Vakit alarmları önceden SADECE bu blok
                // çalıştığında (yani uygulama ilk açıldığında/şehir
                // değiştiğinde) kuruluyordu — günler geçtikçe (uygulama tekrar
                // açılmadan) yenilenmiyordu, bu da bazı vakitlerin (rastgele
                // hangisi denk gelirse) eski/yanlış bir tarihte kalıp
                // çalmamasına yol açabiliyordu. Artık burada, her gece yarısı
                // TÜM alarmları otomatik yenileyen "günlük tazeleme alarmı"
                // da (yeniden) kuruluyor — uygulama tekrar açılmasa bile
                // vakitler her gün taze hesaplanmaya devam eder.
                EzanAlarmManager.gunlukYenilemeAlarmiKur(context)
            }
        }
    }

    // YENİ ÖZELLİK: Arife günü tespiti + Bayram Namazı saati çekme.
    // Yarının Hicri tarihine bakarak (elimizdeki listede zaten birden fazla
    // gün var, ay uzunluğu tahmini yapmaya gerek yok): 1 Şevval ise Ramazan
    // Bayramı arifesi, 10 Zilhicce ise Kurban Bayramı arifesi.
    suspend fun arifeKontrolEtVeBayramCek(liste: List<DiyanetVakitModel>) {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
        val simdi = Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul"))
        val bugunStr = dateFormat.format(simdi.time)
        val yarinCal = simdi.clone() as Calendar
        yarinCal.add(Calendar.DAY_OF_YEAR, 1)
        val yarinStr = dateFormat.format(yarinCal.time)

        val bugununVakti = liste.find { it.miladiTarihKisa == bugunStr }
        val yarininVakti = liste.find { it.miladiTarihKisa == yarinStr }

        val bugunHicri = bugununVakti?.hicriTarihKisa?.let { hicriGunAyYil(it) }
        val yarinHicri = yarininVakti?.hicriTarihKisa?.let { hicriGunAyYil(it) }

        fun hicriBayramMi(h: Triple<Int, Int, Int>?): String? = when {
            h != null && h.second == 10 && h.first == 1 -> "Ramazan Bayram Namazı"
            h != null && h.second == 12 && h.first == 10 -> "Kurban Bayram Namazı"
            else -> null
        }

        // YENİ: Artık iki durum ayrı ayrı kontrol ediliyor:
        //  - bugunBayramAdi != null -> BUGÜN bayramın kendisi (namaz vakti
        //    geçtiyse rozet aşağıda otomatik gizlenecek)
        //  - yarinBayramAdi != null -> BUGÜN Arife günü (yarın bayram)
        var bugunBayramAdi = hicriBayramMi(bugunHicri)
        var yarinBayramAdi = hicriBayramMi(yarinHicri)

        val yeniBayramAdi = bugunBayramAdi ?: yarinBayramAdi
        bugunBayramGunuMu = bugunBayramAdi != null

        if (yeniBayramAdi == null) {
            bayramAdi = null
            bayramNamaziSaati = null
            bayramRozetiGorunurMu = false
            return
        }
        bayramAdi = yeniBayramAdi

        val sehirKodu = sharedPreferences.getString("SECILEN_SEHIR_KODU", "") ?: ""
        val ilceAdiKayitli = sharedPreferences.getString("SECILEN_ILCE", "") ?: ""
        if (sehirKodu.isBlank()) {
            bayramNamaziSaati = null
            bayramRozetiGorunurMu = true // isim en azından gösterilsin
            return
        }

        try {
            val bayramHam = withContext(Dispatchers.IO) {
                EzanApiService.instance.getBayramNamaziHam(sehirKodu)
            }
            android.util.Log.d("EzanVaktiDebug", "BAYRAM NAMAZI ham cevap boyutu: ${bayramHam.size}")
            if (bayramHam.isNotEmpty()) {
                android.util.Log.d("EzanVaktiDebug", "BAYRAM NAMAZI ilk kayıt örneği: ${bayramHam.first()}")
            }

            fun bayramAnahtarDeneVeAl(map: Map<String, Any>, adaylar: List<String>): String? {
                for (aday in adaylar) {
                    val deger = map[aday]
                    if (deger != null) {
                        val metin = deger.toString().trim()
                        if (metin.isNotEmpty() && metin != "null") return metin
                    }
                }
                return null
            }

            val ilceAdiCandidates = listOf("IlceAdi", "ilceAdi", "İlçeAdi", "Adi", "adi", "Name", "name")
            val saatCandidates = listOf("BayramNamaziSaati", "BayramNamazi", "Saat", "saat", "Vakit", "vakit", "NamazSaati", "namazSaati")

            val normIlce = ilceAdiKayitli.trim().lowercase(Locale.ROOT)
            val eslesen = bayramHam.find { kayit ->
                val ilceAdi = bayramAnahtarDeneVeAl(kayit, ilceAdiCandidates)
                ilceAdi != null && ilceAdi.trim().lowercase(Locale.ROOT) == normIlce
            } ?: bayramHam.firstOrNull()

            val saat = eslesen?.let { bayramAnahtarDeneVeAl(it, saatCandidates) }
            bayramNamaziSaati = saat?.take(5)
            bayramRozetiGorunurMu = true

            if (bayramNamaziSaati == null) {
                android.util.Log.w("EzanVaktiDebug", "BAYRAM NAMAZI saati alan adı bulunamadı. Örnek kayıt: ${bayramHam.firstOrNull()}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            bayramNamaziSaati = null
            bayramRozetiGorunurMu = true
        }
    }

    LaunchedEffect(kaydedilenIlceKodu) {
        if (kaydedilenIlceKodu.isBlank()) {
            siradakiVakitAdi = "Lütfen Ayarlar'dan şehir seçin"
            return@LaunchedEffect
        }

        val cacheKey = "diyanet_vakitler_ilce_$kaydedilenIlceKodu"
        val gson = Gson()

        val gecerliKod = kaydedilenIlceKodu

        val cachedJson = sharedPreferences.getString(cacheKey, null)
        if (cachedJson != null) {
            try {
                val type = object : TypeToken<List<DiyanetVakitModel>>() {}.type
                val cachedList: List<DiyanetVakitModel> = gson.fromJson(cachedJson, type)
                verileriIsleVeGoster(cachedList)
                arifeKontrolEtVeBayramCek(cachedList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            val vakitlerListesiApi = withContext(Dispatchers.IO) {
                EzanApiService.instance.getVakitler(ilceKodu = gecerliKod)
            }

            if (vakitlerListesiApi.isNotEmpty()) {
                val jsonString = gson.toJson(vakitlerListesiApi)
                sharedPreferences.edit().putString(cacheKey, jsonString).apply()
                verileriIsleVeGoster(vakitlerListesiApi)
                arifeKontrolEtVeBayramCek(vakitlerListesiApi)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(hamVakitlerState) {
        while (true) {
            val simdi = Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul"))
            val simdikiDakika = simdi.get(Calendar.HOUR_OF_DAY) * 60 + simdi.get(Calendar.MINUTE)
            val simdikiSaniye = simdi.get(Calendar.SECOND)

            // YENİ: Bugün gerçek Bayram günüyse, Bayram Namazı vaktinin geçip
            // geçmediğini her saniye canlı kontrol ediyoruz. Geçtiyse rozet
            // otomatik gizleniyor (asıl istenen davranış — Arife'de görünüp
            // Bayram Namazı vaktinde kaybolması).
            if (bugunBayramGunuMu && bayramNamaziSaati != null) {
                val parca = bayramNamaziSaati!!.split(":")
                if (parca.size == 2) {
                    val bayramDakika = (parca[0].toIntOrNull() ?: 0) * 60 + (parca[1].toIntOrNull() ?: 0)
                    if (simdikiDakika >= bayramDakika) {
                        bayramRozetiGorunurMu = false
                    }
                }
            }

            if (hamVakitlerState.isNotEmpty()) {
                // YENİ ÖZELLİK: Kerahat vakti hesaplaması — her saniye taze.
                // 3 dönem: Sabah (Güneş'ten sonraki 45 dk), Öğle/İstiva
                // (Öğle'den önceki 45 dk), Akşam (Akşam'dan önceki 45 dk).
                val sabahStr = hamVakitlerState.find { it.first == "Sabah" }?.second
                val gunesStr = hamVakitlerState.find { it.first == "Güneş" }?.second
                val ogleStr = hamVakitlerState.find { it.first == "Öğle" }?.second
                val ikindiStr = hamVakitlerState.find { it.first == "İkindi" }?.second
                val aksamStr = hamVakitlerState.find { it.first == "Akşam" }?.second

                fun saatSaniyeye(saat: String?): Int? {
                    if (saat == null) return null
                    val p = saat.split(":")
                    if (p.size != 2) return null
                    val h = p[0].toIntOrNull() ?: return null
                    val m = p[1].toIntOrNull() ?: return null
                    return h * 3600 + m * 60
                }

                val simdikiSaniyeGunIci = simdikiDakika * 60 + simdikiSaniye
                // DÜZELTME: Kerahat süresi artık Ayarlar'dan kullanıcı
                // tarafından ayarlanabiliyor (varsayılan: 45 dk).
                val kerahatSuresiDakika = sharedPreferences.getInt("KERAHAT_SURESI_DAKIKA", 45)
                val kirkBesDakikaSn = kerahatSuresiDakika * 60

                val sabahSn = saatSaniyeye(sabahStr)
                val gunesSn = saatSaniyeye(gunesStr)
                val ogleSn = saatSaniyeye(ogleStr)
                val ikindiSn = saatSaniyeye(ikindiStr)
                val aksamSn = saatSaniyeye(aksamStr)

                var yeniAktifKerahat: String? = null
                var yeniKerahatKalan = 0L

                if (gunesSn != null) {
                    val bitis = gunesSn + kirkBesDakikaSn
                    if (simdikiSaniyeGunIci in gunesSn until bitis) {
                        yeniAktifKerahat = "Sabah Kerahatı"
                        yeniKerahatKalan = (bitis - simdikiSaniyeGunIci).toLong()
                    }
                }
                if (yeniAktifKerahat == null && ogleSn != null) {
                    val baslangic = ogleSn - kirkBesDakikaSn
                    if (simdikiSaniyeGunIci in baslangic until ogleSn) {
                        yeniAktifKerahat = "Öğle Kerahatı (İstiva)"
                        yeniKerahatKalan = (ogleSn - simdikiSaniyeGunIci).toLong()
                    }
                }
                if (yeniAktifKerahat == null && aksamSn != null) {
                    val baslangic = aksamSn - kirkBesDakikaSn
                    if (simdikiSaniyeGunIci in baslangic until aksamSn) {
                        yeniAktifKerahat = "Akşam Kerahatı"
                        yeniKerahatKalan = (aksamSn - simdikiSaniyeGunIci).toLong()
                    }
                }

                aktifKerahatAdi = yeniAktifKerahat
                kerahatKalanSaniye = yeniKerahatKalan

                // DÜZELTME (KERAHAT SİSTEMİ): Rozet artık gün boyunca sürekli
                // "bir sonraki kerahate kalan" göstermiyor — sadece 3 belirli
                // pencerede geri sayım gösteriliyor, geri kalan zamanlarda
                // (ör. Öğle-İkindi arası) rozet tamamen gizleniyor:
                //   Pencere A: Sabah ezanından Güneş'e kadar -> hedef Güneş
                //     (Güneş doğunca zaten Sabah Kerahatı aktif olur.)
                //   Pencere B: İkindi'den Akşam Kerahatı başlangıcına
                //     (Akşam - kerahatSuresi) kadar -> hedef Akşam Kerahatı
                //     başlangıcı (o noktada zaten Akşam Kerahatı aktif olur).
                //   Pencere C: Sabah Kerahatı bitince (Güneş + kerahatSuresi)
                //     başlayıp Öğle Kerahatının (İstiva) başlangıcına
                //     (Öğle - kerahatSuresi) kadar -> hedef İstiva başlangıcı.
                if (yeniAktifKerahat != null) {
                    // Aktif kerahat sırasında rozet zaten "Kerahat Vakti" olarak gösteriliyor.
                    kerahateKalanSaniye = 0L
                    kerahatGostergesiGorunurMu = true
                } else {
                    val pencereAHedefi = gunesSn
                    val pencereBHedefi = aksamSn?.minus(kirkBesDakikaSn)
                    val pencereCBaslangici = gunesSn?.plus(kirkBesDakikaSn)
                    val pencereCHedefi = ogleSn?.minus(kirkBesDakikaSn)

                    val pencereAIcinde = sabahSn != null && gunesSn != null &&
                            simdikiSaniyeGunIci in sabahSn until gunesSn
                    val pencereBIcinde = ikindiSn != null && pencereBHedefi != null &&
                            simdikiSaniyeGunIci in ikindiSn until pencereBHedefi
                    val pencereCIcinde = pencereCBaslangici != null && pencereCHedefi != null &&
                            simdikiSaniyeGunIci in pencereCBaslangici until pencereCHedefi

                    when {
                        pencereAIcinde -> {
                            kerahateKalanSaniye = (pencereAHedefi!! - simdikiSaniyeGunIci).toLong()
                            kerahatGostergesiGorunurMu = true
                        }
                        pencereCIcinde -> {
                            kerahateKalanSaniye = (pencereCHedefi!! - simdikiSaniyeGunIci).toLong()
                            kerahatGostergesiGorunurMu = true
                        }
                        pencereBIcinde -> {
                            kerahateKalanSaniye = (pencereBHedefi!! - simdikiSaniyeGunIci).toLong()
                            kerahatGostergesiGorunurMu = true
                        }
                        else -> {
                            // Örn. gece saatleri (Akşam'dan ertesi günün Sabah
                            // ezanına kadar) — rozet tamamen gizlenir.
                            kerahateKalanSaniye = 0L
                            kerahatGostergesiGorunurMu = false
                        }
                    }
                }

                // DÜZELTME (ANA HATA): "Sıradaki" ve "aktif" vakit indeksleri
                // artık HER SANİYE, o anki gerçek saatten taze hesaplanıyor.
                // Önceden bu indeksler sadece veri yüklendiğinde bir kez
                // hesaplanıp dondurulmuştu; bir vakit fiilen geçtiğinde liste
                // güncellenmediği için geri sayım yanlışlıkla "ertesi günün
                // aynı vaktine" 24 saatlik sarmaya girip 23:59:59'dan saymaya
                // başlıyordu.
                var siradakiIndex = 0
                var minFark = Int.MAX_VALUE
                hamVakitlerState.forEachIndexed { index, vakit ->
                    val parca = vakit.second.split(":")
                    if (parca.size == 2) {
                        val vakitDakikaCinsinden = (parca[0].toIntOrNull() ?: 0) * 60 + (parca[1].toIntOrNull() ?: 0)
                        val fark = vakitDakikaCinsinden - simdikiDakika
                        if (fark > 0 && fark < minFark) {
                            minFark = fark
                            siradakiIndex = index
                        }
                    }
                }

                var aktifIndex = hamVakitlerState.lastIndex
                var maxGecmisDakika = -1
                hamVakitlerState.forEachIndexed { index, vakit ->
                    val parca = vakit.second.split(":")
                    if (parca.size == 2) {
                        val vakitDakikaCinsinden = (parca[0].toIntOrNull() ?: 0) * 60 + (parca[1].toIntOrNull() ?: 0)
                        if (vakitDakikaCinsinden <= simdikiDakika && vakitDakikaCinsinden > maxGecmisDakika) {
                            maxGecmisDakika = vakitDakikaCinsinden
                            aktifIndex = index
                        }
                    }
                }

                vakitlerListesi = hamVakitlerState.mapIndexed { index, pair ->
                    VakitItem(
                        isim = pair.first,
                        saat = pair.second,
                        siradakiMi = (index == siradakiIndex),
                        aktifMi = (index == aktifIndex)
                    )
                }

                val siradakiVakit = vakitlerListesi.getOrNull(siradakiIndex)
                if (siradakiVakit != null) {
                    val ek = when (siradakiVakit.isim) {
                        "İmsak", "Akşam" -> "'a Kalan"
                        "Öğle", "İkindi", "Yatsı" -> "'ye Kalan"
                        "Güneş", "Sabah" -> "'e Kalan"
                        else -> "'e Kalan"
                    }
                    siradakiVakitAdi = "${siradakiVakit.isim}$ek"

                    val parca = siradakiVakit.saat.split(":")
                    if (parca.size == 2) {
                        val vakitDakika = (parca[0].toIntOrNull() ?: 0) * 60 + (parca[1].toIntOrNull() ?: 0)

                        var toplamSaniyeFark = (vakitDakika * 60) - (simdikiDakika * 60 + simdikiSaniye)
                        if (toplamSaniyeFark < 0) toplamSaniyeFark += 24 * 3600

                        val saatKalan = toplamSaniyeFark / 3600
                        val dakikaKalan = (toplamSaniyeFark % 3600) / 60
                        val saniyeKalan = toplamSaniyeFark % 60

                        kalanSureMetni = String.format("%02d:%02d:%02d", saatKalan, dakikaKalan, saniyeKalan)
                    }
                }
            }
            delay(1000L)
        }
    }

    // DÜZELTME: Sistem yazı boyutu (erişilebilirlik ayarı) "En Büyük" moda
    // alınınca, bu ekrandaki sabit tasarım (7 vakit satırı + geri sayım
    // kartı, ekran yüksekliğine "weight" ile paylaştırılmış) yazı boyutu
    // büyüdüğü için taşmaya/kaymaya başlıyordu. Zikirmatik ekranındaki AYNI
    // çözüm burada da uygulandı — bu ekran artık sistem yazı boyutundan
    // TAMAMEN bağımsız, her zaman sabit/normal boyutta kalacak şekilde
    // kilitlendi (fontScale = 1f).
    val yerelYogunluk = androidx.compose.ui.platform.LocalDensity.current
    val sabitYoğunluk = remember(yerelYogunluk.density) {
        androidx.compose.ui.unit.Density(
            density = yerelYogunluk.density,
            fontScale = 1f
        )
    }
    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides sabitYoğunluk) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.mevlana_bg),
                contentDescription = "Arka Plan",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(modifier = Modifier.fillMaxSize().background(arkaPlanKatmanRengi))

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                UstKonumBar(sehirAdi = gosterilecekSehir, yaziRengi = yaziRengi, vurguRengi = vurguRengi)
                Spacer(modifier = Modifier.height(16.dp))
                GeriSayimKarti(
                    siradakiVakitAdi = siradakiVakitAdi,
                    kalanSure = kalanSureMetni,
                    miladiTarih = if (miladiTarihMetni.isNotEmpty()) miladiTarihMetni else "Yükleniyor...",
                    hicriTarih = hicriTarihMetni,
                    kartArkaPlani = kartArkaPlani,
                    kartCerceveRengi = kartCerceveRengi,
                    yaziRengi = yaziRengi,
                    ikincilYaziRengi = ikincilYaziRengi,
                    vurguRengi = vurguRengi,
                    kerahatAktifMi = aktifKerahatAdi != null,
                    aktifKerahatAdi = aktifKerahatAdi,
                    kerahateKalanSaniye = kerahateKalanSaniye,
                    kerahatGostergesiGorunurMu = kerahatGostergesiGorunurMu,
                    bayramAdi = if (bayramRozetiGorunurMu) bayramAdi else null,
                    bayramNamaziSaati = bayramNamaziSaati
                )

                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "Günün Vakitleri", color = yaziRengi, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 10.dp))
                // DÜZELTME: LazyColumn yerine kalan tüm dikey boşluğu 7 vakit
                // arasında eşit paylaştıran bir Column kullanılıyor — böylece
                // ekran boyu ne olursa olsun (küçük/büyük telefon fark etmeksizin)
                // Akşam ve Yatsı dahil tüm vakitler kaydırmaya gerek kalmadan
                // tek ekrana sığıyor.
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vakitlerListesi.forEach { vakit ->
                        VakitSatiri(
                            vakit = vakit,
                            pasifArkaPlan = vakitPasifArkaPlan,
                            pasifCerceve = vakitPasifCerceve,
                            pasifYazi = vakitPasifYazi,
                            isDarkMode = isDarkMode,
                            vurguRengi = vurguRengi,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    } // CompositionLocalProvider kapanışı — sabit yazı boyutu kilidi
}

@Composable
fun UstKonumBar(sehirAdi: String, yaziRengi: Color, vurguRengi: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Konum", tint = vurguRengi)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = sehirAdi, color = yaziRengi, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GeriSayimKarti(
    siradakiVakitAdi: String,
    kalanSure: String,
    miladiTarih: String,
    hicriTarih: String,
    kartArkaPlani: Color,
    kartCerceveRengi: Color,
    yaziRengi: Color,
    ikincilYaziRengi: Color,
    vurguRengi: Color,
    kerahatAktifMi: Boolean,
    aktifKerahatAdi: String?,
    kerahateKalanSaniye: Long,
    kerahatGostergesiGorunurMu: Boolean,
    bayramAdi: String?,
    bayramNamaziSaati: String?
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = kartArkaPlani), border = androidx.compose.foundation.BorderStroke(1.2.dp, kartCerceveRengi)) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = siradakiVakitAdi, color = ikincilYaziRengi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = kalanSure, color = yaziRengi, fontSize = 48.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(14.dp))
            Box(modifier = Modifier.clip(RoundedCornerShape(30.dp)).border(1.dp, kartCerceveRengi, RoundedCornerShape(30.dp)).background(kartArkaPlani.copy(alpha = 0.6f)).padding(horizontal = 20.dp, vertical = 10.dp)) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = miladiTarih, color = yaziRengi, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.width(1.dp).height(14.dp).background(ikincilYaziRengi))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = hicriTarih, color = vurguRengi, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // YENİ ÖZELLİK: Kerahat vakti göstergesi (+ Arife günüyse yanına
            // Bayram Namazı rozeti) — tarih satırının hemen altında.
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // DÜZELTME: Kerahat rozeti artık gün boyunca sürekli görünmüyor
                // — sadece aktif kerahat sırasında ya da izin verilen 2 geri
                // sayım penceresinde (Sabah ezanı->Güneş, İkindi->Akşam
                // Kerahatı) asansör gibi aşağı kayarak beliriyor, dışarıda
                // (ör. Öğle-İkindi arası) yukarı kayıp tamamen kayboluyor.
                AnimatedVisibility(
                    visible = kerahatGostergesiGorunurMu,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    KerahatGostergesi(
                        aktifMi = kerahatAktifMi,
                        aktifKerahatAdi = aktifKerahatAdi,
                        kalanSaniye = kerahateKalanSaniye,
                        vurguRengi = vurguRengi,
                        yaziRengi = yaziRengi,
                        ikincilYaziRengi = ikincilYaziRengi
                    )
                }
                // YENİ: Arife günü ise (bayramAdi dolu), Bayram Namazı rozeti
                // Kerahat göstergesinin YANINA ekleniyor. Saat henüz
                // çekilemediyse (API yanıt vermediyse) sadece bayram adı
                // gösterilir, uydurma bir saat asla yazılmaz.
                //
                // YENİ: "Asansör" animasyonu — Arife gününün başlangıcında
                // (00:00) yukarıdan aşağı kayarak belirir, Bayram Namazı vakti
                // geldiğinde yukarı kayarak kaybolur.
                AnimatedVisibility(
                    visible = bayramAdi != null,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    if (bayramAdi != null) {
                        BayramNamaziRozeti(
                            bayramAdi = bayramAdi,
                            saat = bayramNamaziSaati,
                            vurguRengi = vurguRengi
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BayramNamaziRozeti(bayramAdi: String, saat: String?, vurguRengi: Color) {
    // DÜZELTME: Artık Kerahat göstergesiyle AYNI şeffaflık kullanılıyor
    // (vurguRengi %16 dolgu + %40 çerçeve) — tutarlı görünüm için.
    // YENİ: Yazı formatı iki satıra çıkarıldı — üstte bayram/namaz adı,
    // altında ORTALI şekilde saat.
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(vurguRengi.copy(alpha = 0.16f))
            .border(1.dp, vurguRengi.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🕌 $bayramAdi",
                color = vurguRengi,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (saat != null) {
                Text(
                    text = saat,
                    color = vurguRengi,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            } else {
                Text(
                    text = "arifesi",
                    color = vurguRengi,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun KerahatGostergesi(
    aktifMi: Boolean,
    aktifKerahatAdi: String?,
    kalanSaniye: Long,
    vurguRengi: Color,
    yaziRengi: Color,
    ikincilYaziRengi: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (aktifMi) vurguRengi.copy(alpha = 0.90f) else vurguRengi.copy(alpha = 0.16f))
            .border(1.dp, if (aktifMi) vurguRengi else vurguRengi.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (aktifMi) {
            // DÜZELTME: Artık hangi kerahat türü olduğu (Sabah/Öğle-İstiva/
            // Akşam) net yazıyor — önceden hepsi aynı genel "Kerahat Vakti"
            // yazısını gösterdiği için kullanıcı hangi dönemde olduğunu
            // ayırt edemiyordu.
            Text(text = "⚠️ Kerahat Vakti", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        } else {
            val saat = kalanSaniye / 3600
            val dakika = (kalanSaniye % 3600) / 60
            val saniye = kalanSaniye % 60
            val sureMetni = if (saat > 0) {
                String.format("%d sa %02d dk", saat, dakika)
            } else {
                String.format("%02d:%02d", dakika, saniye)
            }
            Text(text = "Kerahate $sureMetni", color = vurguRengi, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun VakitSatiri(vakit: VakitItem, pasifArkaPlan: Color, pasifCerceve: Color, pasifYazi: Color, isDarkMode: Boolean, vurguRengi: Color, modifier: Modifier = Modifier) {
    // Yeşil vurgu "aktifMi" (şu an içinde bulunduğumuz vakit) kullanır,
    // "siradakiMi" (gelecek/sıradaki vakit — geri sayımın hedefi) değil.
    //
    // DÜZELTME: Aktif satırın arka planı artık Gece/Gündüz modu fark etmeksizin
    // DÜZELTME: Arka plan artık %88 opak (neredeyse tam renkli) olduğu için
    // yazı da aynı vurgu renginde kalırsa okunmaz hale gelir (renk renk üstüne
    // biner) — yazı rengi beyaza çevrildi, kontrast sağlansın diye.
    val arkaPlanRengi = if (vakit.aktifMi) vurguRengi.copy(alpha = 0.88f) else pasifArkaPlan
    val metinRengi = if (vakit.aktifMi) Color.White else pasifYazi
    val isimRengi = if (vakit.aktifMi) Color.White else pasifYazi
    val cerceveRengi = if (vakit.aktifMi) vurguRengi else pasifCerceve

    // DÜZELTME: Yükseklik artık dışarıdan (weight ile) belirleniyor, sabit
    // dikey padding kaldırıldı — böylece 7 vakit satırı, ekran boyu ne olursa
    // olsun kaydırmaya gerek kalmadan tam olarak sığıyor. Yazı boyutları da
    // büyütüldü (okunabilirlik için).
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(arkaPlanRengi)
            .border(1.2.dp, cerceveRengi, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = vakit.isim, color = isimRengi, fontSize = 22.sp, fontWeight = if (vakit.aktifMi) FontWeight.Bold else FontWeight.SemiBold)
        Text(text = vakit.saat, color = metinRengi, fontSize = 25.sp, fontWeight = FontWeight.Bold)
    }
}