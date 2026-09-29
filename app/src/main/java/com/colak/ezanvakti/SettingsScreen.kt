package com.colak.ezanvakti

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun SettingsScreen(
    onGeriTiklandi: () -> Unit,
    // DÜZELTME: Görünüm Modu (Sistem/Gece/Gündüz) ve Renk Teması artık bu
    // ekranın kendi İÇİNDE tuttuğu bir "remember" değil, DIŞARIDAN
    // (MainActivity) geliyor — çünkü alt menü çubuğu (AltMenuCubugu) da AYNI
    // tercihi kullanıyor ama kendi SharedPreferences okumasıyla çalışıyordu.
    // Bu ikisi ayrı state'ler olduğu için, buradan bir buton değiştirildiğinde
    // alt menü hemen güncellenmiyordu (ancak sekme değiştirilince, yani
    // AltMenuCubugu yeniden kompoze olunca fark ediliyordu). Artık TEK bir
    // paylaşılan state kullanıldığı için değişiklik anında her yere yansıyor.
    temaSecimiDisarida: String,
    onTemaSecimiDegis: (String) -> Unit,
    seciliRenkTemasiDisarida: RenkTemasi,
    onRenkTemasiDegis: (RenkTemasi) -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE)
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // --- SES AYARLARI İÇİN DEĞİŞKENLER (Varsayılan: melodi_1) ---
    var sesDialogAcikMi by remember { mutableStateOf(false) }
    var aktifSesDuzenlenenVakit by remember { mutableStateOf<String?>(null) }

    var imsakSes by remember { mutableStateOf(prefs.getString("IMSAK_SES", "melodi_1") ?: "melodi_1") }
    var sabahSes by remember { mutableStateOf(prefs.getString("SABAH_SES", "melodi_1") ?: "melodi_1") }
    var gunesSes by remember { mutableStateOf(prefs.getString("GUNES_SES", "melodi_1") ?: "melodi_1") }
    var ogleSes by remember { mutableStateOf(prefs.getString("OGLE_SES", "melodi_1") ?: "melodi_1") }
    var ikindiSes by remember { mutableStateOf(prefs.getString("IKINDI_SES", "melodi_1") ?: "melodi_1") }
    var aksamSes by remember { mutableStateOf(prefs.getString("AKSAM_SES", "melodi_1") ?: "melodi_1") }
    var yatsiSes by remember { mutableStateOf(prefs.getString("YATSI_SES", "melodi_1") ?: "melodi_1") }

    // DÜZELTME: "TAM_IKINDI_SES" artık HER YERDE (okuma + yazma) aynı ASCII anahtarla kullanılıyor.
    // Önceki halinde bazı yerlerde "TAM_İKİNDİ_SES" (Türkçe noktalı İ) kullanılıyordu;
    // bu, SharedPreferences'ta farklı bir anahtar sayıldığı için seçilen ses gerçek
    // alarma hiç yansımıyordu. EzanVaktiHomeScreen.kt de "TAM_IKINDI_SES" (ASCII) okuyor,
    // bu yüzden tüm dosyada bu anahtarı tek tipte tutuyoruz.
    var tamSabahSes by remember { mutableStateOf(prefs.getString("TAM_SABAH_SES", "melodi_1") ?: "melodi_1") }
    var tamOgleSes by remember { mutableStateOf(prefs.getString("TAM_OGLE_SES", "melodi_1") ?: "melodi_1") }
    var tamIkindiSes by remember { mutableStateOf(prefs.getString("TAM_IKINDI_SES", "melodi_1") ?: "melodi_1") }
    var tamAksamSes by remember { mutableStateOf(prefs.getString("TAM_AKSAM_SES", "melodi_1") ?: "melodi_1") }
    var tamYatsiSes by remember { mutableStateOf(prefs.getString("TAM_YATSI_SES", "melodi_1") ?: "melodi_1") }

    var imsakAktif by remember { mutableStateOf(prefs.getBoolean("IMSAK_AKTIF", true)) }
    var imsakDakika by remember { mutableStateOf(prefs.getInt("IMSAK_SURE", 45)) }
    var sabahAktif by remember { mutableStateOf(prefs.getBoolean("SABAH_AKTIF", true)) }
    var sabahDakika by remember { mutableStateOf(prefs.getInt("SABAH_SURE", 45)) }
    var gunesAktif by remember { mutableStateOf(prefs.getBoolean("GUNES_AKTIF", true)) }
    var gunesDakika by remember { mutableStateOf(prefs.getInt("GUNES_SURE", 30)) }
    var ogleAktif by remember { mutableStateOf(prefs.getBoolean("OGLE_AKTIF", true)) }
    var ogleDakika by remember { mutableStateOf(prefs.getInt("OGLE_SURE", 45)) }
    var ikindiAktif by remember { mutableStateOf(prefs.getBoolean("IKINDI_AKTIF", true)) }
    var ikindiDakika by remember { mutableStateOf(prefs.getInt("IKINDI_SURE", 45)) }
    var aksamAktif by remember { mutableStateOf(prefs.getBoolean("AKSAM_AKTIF", true)) }
    var aksamDakika by remember { mutableStateOf(prefs.getInt("AKSAM_SURE", 45)) }
    var yatsiAktif by remember { mutableStateOf(prefs.getBoolean("YATSI_AKTIF", true)) }
    var yatsiDakika by remember { mutableStateOf(prefs.getInt("YATSI_SURE", 45)) }

    var aktifDuzenlenenVakit by remember { mutableStateOf<String?>(null) }
    var geciciDakika by remember { mutableStateOf(45) }

    var sabahVaktinde by remember { mutableStateOf(prefs.getBoolean("SABAH_VAKTINDE", true)) }
    var ogleVaktinde by remember { mutableStateOf(prefs.getBoolean("OGLE_VAKTINDE", true)) }
    var ikindiVaktinde by remember { mutableStateOf(prefs.getBoolean("IKINDI_VAKTINDE", true)) }
    var aksamVaktinde by remember { mutableStateOf(prefs.getBoolean("AKSAM_VAKTINDE", true)) }
    var yatsiVaktinde by remember { mutableStateOf(prefs.getBoolean("YATSI_VAKTINDE", true)) }

    // --- DİĞER AYARLAR ---
    var cumaSessiz by remember { mutableStateOf(prefs.getBoolean("CUMA_SESSIZ", true)) }

    // DÜZELTME: Önceden kullanıcı bu anahtarı açmaya çalışıp izin ekranına
    // yönlendirildiğinde, izni verip GERİ DÖNDÜĞÜNDE anahtar gerçekte hiç
    // açılmıyordu — kullanıcının bir daha dokunması gerekiyordu, ki bunu kimse
    // bilmiyordu. Artık izin sayfasından dönüldüğünde izin gerçekten
    // verilmişse, bekleyen anahtar otomatik açılıp kaydediliyor.
    var bekleyenCumaSessizAcma by remember { mutableStateOf(false) }
    var bekleyenNamazSessizAcma by remember { mutableStateOf(false) }
    var namazVaktindeSessiz by remember { mutableStateOf(prefs.getBoolean("NAMAZ_VAKTINDE_SESSIZ", true)) }
    var sabahImsakteOku by remember { mutableStateOf(prefs.getBoolean("SABAH_IMSAKTE_OKU", false)) }
    var titresim by remember { mutableStateOf(prefs.getBoolean("TITRESIM_DURUMU", false)) }
    var bildirimCubuguAktif by remember { mutableStateOf(prefs.getBoolean("BILDIRIM_CUBUGU_AKTIF", true)) }

    // YENİ ÖZELLİK: Kerahat vakti süresi artık kullanıcı tarafından
    // ayarlanabiliyor (varsayılan: 45 dk) — Ana ekrandaki asansör paneli ve
    // geri sayım bu değeri kullanıyor.
    var kerahatSuresi by remember { mutableStateOf(prefs.getInt("KERAHAT_SURESI_DAKIKA", 45)) }
    var kerahatDialogAcikMi by remember { mutableStateOf(false) }
    var geciciKerahatDakika by remember { mutableStateOf(kerahatSuresi) }
    // DÜZELTME: temaSecimi artık dışarıdan (MainActivity'den) geliyor —
    // bkz. yukarıdaki parametre açıklaması.
    val temaSecimi = temaSecimiDisarida

    // DÜZELTME: Artık "Konya"/"Karatay" sabit varsayılanı kullanılmıyor —
    // hiçbir şehir seçilmemişken kafa karıştırmasın diye nötr bir metin
    // gösteriliyor. Gerçek varsayılan sadece kullanıcı bir şehir seçtiğinde
    // veya konum tespiti başarılı olduğunda oluşur.
    val kaydedilenIl = prefs.getString("SECILEN_IL", "") ?: ""
    val kaydedilenIlce = prefs.getString("SECILEN_ILCE", "") ?: ""

    var sehirSecimPenceresiAcikMi by remember { mutableStateOf(false) }
    var aktifSehir by remember {
        mutableStateOf(if (kaydedilenIl.isBlank()) "Şehir Seçilmedi" else "$kaydedilenIl, $kaydedilenIlce")
    }

    val coroutineScope = rememberCoroutineScope()

    // Rahatsız Etmeyin (DND) izin ekranından dönüldüğünde bekleyen anahtarı uygular.
    val dndIzinLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (notificationManager.isNotificationPolicyAccessGranted) {
            if (bekleyenCumaSessizAcma) {
                cumaSessiz = true
                prefs.edit().putBoolean("CUMA_SESSIZ", true).apply()
            }
            if (bekleyenNamazSessizAcma) {
                namazVaktindeSessiz = true
                prefs.edit().putBoolean("NAMAZ_VAKTINDE_SESSIZ", true).apply()
            }
        } else {
            Toast.makeText(context, "İzin verilmedi, otomatik sessize alma açılamadı.", Toast.LENGTH_LONG).show()
        }
        bekleyenCumaSessizAcma = false
        bekleyenNamazSessizAcma = false
    }
    // --- KONUM BULMA FONKSİYONU ---
    // DÜZELTME: Önceden bu fonksiyon sadece SECILEN_IL/SECILEN_ILCE İSİMLERİNİ
    // kaydediyordu, "SECILEN_ILCE_KODU"'nu HİÇ ayarlamıyordu. Bu yüzden konuma
    // izin verilip "Konum bulundu" mesajı çıksa bile HomeScreen hiçbir zaman
    // vakit çekemiyordu (kod her zaman boştu). Artık Geocoder'dan gelen il/ilçe
    // adı, JsonYardimcisi.konumdanKoduCoz ile gerçek Diyanet koduna çevrilip
    // ancak o zaman kaydediliyor.
    fun konumuBulVeKaydet() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    try {
                        val geocoder = Geocoder(context, Locale("tr", "TR"))
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            val il = address.adminArea ?: address.subAdminArea ?: "Bilinmiyor"
                            val ilce = address.subAdminArea ?: address.locality ?: "Bilinmiyor"

                            coroutineScope.launch {
                                val sonuc = JsonYardimcisi.konumdanKoduCoz(context, il, ilce)
                                if (sonuc != null) {
                                    prefs.edit()
                                        .putString("SECILEN_IL", il)
                                        .putString("SECILEN_ILCE", ilce)
                                        .putString("SECILEN_ILCE_KODU", sonuc.ilceKodu)
                                        .putString("SECILEN_SEHIR_KODU", sonuc.sehirKodu)
                                        .apply()
                                    aktifSehir = "$il, $ilce"
                                    Toast.makeText(context, "Konum bulundu: $il, $ilce", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Konum bulundu ($il, $ilce) ama vakit kodu eşleştirilemedi. Lütfen 'Şehri Manuel Değiştir' ile seçin.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Adres bulunamadı. Lütfen şehri manuel seçin.", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Adres çözümlenemedi.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Konum bulunamadı. Lütfen GPS'in açık olduğundan emin olun.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    // --- KONUM İZNİ İSTEYİCİ ---
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            konumuBulVeKaydet()
        } else {
            Toast.makeText(context, "Konum izni reddedildi. Konya/Karatay kullanılacak.", Toast.LENGTH_LONG).show()
        }
    }

    val isDarkMode = when (temaSecimi) {
        "Gece" -> true
        "Gündüz" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    // DÜZELTME: Vurgu rengi artık sabit değil, kullanıcının Ayarlar'dan seçtiği
    // renk temasından okunuyor (varsayılan: Altın). seciliRenkTemasi de artık
    // dışarıdan (MainActivity'den) geliyor — bkz. yukarıdaki açıklama.
    val seciliRenkTemasi = seciliRenkTemasiDisarida
    val vurguRengi = seciliRenkTemasi.renk

    // DÜZELTME: Sayfa ve kart arka planları artık sabit lacivert değil —
    // seçili tema rengine göre koyulaştırılmış/açıklaştırılmış bir ton.
    val arkaPlanRengi = TemaYardimcisi.sayfaArkaPlaniniHesapla(vurguRengi, isDarkMode)
    val kartArkaPlani = if (isDarkMode) TemaYardimcisi.kartArkaPlaniniHesapla(vurguRengi, isDarkMode).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.75f)
    val yaziRengi = if (isDarkMode) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val ikincilYaziRengi = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    // YENİ: Ana ekrandaki (HomeScreen) arka plan resmi artık Ayarlar ekranında da
    // gösteriliyor — resim + tema rengine göre yarı saydam bir katman (Sistem/
    // Gece/Gündüz moduna göre otomatik uyum sağlıyor, HomeScreen'deki mantıkla aynı).
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.mevlana_bg),
            contentDescription = "Arka Plan",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(modifier = Modifier.fillMaxSize().background(arkaPlanRengi.copy(alpha = 0.80f)))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            // --- ÜST BAR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onGeriTiklandi() },
                    modifier = Modifier.background(vurguRengi.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Geri", tint = vurguRengi)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "Uygulama Ayarları", color = yaziRengi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- ANA KARTLAR ALANI ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. KART: GENEL & KONUM
                AyarKarti(kartArkaPlani) {
                    AyarBaslik("Konum ve Veriler", vurguRengi)
                    Spacer(modifier = Modifier.height(8.dp))
                    AyarTiklamaSatiri("Konuma Göre Ayarla", "Bulunduğun konumu otomatik tespit et", yaziRengi, ikincilYaziRengi, vurguRengi) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            konumuBulVeKaydet()
                        } else {
                            locationPermissionLauncher.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        }
                    }
                    Divider(color = ikincilYaziRengi.copy(alpha = 0.15f), thickness = 0.8.dp)
                    AyarTiklamaSatiri("Şehri Manuel Değiştir", aktifSehir, yaziRengi, ikincilYaziRengi, vurguRengi) {
                        sehirSecimPenceresiAcikMi = true
                    }
                    Divider(color = ikincilYaziRengi.copy(alpha = 0.15f), thickness = 0.8.dp)
                    AyarTiklamaSatiri("Vakitleri Güncelle", "İnternetten güncel veriyi çek", yaziRengi, ikincilYaziRengi, vurguRengi) {
                        coroutineScope.launch(context = Dispatchers.IO) {
                            try {
                                val sharedPreferences = context.getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE)
                                // DÜZELTME: Buradaki eski "9541" (yanlış şehre ait
                                // olduğu daha önce doğrulanmıştı) varsayılanı
                                // gözden kaçmış, düzeltildi.
                                val kaydedilenIlceKodu = sharedPreferences.getString("SECILEN_ILCE_KODU", "") ?: ""
                                if (kaydedilenIlceKodu.isBlank()) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Önce bir şehir seçmelisiniz.", Toast.LENGTH_SHORT).show()
                                    }
                                    return@launch
                                }

                                val vakitlerListesi = EzanApiService.instance.getVakitler(ilceKodu = kaydedilenIlceKodu)

                                withContext(Dispatchers.Main) {
                                    if (vakitlerListesi.isNotEmpty()) {
                                        Toast.makeText(context, "Vakitler başarıyla güncellendi", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Güncellenemedi.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Bağlantı hatası!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                // 2. KART: GÖRÜNÜM MODU
                AyarKarti(kartArkaPlani) {
                    AyarBaslik("Görünüm Modu", vurguRengi)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp) // Aralarına minik boşluk verdik
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SecenekButon("Sistem", temaSecimi == "Sistem", vurguRengi) {
                                onTemaSecimiDegis("Sistem")
                                prefs.edit().putString("TEMA_SECIMI", "Sistem").apply()
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            SecenekButon("Gece", temaSecimi == "Gece", vurguRengi) {
                                onTemaSecimiDegis("Gece")
                                prefs.edit().putString("TEMA_SECIMI", "Gece").apply()
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            SecenekButon("Gündüz", temaSecimi == "Gündüz", vurguRengi) {
                                onTemaSecimiDegis("Gündüz")
                                prefs.edit().putString("TEMA_SECIMI", "Gündüz").apply()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = ikincilYaziRengi.copy(alpha = 0.15f), thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // YENİ ÖZELLİK: Renk teması seçici — 5 hazır renk arasından
                    // seçim yapılabiliyor, seçim anında tüm uygulamaya yansıyor.
                    Text(text = "Renk Teması", color = yaziRengi, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        RenkTemasi.entries.forEach { tema ->
                            RenkSecenegi(
                                renk = tema.renk,
                                seciliMi = seciliRenkTemasi == tema,
                                cerceveRengi = yaziRengi,
                                onClick = {
                                    onRenkTemasiDegis(tema)
                                    TemaYardimcisi.temayiKaydet(context, tema)
                                }
                            )
                        }
                    }
                }

                // 3. KART: VAKİTLERDEN ÖNCE UYARILAR
                AyarKarti(kartArkaPlani) {
                    AyarBaslik("Vakitlerden Önce Uyarılar", vurguRengi)
                    Spacer(modifier = Modifier.height(8.dp))

                    GelismisAyarSatiri("İmsaktan", imsakAktif, "$imsakDakika dk. önce", yaziRengi, ikincilYaziRengi, vurguRengi, sesIsminiBul(imsakSes),
                        onSwitchChange = { imsakAktif = it; prefs.edit().putBoolean("IMSAK_AKTIF", it).apply() }, onSureClick = { geciciDakika = imsakDakika; aktifDuzenlenenVakit = "İmsak" }, onSesClick = { aktifSesDuzenlenenVakit = "İmsak"; sesDialogAcikMi = true })

                    GelismisAyarSatiri("Sabahtan", sabahAktif, "$sabahDakika dk. önce", yaziRengi, ikincilYaziRengi, vurguRengi, sesIsminiBul(sabahSes),
                        onSwitchChange = { sabahAktif = it; prefs.edit().putBoolean("SABAH_AKTIF", it).apply() }, onSureClick = { geciciDakika = sabahDakika; aktifDuzenlenenVakit = "Sabah" }, onSesClick = { aktifSesDuzenlenenVakit = "Sabah"; sesDialogAcikMi = true })

                    GelismisAyarSatiri("Güneş'ten", gunesAktif, "$gunesDakika dk. önce", yaziRengi, ikincilYaziRengi, vurguRengi, sesIsminiBul(gunesSes),
                        onSwitchChange = { gunesAktif = it; prefs.edit().putBoolean("GUNES_AKTIF", it).apply() }, onSureClick = { geciciDakika = gunesDakika; aktifDuzenlenenVakit = "Güneş" }, onSesClick = { aktifSesDuzenlenenVakit = "Güneş"; sesDialogAcikMi = true })

                    GelismisAyarSatiri("Öğleden", ogleAktif, "$ogleDakika dk. önce", yaziRengi, ikincilYaziRengi, vurguRengi, sesIsminiBul(ogleSes),
                        onSwitchChange = { ogleAktif = it; prefs.edit().putBoolean("OGLE_AKTIF", it).apply() }, onSureClick = { geciciDakika = ogleDakika; aktifDuzenlenenVakit = "Öğle" }, onSesClick = { aktifSesDuzenlenenVakit = "Öğle"; sesDialogAcikMi = true })

                    GelismisAyarSatiri("İkindiden", ikindiAktif, "$ikindiDakika dk. önce", yaziRengi, ikincilYaziRengi, vurguRengi, sesIsminiBul(ikindiSes),
                        onSwitchChange = { ikindiAktif = it; prefs.edit().putBoolean("IKINDI_AKTIF", it).apply() }, onSureClick = { geciciDakika = ikindiDakika; aktifDuzenlenenVakit = "İkindi" }, onSesClick = { aktifSesDuzenlenenVakit = "İkindi"; sesDialogAcikMi = true })

                    GelismisAyarSatiri("Akşamdan", aksamAktif, "$aksamDakika dk. önce", yaziRengi, ikincilYaziRengi, vurguRengi, sesIsminiBul(aksamSes),
                        onSwitchChange = { aksamAktif = it; prefs.edit().putBoolean("AKSAM_AKTIF", it).apply() }, onSureClick = { geciciDakika = aksamDakika; aktifDuzenlenenVakit = "Akşam" }, onSesClick = { aktifSesDuzenlenenVakit = "Akşam"; sesDialogAcikMi = true })

                    GelismisAyarSatiri("Yatsıdan", yatsiAktif, "$yatsiDakika dk. önce", yaziRengi, ikincilYaziRengi, vurguRengi, sesIsminiBul(yatsiSes),
                        onSwitchChange = { yatsiAktif = it; prefs.edit().putBoolean("YATSI_AKTIF", it).apply() }, onSureClick = { geciciDakika = yatsiDakika; aktifDuzenlenenVakit = "Yatsı" }, onSesClick = { aktifSesDuzenlenenVakit = "Yatsı"; sesDialogAcikMi = true })
                }

                // 4. KART: VAKİT ZAMANINDA UYARILAR
                AyarKarti(kartArkaPlani) {
                    AyarBaslik("Vakit Zamanında Uyarılar", vurguRengi)
                    Spacer(modifier = Modifier.height(8.dp))

                    AyarSwitchVeSesSatiri("Sabah Ezanı", sabahVaktinde, yaziRengi, ikincilYaziRengi, vurguRengi, sesIsminiBul(tamSabahSes), degis = { sabahVaktinde = it; prefs.edit().putBoolean("SABAH_VAKTINDE", it).apply() }, onSesClick = { aktifSesDuzenlenenVakit = "Tam_Sabah"; sesDialogAcikMi = true })
                    AyarSwitchVeSesSatiri("Öğle Ezanı", ogleVaktinde, yaziRengi, ikincilYaziRengi, vurguRengi, sesIsminiBul(tamOgleSes), degis = { ogleVaktinde = it; prefs.edit().putBoolean("OGLE_VAKTINDE", it).apply() }, onSesClick = { aktifSesDuzenlenenVakit = "Tam_Öğle"; sesDialogAcikMi = true })
                    AyarSwitchVeSesSatiri("İkindi Ezanı", ikindiVaktinde, yaziRengi, ikincilYaziRengi, vurguRengi, sesIsminiBul(tamIkindiSes), degis = { ikindiVaktinde = it; prefs.edit().putBoolean("IKINDI_VAKTINDE", it).apply() }, onSesClick = { aktifSesDuzenlenenVakit = "Tam_İkindi"; sesDialogAcikMi = true })
                    AyarSwitchVeSesSatiri("Akşam Ezanı", aksamVaktinde, yaziRengi, ikincilYaziRengi, vurguRengi, sesIsminiBul(tamAksamSes), degis = { aksamVaktinde = it; prefs.edit().putBoolean("AKSAM_VAKTINDE", it).apply() }, onSesClick = { aktifSesDuzenlenenVakit = "Tam_Akşam"; sesDialogAcikMi = true })
                    AyarSwitchVeSesSatiri("Yatsı Ezanı", yatsiVaktinde, yaziRengi, ikincilYaziRengi, vurguRengi, sesIsminiBul(tamYatsiSes), degis = { yatsiVaktinde = it; prefs.edit().putBoolean("YATSI_VAKTINDE", it).apply() }, onSesClick = { aktifSesDuzenlenenVakit = "Tam_Yatsı"; sesDialogAcikMi = true })
                }

                // YENİ KART: KERAHAT VAKTİ AYARI
                AyarKarti(kartArkaPlani) {
                    AyarTiklamaSatiri(
                        baslik = "Kerahat Vakti",
                        altYazi = "$kerahatSuresi dk",
                        renk = yaziRengi,
                        altRenk = ikincilYaziRengi,
                        vurguRengi = vurguRengi
                    ) {
                        geciciKerahatDakika = kerahatSuresi
                        kerahatDialogAcikMi = true
                    }
                }

                // 5. KART: DİĞER AYARLAR VE OTOMASYON
                AyarKarti(kartArkaPlani) {
                    AyarBaslik("Bildirim Ayarları", vurguRengi)
                    Spacer(modifier = Modifier.height(8.dp))

                    AyarSwitchSatiri("Cuma Vaktinde Otomatik Sessiz", cumaSessiz, yaziRengi, vurguRengi) { yeniDurum ->
                        if (yeniDurum) {
                            if (!notificationManager.isNotificationPolicyAccessGranted) {
                                bekleyenCumaSessizAcma = true
                                dndIzinLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                            } else {
                                cumaSessiz = true; prefs.edit().putBoolean("CUMA_SESSIZ", true).apply()
                            }
                        } else {
                            cumaSessiz = false; prefs.edit().putBoolean("CUMA_SESSIZ", false).apply()
                        }
                    }

                    Divider(color = ikincilYaziRengi.copy(alpha = 0.15f), thickness = 0.8.dp)

                    AyarSwitchSatiri("Namaz Vakitlerinde Telefonu Sessize Al", namazVaktindeSessiz, yaziRengi, vurguRengi) { yeniDurum ->
                        if (yeniDurum) {
                            if (!notificationManager.isNotificationPolicyAccessGranted) {
                                bekleyenNamazSessizAcma = true
                                dndIzinLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                            } else {
                                namazVaktindeSessiz = true; prefs.edit().putBoolean("NAMAZ_VAKTINDE_SESSIZ", true).apply()
                            }
                        } else {
                            namazVaktindeSessiz = false; prefs.edit().putBoolean("NAMAZ_VAKTINDE_SESSIZ", false).apply()
                        }
                    }

                    Divider(color = ikincilYaziRengi.copy(alpha = 0.15f), thickness = 0.8.dp)

                    AyarSwitchSatiri("Sabah Ezanını İmsak Vaktinde Oku", sabahImsakteOku, yaziRengi, vurguRengi) { yeniDurum ->
                        sabahImsakteOku = yeniDurum
                        prefs.edit().putBoolean("SABAH_IMSAKTE_OKU", yeniDurum).apply()
                    }

                    Divider(color = ikincilYaziRengi.copy(alpha = 0.15f), thickness = 0.8.dp)

                    AyarSwitchSatiri("Titresim", titresim, yaziRengi, vurguRengi) { yeniDurum ->
                        titresim = yeniDurum
                        prefs.edit().putBoolean("TITRESIM_DURUMU", yeniDurum).apply()
                    }

                    Divider(color = ikincilYaziRengi.copy(alpha = 0.15f), thickness = 0.8.dp)

                    // YENİ ÖZELLİK: Bildirim çubuğunda (telefonu yukarıdan kaydırınca
                    // görünen alan) günün vakitlerini gösteren kalıcı bildirim.
                    // Servis, bu anahtar açıldığında başlar, kapatıldığında durur.
                    AyarSwitchSatiri("Bildirim Çubuğunda Vakitleri Göster", bildirimCubuguAktif, yaziRengi, vurguRengi) { yeniDurum ->
                        bildirimCubuguAktif = yeniDurum
                        prefs.edit().putBoolean("BILDIRIM_CUBUGU_AKTIF", yeniDurum).apply()
                        if (yeniDurum) {
                            EzanBildirimServisi.baslat(context)
                        } else {
                            EzanBildirimServisi.durdur(context)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // YENİ ÖZELLİK: İstek ve Öneri — telefonun e-posta uygulamasını
                // açıp "Kime", "Konu" ve mesaj gövdesinin altına cihaz
                // bilgilerini otomatik dolduruyor.
                AyarKarti(kartArkaPlani) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val paketBilgisi = context.packageManager.getPackageInfo(context.packageName, 0)
                                    val uygulamaSurumu = paketBilgisi.versionName ?: "Bilinmiyor"
                                    val mesajGovdesi = """


                                        --------------------------
                                        Device Info:
                                        Manufacturer: ${Build.MANUFACTURER}
                                        Model: ${Build.MODEL}
                                        OS Version: ${Build.VERSION.RELEASE}
                                        SDK Version: ${Build.VERSION.SDK_INT}
                                        App Version: $uygulamaSurumu
                                    """.trimIndent()

                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:")
                                        putExtra(Intent.EXTRA_EMAIL, arrayOf("ezanvaktimdestek@gmail.com"))
                                        putExtra(Intent.EXTRA_SUBJECT, "Ezan Vaktim Uygulaması - Geri Bildirim")
                                        putExtra(Intent.EXTRA_TEXT, mesajGovdesi)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "İstek ve Öneri", color = yaziRengi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "Görüş, hata bildirimi veya önerilerinizi bize e-posta ile iletin", color = ikincilYaziRengi, fontSize = 12.sp)
                        }
                        Icon(imageVector = Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = vurguRengi)
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }

        // --- DAKİKA AYARLAMA POPUP DİALOGU ---
        if (aktifDuzenlenenVakit != null) {
            AlertDialog(
                onDismissRequest = { aktifDuzenlenenVakit = null },
                containerColor = kartArkaPlani,
                shape = RoundedCornerShape(20.dp),
                title = { Text(text = "$aktifDuzenlenenVakit Süresi (Dk)", color = yaziRengi, fontWeight = FontWeight.Bold) },
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { if (geciciDakika > 1) geciciDakika -= 1 }, modifier = Modifier.size(48.dp).background(vurguRengi.copy(alpha = 0.1f), RoundedCornerShape(10.dp))) {
                            Text(text = "-", color = vurguRengi, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(text = geciciDakika.toString(), color = vurguRengi, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                        IconButton(onClick = { geciciDakika += 1 }, modifier = Modifier.size(48.dp).background(vurguRengi.copy(alpha = 0.1f), RoundedCornerShape(10.dp))) {
                            Text(text = "+", color = vurguRengi, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            when (aktifDuzenlenenVakit) {
                                "İmsak" -> { imsakDakika = geciciDakika; prefs.edit().putInt("IMSAK_SURE", geciciDakika).apply() }
                                "Sabah" -> { sabahDakika = geciciDakika; prefs.edit().putInt("SABAH_SURE", geciciDakika).apply() }
                                "Güneş" -> { gunesDakika = geciciDakika; prefs.edit().putInt("GUNES_SURE", geciciDakika).apply() }
                                "Öğle" -> { ogleDakika = geciciDakika; prefs.edit().putInt("OGLE_SURE", geciciDakika).apply() }
                                "İkindi" -> { ikindiDakika = geciciDakika; prefs.edit().putInt("IKINDI_SURE", geciciDakika).apply() }
                                "Akşam" -> { aksamDakika = geciciDakika; prefs.edit().putInt("AKSAM_SURE", geciciDakika).apply() }
                                "Yatsı" -> { yatsiDakika = geciciDakika; prefs.edit().putInt("YATSI_SURE", geciciDakika).apply() }
                            }
                            aktifDuzenlenenVakit = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = vurguRengi)
                    ) { Text("Kaydet", color = Color.White, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { aktifDuzenlenenVakit = null }) { Text("İptal", color = ikincilYaziRengi) }
                }
            )
        }

        // --- KERAHAT VAKTİ SÜRESİ AYARLAMA POPUP DİALOGU ---
        if (kerahatDialogAcikMi) {
            AlertDialog(
                onDismissRequest = { kerahatDialogAcikMi = false },
                containerColor = kartArkaPlani,
                shape = RoundedCornerShape(20.dp),
                title = { Text(text = "Kerahat Vakti Süresi (Dk)", color = yaziRengi, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = "Güneşin doğuşundan sonra, öğle ve akşam vaktinden önce namaz kılınmayan sürenin uzunluğu.",
                            color = ikincilYaziRengi,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { if (geciciKerahatDakika > 1) geciciKerahatDakika -= 1 }, modifier = Modifier.size(48.dp).background(vurguRengi.copy(alpha = 0.1f), RoundedCornerShape(10.dp))) {
                                Text(text = "-", color = vurguRengi, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(text = geciciKerahatDakika.toString(), color = vurguRengi, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                            IconButton(onClick = { geciciKerahatDakika += 1 }, modifier = Modifier.size(48.dp).background(vurguRengi.copy(alpha = 0.1f), RoundedCornerShape(10.dp))) {
                                Text(text = "+", color = vurguRengi, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            kerahatSuresi = geciciKerahatDakika
                            prefs.edit().putInt("KERAHAT_SURESI_DAKIKA", geciciKerahatDakika).apply()
                            kerahatDialogAcikMi = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = vurguRengi)
                    ) { Text("Kaydet", color = Color.White, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { kerahatDialogAcikMi = false }) { Text("İptal", color = ikincilYaziRengi) }
                }
            )
        }

        // --- SES SEÇİM DİALOGU ÇAĞRISI ---
        if (sesDialogAcikMi && aktifSesDuzenlenenVakit != null) {
            val mevcutSeciliSes = when(aktifSesDuzenlenenVakit) {
                "İmsak" -> imsakSes; "Sabah" -> sabahSes; "Güneş" -> gunesSes; "Öğle" -> ogleSes; "İkindi" -> ikindiSes; "Akşam" -> aksamSes; "Yatsı" -> yatsiSes
                "Tam_Sabah" -> tamSabahSes; "Tam_Öğle" -> tamOgleSes; "Tam_İkindi" -> tamIkindiSes; "Tam_Akşam" -> tamAksamSes; "Tam_Yatsı" -> tamYatsiSes
                else -> "melodi_1"
            }

            SesSecimDialog(
                gosteriliyorMu = sesDialogAcikMi,
                mevcutSeciliDosyaAdi = mevcutSeciliSes,
                dialogArkaplanRengi = kartArkaPlani,
                vurguRengi = vurguRengi,
                onKapat = { sesDialogAcikMi = false; aktifSesDuzenlenenVakit = null },
                onSesKaydet = { secilenSes ->
                    val dosyaAdi = secilenSes.dosyaAdi
                    when(aktifSesDuzenlenenVakit) {
                        "İmsak" -> { imsakSes = dosyaAdi; prefs.edit().putString("IMSAK_SES", dosyaAdi).apply() }
                        "Sabah" -> { sabahSes = dosyaAdi; prefs.edit().putString("SABAH_SES", dosyaAdi).apply() }
                        "Güneş" -> { gunesSes = dosyaAdi; prefs.edit().putString("GUNES_SES", dosyaAdi).apply() }
                        "Öğle" -> { ogleSes = dosyaAdi; prefs.edit().putString("OGLE_SES", dosyaAdi).apply() }
                        "İkindi" -> { ikindiSes = dosyaAdi; prefs.edit().putString("IKINDI_SES", dosyaAdi).apply() }
                        "Akşam" -> { aksamSes = dosyaAdi; prefs.edit().putString("AKSAM_SES", dosyaAdi).apply() }
                        "Yatsı" -> { yatsiSes = dosyaAdi; prefs.edit().putString("YATSI_SES", dosyaAdi).apply() }
                        "Tam_Sabah" -> { tamSabahSes = dosyaAdi; prefs.edit().putString("TAM_SABAH_SES", dosyaAdi).apply() }
                        "Tam_Öğle" -> { tamOgleSes = dosyaAdi; prefs.edit().putString("TAM_OGLE_SES", dosyaAdi).apply() }
                        // DÜZELTME: "TAM_İKİNDİ_SES" -> "TAM_IKINDI_SES" (ASCII, HomeScreen'in okuduğu anahtarla aynı)
                        "Tam_İkindi" -> { tamIkindiSes = dosyaAdi; prefs.edit().putString("TAM_IKINDI_SES", dosyaAdi).apply() }
                        "Tam_Akşam" -> { tamAksamSes = dosyaAdi; prefs.edit().putString("TAM_AKSAM_SES", dosyaAdi).apply() }
                        "Tam_Yatsı" -> { tamYatsiSes = dosyaAdi; prefs.edit().putString("TAM_YATSI_SES", dosyaAdi).apply() }
                    }
                    sesDialogAcikMi = false; aktifSesDuzenlenenVakit = null
                },
                onHepsineUygula = { secilenSes ->
                    val dosyaAdi = secilenSes.dosyaAdi
                    if (aktifSesDuzenlenenVakit?.startsWith("Tam_") == true) {
                        tamSabahSes = dosyaAdi; tamOgleSes = dosyaAdi; tamIkindiSes = dosyaAdi; tamAksamSes = dosyaAdi; tamYatsiSes = dosyaAdi
                        // DÜZELTME: "TAM_İKİNDİ_SES" -> "TAM_IKINDI_SES" (ASCII, HomeScreen'in okuduğu anahtarla aynı)
                        prefs.edit().putString("TAM_SABAH_SES", dosyaAdi).putString("TAM_OGLE_SES", dosyaAdi).putString("TAM_IKINDI_SES", dosyaAdi).putString("TAM_AKSAM_SES", dosyaAdi).putString("TAM_YATSI_SES", dosyaAdi).apply()
                    } else {
                        imsakSes = dosyaAdi; sabahSes = dosyaAdi; gunesSes = dosyaAdi; ogleSes = dosyaAdi; ikindiSes = dosyaAdi; aksamSes = dosyaAdi; yatsiSes = dosyaAdi
                        prefs.edit().putString("IMSAK_SES", dosyaAdi).putString("SABAH_SES", dosyaAdi).putString("GUNES_SES", dosyaAdi).putString("OGLE_SES", dosyaAdi).putString("IKINDI_SES", dosyaAdi).putString("AKSAM_SES", dosyaAdi).putString("YATSI_SES", dosyaAdi).apply()
                    }
                    sesDialogAcikMi = false; aktifSesDuzenlenenVakit = null
                }
            )
        }

        // --- ŞEHİR SEÇİM DİALOGU ---
        if (sehirSecimPenceresiAcikMi) {
            SehirSecimDialog(
                onSecildi = { yeniIl, yeniIlce, yeniKod, yeniSehirKodu ->
                    aktifSehir = "$yeniIl, $yeniIlce"
                    prefs.edit()
                        .putString("SECILEN_IL", yeniIl)
                        .putString("SECILEN_ILCE", yeniIlce)
                        .putString("SECILEN_ILCE_KODU", yeniKod)
                        .putString("SECILEN_SEHIR_KODU", yeniSehirKodu)
                        .apply()
                    sehirSecimPenceresiAcikMi = false
                },
                onKapat = { sehirSecimPenceresiAcikMi = false },
                vurguRengi = vurguRengi
            )
        }
    }
}

// --- GÖRSEL YARDIMCI BİLEŞENLER ---

@Composable
fun AyarKarti(arkaplanRengi: Color, icerik: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        // DÜZELTME: Tam opak (%100) yerine %95 yarı saydam — ana ekrandaki
        // (HomeScreen) VakitSatiri kartlarıyla aynı stil (onlar da %90-95
        // aralığında saydam, tam opak değil).
        colors = CardDefaults.cardColors(containerColor = arkaplanRengi.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            content = icerik
        )
    }
}

@Composable
fun AyarBaslik(baslik: String, vurguRengi: Color) {
    Text(
        text = baslik,
        color = vurguRengi,
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
fun GelismisAyarSatiri(baslik: String, aktif: Boolean, sureMetni: String, renk: Color, ikincilRenk: Color, vurguRengi: Color, sesAdi: String, onSwitchChange: (Boolean) -> Unit, onSureClick: () -> Unit, onSesClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = aktif, onCheckedChange = onSwitchChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = vurguRengi))
                Spacer(modifier = Modifier.width(12.dp))
                // DÜZELTME: Sistem yazı boyutu büyütülmüş cihazlarda metin
                // kesiliyordu; weight(1f) ile artık taşarsa alt satıra kayıyor.
                Text(text = baslik, color = renk, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onSureClick) {
                Text(text = sureMetni, color = vurguRengi, fontSize = 13.sp, fontWeight = FontWeight.Bold, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = sesAdi, color = ikincilRenk, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
            TextButton(onClick = onSesClick) { Text(text = "Sesi Değiştir", color = vurguRengi, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun AyarSwitchVeSesSatiri(baslik: String, durum: Boolean, renk: Color, ikincilRenk: Color, vurguRengi: Color, sesAdi: String, degis: (Boolean) -> Unit, onSesClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            // DÜZELTME: Sistem yazı boyutu büyütülmüş (yaşlı kullanıcılar gibi)
            // cihazlarda metin kesiliyordu. weight(1f) ile metin artık sığmadığında
            // kesilmek yerine alt satıra kayıyor, Switch'e yer açılıyor.
            Text(text = baslik, color = renk, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Switch(checked = durum, onCheckedChange = degis, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = vurguRengi))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = sesAdi, color = ikincilRenk, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
            TextButton(onClick = onSesClick) { Text(text = "Sesi Değiştir", color = vurguRengi, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun AyarSwitchSatiri(baslik: String, durum: Boolean, renk: Color, vurguRengi: Color, degis: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = baslik, color = renk, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = durum, onCheckedChange = degis, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = vurguRengi))
    }
}

@Composable
fun AyarTiklamaSatiri(baslik: String, altYazi: String, renk: Color, altRenk: Color, vurguRengi: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = baslik, color = renk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (altYazi.isNotEmpty()) {
                Text(text = altYazi, color = altRenk, fontSize = 12.sp)
            }
        }
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(36.dp).background(vurguRengi.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
        ) {
            Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = "Git", tint = vurguRengi)
        }
    }
}

@Composable
fun SecenekButon(text: String, secili: Boolean, vurguRengi: Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        // DÜZELTME: Butonlar biraz daha büyük görünsün diye iç boşluk
        // (özellikle dikey) artırıldı. Yatay boşluk yine de dar tutuldu ki
        // 3 buton yan yana sığarken metin taşmasın.
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (secili) vurguRengi.copy(alpha = 0.15f) else Color.Transparent,
            contentColor = if (secili) vurguRengi else Color.Gray
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (secili) vurguRengi else Color.Gray.copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            fontWeight = if (secili) FontWeight.Bold else FontWeight.Normal,
            // DÜZELTME: Yazı boyutu 13sp'den 14sp'ye çıkarıldı — butonun
            // büyümesiyle orantılı, hâlâ tek satıra sığıyor.
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
fun sesIsminiBul(dosyaAdi: String): String {
    return sesListesi.find { it.dosyaAdi == dosyaAdi }?.isim ?: "Melodi 1"
}

@Composable
fun RenkSecenegi(renk: Color, seciliMi: Boolean, cerceveRengi: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(if (seciliMi) 38.dp else 32.dp)
            .clip(CircleShape)
            .background(renk)
            .border(
                width = if (seciliMi) 2.5.dp else 0.dp,
                color = cerceveRengi,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // DÜZELTME: Sadece çerçeve rengi bazı durumlarda (koyu bir renk
        // üzerinde koyu çerçeve gibi) yetersiz kontrast verebiliyordu.
        // Seçili olduğunda ek olarak bir onay (✓) simgesi de gösteriliyor
        // — hangi tema modunda/renkte olursa olsun her zaman net görünür.
        if (seciliMi) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Seçili",
                tint = Color.White,
                modifier = Modifier
                    .size(18.dp)
                    .shadow(elevation = 2.dp, shape = CircleShape)
            )
        }
    }
}