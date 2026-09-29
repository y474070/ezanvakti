package com.colak.ezanvakti
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
// DÜZELTME: Kurulum akışı tamamen yeniden tasarlandı — kullanıcının verdiği
// referans uygulamanın (koyu tema + yeşil vurgu) görünümüne benzer şekilde.
// 3 adım: 1) Selamun Aleykum (konum izni/manuel seçim), 2) Lokasyon + tüm
// bildirim/tema ayarları, 3) Bildirim/Alarm/Pil izinleri (onaylanmadan
// geçilemiyor). Eski, ayrı "açıklama pencereleri" (izin öncesi ayrı
// AlertDialog'lar) kaldırıldı — artık izinler doğrudan bu 3. ekranda,
// "İzin Ver" / "✓ Verildi" satırlarıyla isteniyor.
private enum class KurulumAdimi { HOSGELDIN, LOKASYON, IZINLER }
// YENİ ÖZELLİK: Kuruluş artık Görünüm Modu'na (Sistem/Gece/Gündüz) göre
// CANLI olarak koyu/açık arasında geçiş yapabiliyor — önceden hep sabit
// koyu temaydı. Koyu ve açık renk çiftleri burada tanımlı.
private val KoyuArkaPlan = Color(0xFF0B0F1A)
private val AcikArkaPlan = Color(0xFFF4F6F9)
// DÜZELTME: Kurulumun varsayılan vurgu rengi artık yeşil değil,
// uygulamanın genel varsayılan tema rengiyle (RenkTemasi.ALTIN) AYNI —
// altın/sarı. LokasyonEkrani zaten seçili temaya göre canlı değişiyor;
// bu sabit sadece henüz renk seçici olmayan (Hoşgeldin, İzinler)
// ekranlarda kullanılıyor.
private val VarsayilanVurgu = Color(0xFFF59E0B)
private val YaziBeyazKoyu = Color(0xFFF1F5F9)
private val YaziIkincilKoyu = Color(0xFF94A3B8)
private val YaziKoyuAcik = Color(0xFF0F172A)
private val YaziIkincilAcik = Color(0xFF64748B)

// YENİ ÖZELLİK: Kurulumun tüm ekranları bu paleti CompositionLocal
// üzerinden okuyor — Görünüm Modu değişince, üstteki tek bir yerden
// hesaplanan palet sayesinde her ekran otomatik güncelleniyor.
private data class KurulumRenkPaleti(
    val arkaPlan: Color,
    val yaziBeyaz: Color,
    val yaziIkincil: Color,
    val isDarkMode: Boolean
)

private val LocalKurulumRenkleri = compositionLocalOf {
    KurulumRenkPaleti(KoyuArkaPlan, YaziBeyazKoyu, YaziIkincilKoyu, true)
}

// YENİ ÖZELLİK: "İzin Ver"e basınca sistem ayarlarına gitmeden önce
// gösterilen yardım penceresinin içeriği. Sistem ekranının başlığını ve
// kullanıcıya ne yapması gerektiğini anlatan açıklamayı tutar.
private data class IzinYardimIcerigi(
    val sistemBasligi: String,
    val aciklamaMetni: String
)
private val UstteGosterYardimi = IzinYardimIcerigi(
    sistemBasligi = "Üstte Göster",
    aciklamaMetni = "Ezan Vaktim uygulamasını seçip gerekli izni verin."
)
private val AlarmYardimi = IzinYardimIcerigi(
    sistemBasligi = "Alarmlar ve Anımsatmalar",
    aciklamaMetni = "Ezan Vaktim uygulamasını seçip gerekli izni verin."
)
private val DndYardimi = IzinYardimIcerigi(
    sistemBasligi = "Rahatsız Etme Erişimi",
    aciklamaMetni = "Ezan Vaktim uygulamasını seçip gerekli izni verin."
)
@Composable
fun WelcomeScreen(onDevamTiklandi: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var adim by remember { mutableStateOf(KurulumAdimi.HOSGELDIN) }
    var kullanimKosullariAcikMi by remember { mutableStateOf(false) }
    var gizlilikPolitikasiAcikMi by remember { mutableStateOf(false) }
    var konumBelirleniyorMu by remember { mutableStateOf(false) }
    // --- LOKASYON durumları ---
    // YENİ ÖZELLİK: Seçili tema rengi artık üst seviyede tutuluyor — hem
    // Lokasyon ekranındaki canlı önizleme hem de İzinler ekranındaki
    // vurgular AYNI seçili rengi kullansın diye.
    var seciliRenkTemasi by remember { mutableStateOf(TemaYardimcisi.seciliTemayiOku(context)) }
    // YENİ ÖZELLİK: Görünüm Modu (Sistem/Gece/Gündüz) de artık üst
    // seviyede — seçilince kuruluşun TÜM ekranları CANLI olarak
    // koyu/açık arasında geçiş yapıyor (Ayarlar'daki AYNI mantık).
    var temaSecimi by remember { mutableStateOf(prefs.getString("TEMA_SECIMI", "Sistem") ?: "Sistem") }
    val sistemKaranlikMi = isSystemInDarkTheme()
    val isDarkMode = when (temaSecimi) {
        "Gece" -> true
        "Gündüz" -> false
        else -> sistemKaranlikMi
    }
    val kurulumPaleti = remember(isDarkMode) {
        if (isDarkMode) {
            KurulumRenkPaleti(KoyuArkaPlan, YaziBeyazKoyu, YaziIkincilKoyu, true)
        } else {
            KurulumRenkPaleti(AcikArkaPlan, YaziKoyuAcik, YaziIkincilAcik, false)
        }
    }
    var kaydedilenIl by remember { mutableStateOf(prefs.getString("SECILEN_IL", "") ?: "") }
    var kaydedilenIlce by remember { mutableStateOf(prefs.getString("SECILEN_ILCE", "") ?: "") }
    var kaydedilenKodu by remember { mutableStateOf(prefs.getString("SECILEN_ILCE_KODU", "") ?: "") }
    var sehirSecimPenceresiAcikMi by remember { mutableStateOf(false) }
    var vakitOnizleme by remember { mutableStateOf<DiyanetVakitModel?>(null) }
    // --- İZİNLER durumları — her ekrana dönüldüğünde güncel durumu okuyoruz ---
    fun bildirimIzniVarMi(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    fun alarmVarMi(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        } else true
    fun dndVarMi(): Boolean =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
    fun pilOptimizasyonuVarMi(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
    var bildirimIzniVerildiMi by remember { mutableStateOf(bildirimIzniVarMi()) }
    // YENİ ÖZELLİK: Toplam 5 ayrı izin satırı — Bildirim, Üstte Göster,
    // Anımsatmalar (tam zamanlı alarm), Rahatsız Etme (DND erişimi) ve
    // Güç Tasarrufu. Önceden Anımsatmalar+Rahatsız Etme tek satırdaydı,
    // artık ikisi de kendi ayrı satırında.
    fun ustteGosterVarMi(): Boolean = Settings.canDrawOverlays(context)
    var ustteGosterVerildiMi by remember { mutableStateOf(ustteGosterVarMi()) }
    var alarmVerildiMi by remember { mutableStateOf(alarmVarMi()) }
    var dndVerildiMi by remember { mutableStateOf(dndVarMi()) }
    var pilOptimizasyonuVerildiMi by remember { mutableStateOf(pilOptimizasyonuVarMi()) }
    val bildirimIzniLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { bildirimIzniVerildiMi = it }
    val ustteGosterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        ustteGosterVerildiMi = ustteGosterVarMi()
    }
    fun ustteGosterIste() {
        if (ustteGosterVarMi()) {
            ustteGosterVerildiMi = true
            return
        }
        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            ustteGosterLauncher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    val alarmIzniLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        alarmVerildiMi = alarmVarMi()
    }
    fun alarmIzniIste() {
        if (alarmVarMi()) {
            alarmVerildiMi = true
            return
        }
        try {
            alarmIzniLauncher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    val dndIzniLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        dndVerildiMi = dndVarMi()
    }
    fun dndIzniIste() {
        if (dndVarMi()) {
            dndVerildiMi = true
            return
        }
        try {
            dndIzniLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    val pilOptimizasyonuLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        pilOptimizasyonuVerildiMi = pilOptimizasyonuVarMi()
    }
    fun pilOptimizasyonuIste() {
        if (pilOptimizasyonuVarMi()) {
            pilOptimizasyonuVerildiMi = true
            return
        }
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            pilOptimizasyonuLauncher.launch(intent)
        } catch (e: Exception) {
            // DÜZELTME: Bazı cihazlarda (özellikle manifest'te
            // REQUEST_IGNORE_BATTERY_OPTIMIZATIONS izni eksikse ya da
            // üretici arayüzü özel pencereyi engelliyorsa) doğrudan istek
            // sessizce başarısız olabiliyordu — kullanıcı "İzin Ver"e
            // basıyor ama hiçbir şey açılmıyordu. Artık başarısız olursa,
            // kullanıcının uygulamayı kendi bulup manuel olarak
            // ayarlayabileceği genel pil ayarları ekranına yönlendiriyoruz.
            e.printStackTrace()
            try {
                pilOptimizasyonuLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }
    // Şehir kodu belirlendiğinde/güncellendiğinde günün vakitlerini önizleme için çek.
    LaunchedEffect(kaydedilenKodu) {
        if (kaydedilenKodu.isNotBlank()) {
            try {
                val liste = withContext(Dispatchers.IO) {
                    EzanApiService.instance.getVakitler(ilceKodu = kaydedilenKodu)
                }
                // DÜZELTME: Listenin İLK elemanını almak (genelde ayın 1'i)
                // BUGÜNÜN vakitlerini göstermiyordu — HomeScreen.kt'deki AYNI
                // mantıkla, tarihi bugünün tarihiyle eşleştirerek doğru
                // günün kaydını buluyoruz.
                val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
                val bugunStr = dateFormat.format(java.util.Date())
                vakitOnizleme = liste.find { it.miladiTarihKisa == bugunStr } ?: liste.firstOrNull()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    fun konumuCoz() {
        konumBelirleniyorMu = true
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    coroutineScope.launch {
                        try {
                            val geocoder = Geocoder(context, Locale("tr", "TR"))
                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                            val address = addresses?.firstOrNull()
                            val il = address?.adminArea ?: address?.subAdminArea
                            val ilce = address?.subAdminArea ?: address?.locality
                            if (il != null && ilce != null) {
                                val sonuc = JsonYardimcisi.konumdanKoduCoz(context, il, ilce)
                                if (sonuc != null) {
                                    prefs.edit()
                                        .putString("SECILEN_IL", il)
                                        .putString("SECILEN_ILCE", ilce)
                                        .putString("SECILEN_ILCE_KODU", sonuc.ilceKodu)
                                        .putString("SECILEN_SEHIR_KODU", sonuc.sehirKodu)
                                        .apply()
                                    kaydedilenIl = il
                                    kaydedilenIlce = ilce
                                    kaydedilenKodu = sonuc.ilceKodu
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            konumBelirleniyorMu = false
                            adim = KurulumAdimi.LOKASYON
                        }
                    }
                } else {
                    konumBelirleniyorMu = false
                    adim = KurulumAdimi.LOKASYON
                }
            }.addOnFailureListener {
                konumBelirleniyorMu = false
                adim = KurulumAdimi.LOKASYON
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            konumBelirleniyorMu = false
            adim = KurulumAdimi.LOKASYON
        }
    }
    val konumIzinLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { izinVerildiMi ->
        if (izinVerildiMi) {
            konumuCoz()
        } else {
            konumBelirleniyorMu = false
            adim = KurulumAdimi.LOKASYON
        }
    }
    // YENİ ÖZELLİK: Tüm kuruluş, hesaplanan palet üzerinden CANLI olarak
    // koyu/açık arasında geçiş yapabiliyor.
    CompositionLocalProvider(LocalKurulumRenkleri provides kurulumPaleti) {
        Box(modifier = Modifier.fillMaxSize().background(kurulumPaleti.arkaPlan)) {
            Image(
                painter = painterResource(id = R.drawable.mevlana_bg),
                contentDescription = "Arka Plan",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.35f)
            )
            Box(modifier = Modifier.fillMaxSize().background(kurulumPaleti.arkaPlan.copy(alpha = 0.82f)))
            when (adim) {
                KurulumAdimi.HOSGELDIN -> HosgeldinEkrani(
                    konumBelirleniyorMu = konumBelirleniyorMu,
                    onDevamEtTiklandi = { konumIzinLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                    onManuelSecTiklandi = { adim = KurulumAdimi.LOKASYON },
                    onGizlilikTiklandi = { gizlilikPolitikasiAcikMi = true },
                    onKullanimTiklandi = { kullanimKosullariAcikMi = true }
                )
                KurulumAdimi.LOKASYON -> LokasyonEkrani(
                    gosterilenSehirMetni = if (kaydedilenIl.isNotBlank()) "$kaydedilenIlce, $kaydedilenIl, Türkiye" else "Şehrinizi seçin",
                    vakitOnizleme = vakitOnizleme,
                    prefs = prefs,
                    konumBelirliMi = kaydedilenKodu.isNotBlank(),
                    seciliRenkTemasi = seciliRenkTemasi,
                    onRenkTemasiDegis = { seciliRenkTemasi = it },
                    temaSecimi = temaSecimi,
                    onTemaSecimiDegis = { temaSecimi = it },
                    onGeriTiklandi = { adim = KurulumAdimi.HOSGELDIN },
                    onSehirSatiriTiklandi = { sehirSecimPenceresiAcikMi = true },
                    onOnaylaTiklandi = { adim = KurulumAdimi.IZINLER }
                )
                KurulumAdimi.IZINLER -> IzinlerEkrani(
                    vurgu = seciliRenkTemasi.renk,
                    bildirimIzniVerildiMi = bildirimIzniVerildiMi,
                    ustteGosterVerildiMi = ustteGosterVerildiMi,
                    alarmVerildiMi = alarmVerildiMi,
                    dndVerildiMi = dndVerildiMi,
                    pilOptimizasyonuVerildiMi = pilOptimizasyonuVerildiMi,
                    onBildirimIzniIste = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            bildirimIzniLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            bildirimIzniVerildiMi = true
                        }
                    },
                    onUstteGosterIste = { ustteGosterIste() },
                    onAlarmIzniIste = { alarmIzniIste() },
                    onDndIzniIste = { dndIzniIste() },
                    onPilOptimizasyonuIste = { pilOptimizasyonuIste() },
                    onTamamlaTiklandi = onDevamTiklandi
                )
            }
            if (sehirSecimPenceresiAcikMi) {
                SehirSecimDialog(
                    onSecildi = { yeniIl, yeniIlce, yeniKod, yeniSehirKodu ->
                        kaydedilenIl = yeniIl
                        kaydedilenIlce = yeniIlce
                        kaydedilenKodu = yeniKod
                        prefs.edit()
                            .putString("SECILEN_IL", yeniIl)
                            .putString("SECILEN_ILCE", yeniIlce)
                            .putString("SECILEN_ILCE_KODU", yeniKod)
                            .putString("SECILEN_SEHIR_KODU", yeniSehirKodu)
                            .apply()
                        sehirSecimPenceresiAcikMi = false
                    },
                    onKapat = { sehirSecimPenceresiAcikMi = false },
                    vurguRengi = VarsayilanVurgu
                )
            }
        }
    } // CompositionLocalProvider kapanışı
    if (kullanimKosullariAcikMi) {
        AlertDialog(onDismissRequest = { kullanimKosullariAcikMi = false }, containerColor = Color(0xFF1E293B), title = { Text("Kullanım Koşulları", color = Color.White) }, text = { Column(modifier = Modifier.fillMaxWidth().height(400.dp).verticalScroll(rememberScrollState())) { Text(sKullanimSartlariMetni, color = Color(0xFFCBD5E1), fontSize = 12.sp) } }, confirmButton = { Button(onClick = { kullanimKosullariAcikMi = false }, colors = ButtonDefaults.buttonColors(containerColor = VarsayilanVurgu)) { Text("Kapat", color = Color.White) } })
    }
    if (gizlilikPolitikasiAcikMi) {
        AlertDialog(onDismissRequest = { gizlilikPolitikasiAcikMi = false }, containerColor = Color(0xFF1E293B), title = { Text("Gizlilik Politikası", color = Color.White) }, text = { Column(modifier = Modifier.fillMaxWidth().height(400.dp).verticalScroll(rememberScrollState())) { Text(sGizlilikPolitikasiMetni, color = Color(0xFFCBD5E1), fontSize = 12.sp) } }, confirmButton = { Button(onClick = { gizlilikPolitikasiAcikMi = false }, colors = ButtonDefaults.buttonColors(containerColor = VarsayilanVurgu)) { Text("Kapat", color = Color.White) } })
    }
}
// ============================================================
// 1. EKRAN — Selamun Aleykum
// ============================================================
@Composable
private fun HosgeldinEkrani(
    konumBelirleniyorMu: Boolean,
    onDevamEtTiklandi: () -> Unit,
    onManuelSecTiklandi: () -> Unit,
    onGizlilikTiklandi: () -> Unit,
    onKullanimTiklandi: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = "Selamün Aleyküm",
            color = LocalKurulumRenkleri.current.yaziBeyaz,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Ezan Vaktim uygulamasına hoş geldiniz",
            color = LocalKurulumRenkleri.current.yaziIkincil,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(36.dp))
        // YENİ: Referans görseldeki dünya+konum ikonu yerine (özel bir
        // görsel varlık gerektirdiği için), aynı fikri veren sade bir
        // "konum" rozeti — yeşil daire içinde konum iğnesi.
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(VarsayilanVurgu.copy(alpha = 0.14f))
                .border(1.dp, VarsayilanVurgu.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(VarsayilanVurgu),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Filled.Place, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = "Bulunduğunuz konuma göre vakitleri otomatik konumla hızlıca bulabilirsiniz.",
            color = LocalKurulumRenkleri.current.yaziIkincil,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(36.dp))
        Button(
            onClick = onDevamEtTiklandi,
            enabled = !konumBelirleniyorMu,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VarsayilanVurgu, disabledContainerColor = VarsayilanVurgu.copy(alpha = 0.5f))
        ) {
            if (konumBelirleniyorMu) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "Konum belirleniyor...", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            } else {
                Text(text = "Devam Et", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        TextButton(onClick = onManuelSecTiklandi) {
            Text(text = "Konumu Manuel Seç", color = LocalKurulumRenkleri.current.yaziIkincil, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(24.dp))
        val altYaziRengi = Color(0xFF64748B)
        val altMetin = buildAnnotatedString {
            withStyle(SpanStyle(color = altYaziRengi, fontSize = 11.sp)) {
                append("Devam ederek ")
            }
            pushStringAnnotation(tag = "GIZLILIK", annotation = "gizlilik")
            withStyle(SpanStyle(color = VarsayilanVurgu, fontSize = 11.sp, fontWeight = FontWeight.Bold)) {
                append("gizlilik politikasını")
            }
            pop()
            withStyle(SpanStyle(color = altYaziRengi, fontSize = 11.sp)) {
                append(" ve ")
            }
            pushStringAnnotation(tag = "KULLANIM", annotation = "kullanim")
            withStyle(SpanStyle(color = VarsayilanVurgu, fontSize = 11.sp, fontWeight = FontWeight.Bold)) {
                append("kullanım şartnamesini")
            }
            pop()
            withStyle(SpanStyle(color = altYaziRengi, fontSize = 11.sp)) {
                append(" kabul edersiniz.")
            }
        }
        ClickableText(
            text = altMetin,
            style = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) { ofset ->
            altMetin.getStringAnnotations(tag = "GIZLILIK", start = ofset, end = ofset).firstOrNull()?.let { onGizlilikTiklandi() }
            altMetin.getStringAnnotations(tag = "KULLANIM", start = ofset, end = ofset).firstOrNull()?.let { onKullanimTiklandi() }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}
// ============================================================
// 2. EKRAN — Lokasyonunu Seç (+ bildirim/tema ayarları)
// ============================================================
@Composable
private fun LokasyonEkrani(
    gosterilenSehirMetni: String,
    vakitOnizleme: DiyanetVakitModel?,
    prefs: android.content.SharedPreferences,
    konumBelirliMi: Boolean,
    seciliRenkTemasi: RenkTemasi,
    onRenkTemasiDegis: (RenkTemasi) -> Unit,
    temaSecimi: String,
    onTemaSecimiDegis: (String) -> Unit,
    onGeriTiklandi: () -> Unit,
    onSehirSatiriTiklandi: () -> Unit,
    onOnaylaTiklandi: () -> Unit
) {
    // DÜZELTME: Bu anahtarlar YENİ bir özellik değil — SettingsScreen.kt'de zaten
    // var olan aynı SharedPreferences anahtarlarını kullanıyor. Burada
    // değiştirilen bir ayar, Ayarlar ekranında da aynen görünecek.
    var imsakOnce by remember { mutableStateOf(prefs.getBoolean("IMSAK_AKTIF", true)) }
    var sabahVaktinde by remember { mutableStateOf(prefs.getBoolean("SABAH_VAKTINDE", true)) }
    var sabahOnce by remember { mutableStateOf(prefs.getBoolean("SABAH_AKTIF", true)) }
    var gunesOnce by remember { mutableStateOf(prefs.getBoolean("GUNES_AKTIF", true)) }
    var ogleVaktinde by remember { mutableStateOf(prefs.getBoolean("OGLE_VAKTINDE", true)) }
    var ogleOnce by remember { mutableStateOf(prefs.getBoolean("OGLE_AKTIF", true)) }
    var ikindiVaktinde by remember { mutableStateOf(prefs.getBoolean("IKINDI_VAKTINDE", true)) }
    var ikindiOnce by remember { mutableStateOf(prefs.getBoolean("IKINDI_AKTIF", true)) }
    var aksamVaktinde by remember { mutableStateOf(prefs.getBoolean("AKSAM_VAKTINDE", true)) }
    var aksamOnce by remember { mutableStateOf(prefs.getBoolean("AKSAM_AKTIF", true)) }
    var yatsiVaktinde by remember { mutableStateOf(prefs.getBoolean("YATSI_VAKTINDE", true)) }
    var yatsiOnce by remember { mutableStateOf(prefs.getBoolean("YATSI_AKTIF", true)) }
    // YENİ ÖZELLİK: İstek üzerine eklenen ek ayarlar — SettingsScreen.kt'deki
    // AYNI anahtarları VE AYNI varsayılan değerleri kullanıyor.
    var cumaSessiz by remember { mutableStateOf(prefs.getBoolean("CUMA_SESSIZ", true)) }
    var namazVaktindeSessiz by remember { mutableStateOf(prefs.getBoolean("NAMAZ_VAKTINDE_SESSIZ", true)) }
    var imsaktaSabahOku by remember { mutableStateOf(prefs.getBoolean("SABAH_IMSAKTE_OKU", false)) }
    var bildirimCubuguAktif by remember { mutableStateOf(prefs.getBoolean("BILDIRIM_CUBUGU_AKTIF", true)) }
    // YENİ ÖZELLİK: Titreşim de kuruluma eklendi — SettingsScreen.kt'deki
    // AYNI anahtarı (TITRESIM_DURUMU) kullanıyor. Ayarlar ekranında bu
    // anahtarın varsayılanı KAPALI, ama kuruluşta AÇIK gelmesi istendiği
    // için: anahtar daha önce hiç kaydedilmemişse (ilk kurulum), burada
    // hemen "açık" olarak KAYDEDİYORUZ — kullanıcı anahtara hiç dokunmasa
    // bile, sonradan Ayarlar ekranına gidince yine açık görünsün diye.
    var titresimAktif by remember {
        mutableStateOf(
            if (prefs.contains("TITRESIM_DURUMU")) {
                prefs.getBoolean("TITRESIM_DURUMU", true)
            } else {
                prefs.edit().putBoolean("TITRESIM_DURUMU", true).apply()
                true
            }
        )
    }
    val context = LocalContext.current
    // DÜZELTME: Artık yeşil çerçeve yerine, uygulamanın kendi Ayarlar
    // ekranındaki kart stiliyle (dolu, hafif saydam arka plan) aynı; ve
    // vurgu rengi artık sabit yeşil değil, kullanıcının SEÇTİĞİ tema
    // rengine göre CANLI değişiyor — hangi rengi seçtiğini kurulum
    // ekranında anında görebiliyor.
    val vurgu = seciliRenkTemasi.renk
    // DÜZELTME: Kart arka planı artık her zaman KOYU formülü kullanmıyordu
    // (sabit "true") — Gündüz seçilince kartlar da açık renge dönsün diye
    // gerçek isDarkMode değeri kullanılıyor.
    val isDarkMode = LocalKurulumRenkleri.current.isDarkMode
    val kartArkaPlani = TemaYardimcisi.kartArkaPlaniniHesapla(vurgu, isDarkMode).copy(alpha = 0.75f)
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onGeriTiklandi) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Geri", tint = vurgu)
            }
            Text(
                text = "Lokasyonunu Seç",
                color = vurgu,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // --- Konum ---
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.LocationOn, contentDescription = null, tint = LocalKurulumRenkleri.current.yaziBeyaz, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Konum", color = LocalKurulumRenkleri.current.yaziBeyaz, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(kartArkaPlani)
                        .clickable { onSehirSatiriTiklandi() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = gosterilenSehirMetni, color = LocalKurulumRenkleri.current.yaziBeyaz, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = "Değiştir", tint = vurgu)
                }
            }
            // --- Hesaplama Yöntemi ---
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.DateRange, contentDescription = null, tint = LocalKurulumRenkleri.current.yaziBeyaz, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Hesaplama Yöntemi", color = LocalKurulumRenkleri.current.yaziBeyaz, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(kartArkaPlani)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Diyanet İşleri Başkanlığı", color = LocalKurulumRenkleri.current.yaziBeyaz, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                if (vakitOnizleme != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        VakitOnizlemeSaati("İmsak", vakitOnizleme.imsak?.take(5) ?: "--:--")
                        VakitOnizlemeSaati("Güneş", vakitOnizleme.gunes?.take(5) ?: "--:--")
                        VakitOnizlemeSaati("Öğle", vakitOnizleme.ogle?.take(5) ?: "--:--")
                        VakitOnizlemeSaati("İkindi", vakitOnizleme.ikindi?.take(5) ?: "--:--")
                        VakitOnizlemeSaati("Akşam", vakitOnizleme.aksam?.take(5) ?: "--:--")
                        VakitOnizlemeSaati("Yatsı", vakitOnizleme.yatsi?.take(5) ?: "--:--")
                    }
                }
            }
            // --- Bildirim Ayarları ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(kartArkaPlani)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(imageVector = Icons.Filled.Notifications, contentDescription = null, tint = LocalKurulumRenkleri.current.yaziBeyaz, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Bildirim Ayarları", color = LocalKurulumRenkleri.current.yaziBeyaz, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(text = "Vaktinde", color = vurgu, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
                    Text(text = "Vakitten\nÖnce", color = vurgu, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
                }
                KurulumBildirimSatiri("İmsak", null, null, imsakOnce, { imsakOnce = it; prefs.edit().putBoolean("IMSAK_AKTIF", it).apply() }, vurgu)
                KurulumBildirimSatiri("Sabah", sabahVaktinde, { sabahVaktinde = it; prefs.edit().putBoolean("SABAH_VAKTINDE", it).apply() }, sabahOnce, { sabahOnce = it; prefs.edit().putBoolean("SABAH_AKTIF", it).apply() }, vurgu)
                KurulumBildirimSatiri("Güneş", null, null, gunesOnce, { gunesOnce = it; prefs.edit().putBoolean("GUNES_AKTIF", it).apply() }, vurgu)
                KurulumBildirimSatiri("Öğle", ogleVaktinde, { ogleVaktinde = it; prefs.edit().putBoolean("OGLE_VAKTINDE", it).apply() }, ogleOnce, { ogleOnce = it; prefs.edit().putBoolean("OGLE_AKTIF", it).apply() }, vurgu)
                KurulumBildirimSatiri("İkindi", ikindiVaktinde, { ikindiVaktinde = it; prefs.edit().putBoolean("IKINDI_VAKTINDE", it).apply() }, ikindiOnce, { ikindiOnce = it; prefs.edit().putBoolean("IKINDI_AKTIF", it).apply() }, vurgu)
                KurulumBildirimSatiri("Akşam", aksamVaktinde, { aksamVaktinde = it; prefs.edit().putBoolean("AKSAM_VAKTINDE", it).apply() }, aksamOnce, { aksamOnce = it; prefs.edit().putBoolean("AKSAM_AKTIF", it).apply() }, vurgu)
                KurulumBildirimSatiri("Yatsı", yatsiVaktinde, { yatsiVaktinde = it; prefs.edit().putBoolean("YATSI_VAKTINDE", it).apply() }, yatsiOnce, { yatsiOnce = it; prefs.edit().putBoolean("YATSI_AKTIF", it).apply() }, vurgu)
            }
            // --- YENİ ÖZELLİK: Diğer ayarlar — SettingsScreen.kt'deki AYNI
            // başlıklar, AYNI anahtarlar, AYNI varsayılan değerler. ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(kartArkaPlani)
                    .padding(16.dp)
            ) {
                Text(text = "Diğer Ayarlar", color = LocalKurulumRenkleri.current.yaziBeyaz, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                DigerAyarSatiri("Cuma Vaktinde Otomatik Sessiz", cumaSessiz, vurgu) { cumaSessiz = it; prefs.edit().putBoolean("CUMA_SESSIZ", it).apply() }
                DigerAyarSatiri("Namaz Vakitlerinde Telefonu Sessize Al", namazVaktindeSessiz, vurgu) { namazVaktindeSessiz = it; prefs.edit().putBoolean("NAMAZ_VAKTINDE_SESSIZ", it).apply() }
                DigerAyarSatiri("Sabah Ezanını İmsak Vaktinde Oku", imsaktaSabahOku, vurgu) { imsaktaSabahOku = it; prefs.edit().putBoolean("SABAH_IMSAKTE_OKU", it).apply() }
                DigerAyarSatiri("Bildirim Çubuğunda Vakitleri Göster", bildirimCubuguAktif, vurgu) { bildirimCubuguAktif = it; prefs.edit().putBoolean("BILDIRIM_CUBUGU_AKTIF", it).apply() }
                DigerAyarSatiri("Titreşim", titresimAktif, vurgu) { titresimAktif = it; prefs.edit().putBoolean("TITRESIM_DURUMU", it).apply() }
            }
            // --- YENİ ÖZELLİK: Görünüm Modu — SettingsScreen.kt'deki AYNI
            // anahtar (TEMA_SECIMI) ve AYNI 3 seçenek (Sistem/Gece/Gündüz). ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(kartArkaPlani)
                    .padding(16.dp)
            ) {
                Text(text = "Görünüm Modu", color = LocalKurulumRenkleri.current.yaziBeyaz, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GorunumModuSecenegi("Sistem", temaSecimi == "Sistem", vurgu, Modifier.weight(1f)) {
                        onTemaSecimiDegis("Sistem")
                        prefs.edit().putString("TEMA_SECIMI", "Sistem").apply()
                    }
                    GorunumModuSecenegi("Gece", temaSecimi == "Gece", vurgu, Modifier.weight(1f)) {
                        onTemaSecimiDegis("Gece")
                        prefs.edit().putString("TEMA_SECIMI", "Gece").apply()
                    }
                    GorunumModuSecenegi("Gündüz", temaSecimi == "Gündüz", vurgu, Modifier.weight(1f)) {
                        onTemaSecimiDegis("Gündüz")
                        prefs.edit().putString("TEMA_SECIMI", "Gündüz").apply()
                    }
                }
            }
            // --- Renk Teması ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(kartArkaPlani)
                    .padding(16.dp)
            ) {
                Text(text = "Renk Teması", color = LocalKurulumRenkleri.current.yaziBeyaz, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    RenkTemasi.entries.forEach { tema ->
                        RenkSecenegi(
                            renk = tema.renk,
                            seciliMi = seciliRenkTemasi == tema,
                            cerceveRengi = LocalKurulumRenkleri.current.yaziBeyaz,
                            onClick = {
                                onRenkTemasiDegis(tema)
                                TemaYardimcisi.temayiKaydet(context, tema)
                            }
                        )
                    }
                }
            }
            if (!konumBelirliMi) {
                Text(
                    text = "Devam etmeden önce lütfen konumunuzu otomatik tespit ettirin veya şehrinizi elle seçin.",
                    color = Color(0xFFF97316),
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = onOnaylaTiklandi,
                enabled = konumBelirliMi,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = vurgu, disabledContainerColor = vurgu.copy(alpha = 0.35f))
            ) {
                Text(text = "Onayla", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
// YENİ ÖZELLİK: Görünüm Modu seçim butonu — SettingsScreen.kt'deki
// SecenekButon ile aynı davranış, kuruluşun koyu temasına uyarlanmış.
@Composable
private fun GorunumModuSecenegi(baslik: String, seciliMi: Boolean, vurgu: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (seciliMi) vurgu.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (seciliMi) vurgu else LocalKurulumRenkleri.current.yaziIkincil.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = baslik, color = if (seciliMi) vurgu else LocalKurulumRenkleri.current.yaziIkincil, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DigerAyarSatiri(baslik: String, durum: Boolean, vurgu: Color, onDegis: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = baslik, color = LocalKurulumRenkleri.current.yaziIkincil, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = durum,
            onCheckedChange = onDegis,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = vurgu)
        )
    }
}
@Composable
private fun VakitOnizlemeSaati(isim: String, saat: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = isim, color = LocalKurulumRenkleri.current.yaziIkincil, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = saat, color = LocalKurulumRenkleri.current.yaziBeyaz, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
@Composable
private fun KurulumBildirimSatiri(
    vakitAdi: String,
    vaktindeDurum: Boolean?,
    vaktindeDegis: ((Boolean) -> Unit)?,
    onceDurum: Boolean,
    onceDegis: (Boolean) -> Unit,
    vurgu: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = vakitAdi, color = LocalKurulumRenkleri.current.yaziIkincil, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                if (vaktindeDurum != null && vaktindeDegis != null) {
                    Switch(checked = vaktindeDurum, onCheckedChange = vaktindeDegis, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = vurgu))
                }
            }
            Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                Switch(checked = onceDurum, onCheckedChange = onceDegis, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = vurgu))
            }
        }
    }
}
// ============================================================
// 3. EKRAN — Bildirimleri Kaçırma! (izinler)
// ============================================================
@Composable
private fun IzinlerEkrani(
    vurgu: Color,
    bildirimIzniVerildiMi: Boolean,
    ustteGosterVerildiMi: Boolean,
    alarmVerildiMi: Boolean,
    dndVerildiMi: Boolean,
    pilOptimizasyonuVerildiMi: Boolean,
    onBildirimIzniIste: () -> Unit,
    onUstteGosterIste: () -> Unit,
    onAlarmIzniIste: () -> Unit,
    onDndIzniIste: () -> Unit,
    onPilOptimizasyonuIste: () -> Unit,
    onTamamlaTiklandi: () -> Unit
) {
    val hepsiTamam = bildirimIzniVerildiMi && ustteGosterVerildiMi && alarmVerildiMi && dndVerildiMi && pilOptimizasyonuVerildiMi
    // YENİ ÖZELLİK: "İzin Ver"e basınca doğrudan sisteme gitmek yerine,
    // önce kullanıcıya sistem ekranında ne yapması gerektiğini gösteren
    // bir yardım penceresi açılıyor. Pencere "Tamam" ile kapanınca asıl
    // sistem izin ekranı açılıyor.
    var acikYardim by remember { mutableStateOf<IzinYardimIcerigi?>(null) }
    var acikYardimAksiyonu by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun yardimAc(icerik: IzinYardimIcerigi, aksiyon: () -> Unit) {
        acikYardim = icerik
        acikYardimAksiyonu = aksiyon
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(vurgu.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Filled.Notifications, contentDescription = null, tint = vurgu, modifier = Modifier.size(38.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Bildirimleri Kaçırma!",
            color = LocalKurulumRenkleri.current.yaziBeyaz,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Uygulamayı en iyi şekilde kullanabilmek için aşağıdaki izinleri vermelisiniz.",
            color = LocalKurulumRenkleri.current.yaziIkincil,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(28.dp))
        IzinSatiri(
            baslik = "Bildirim",
            aciklama = "Vakitleri bildirim ekranından takip edebilmeniz için gereklidir.",
            verildiMi = bildirimIzniVerildiMi,
            vurgu = vurgu,
            onTiklandi = onBildirimIzniIste
        )
        Spacer(modifier = Modifier.height(16.dp))
        IzinSatiri(
            baslik = "Üstte Göster",
            aciklama = "Uygulamanın diğer uygulamaların üzerinde ekranınıza uyarı gösterebilmesi için gerekli.",
            verildiMi = ustteGosterVerildiMi,
            vurgu = vurgu,
            // YENİ ÖZELLİK: Doğrudan sisteme gitmeden önce yardım penceresi gösteriliyor.
            onTiklandi = { yardimAc(UstteGosterYardimi) { onUstteGosterIste() } }
        )
        Spacer(modifier = Modifier.height(16.dp))
        // YENİ ÖZELLİK: Anımsatmalar (tam zamanlı alarm) ve Rahatsız Etme
        // (DND erişimi) artık AYRI iki satır — toplam 5 izin satırı.
        IzinSatiri(
            baslik = "Alarmlar ve Anımsatmalar",
            aciklama = "Vakit geldiğinde belirlediğiniz sesin tam zamanında çalabilmesi için gerekli.",
            verildiMi = alarmVerildiMi,
            vurgu = vurgu,
            onTiklandi = { yardimAc(AlarmYardimi) { onAlarmIzniIste() } }
        )
        Spacer(modifier = Modifier.height(16.dp))
        IzinSatiri(
            baslik = "Rahatsız Etme",
            aciklama = "Namaz vakitlerinde telefonun otomatik sessize alınabilmesi için gerekli.",
            verildiMi = dndVerildiMi,
            vurgu = vurgu,
            onTiklandi = { yardimAc(DndYardimi) { onDndIzniIste() } }
        )
        Spacer(modifier = Modifier.height(16.dp))
        IzinSatiri(
            baslik = "Güç Tasarrufu",
            aciklama = "Uygulamanın arka planda sorunsuz çalışabilmesi için gereklidir.",
            verildiMi = pilOptimizasyonuVerildiMi,
            vurgu = vurgu,
            onTiklandi = onPilOptimizasyonuIste
        )
        Spacer(modifier = Modifier.height(32.dp))
        // DÜZELTME: Dördü de onaylanmadan "Tamamla" pasif kalıyor —
        // kullanıcı izin vermeden ana menüye geçemiyor.
        if (!hepsiTamam) {
            Text(
                text = "Devam etmeden önce yukarıdaki izinlerin hepsini vermeniz gerekiyor.",
                color = Color(0xFFF97316),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
        Button(
            onClick = onTamamlaTiklandi,
            enabled = hepsiTamam,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = vurgu, disabledContainerColor = vurgu.copy(alpha = 0.35f))
        ) {
            Text(text = "Tamamla", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
    // YENİ ÖZELLİK: Yardım penceresi — açıksa gösterilir, "Tamam"a
    // basılınca kapanır ve asıl sistem izin ekranı tetiklenir.
    acikYardim?.let { icerik ->
        IzinYardimDialog(
            icerik = icerik,
            vurgu = vurgu,
            onTamamTiklandi = {
                acikYardimAksiyonu?.invoke()
                acikYardim = null
                acikYardimAksiyonu = null
            }
        )
    }
}
// YENİ ÖZELLİK: "İzin Ver"e basınca açılan, kullanıcıya sistem ayarları
// ekranında ne yapması gerektiğini gösteren yardım penceresi. Yeşil
// vurgulu öğeler (uygulama ikon kutusu ve switch) artık sabit renk değil,
// kuruluşta seçilen tema rengini (vurgu) kullanıyor.
@Composable
private fun IzinYardimDialog(
    icerik: IzinYardimIcerigi,
    vurgu: Color,
    onTamamTiklandi: () -> Unit
) {
    Dialog(onDismissRequest = onTamamTiklandi) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
        ) {
            // --- Sahte sistem ayarları kartı ---
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF1F5F9))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Color(0xFF334155),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = icerik.sistemBasligi,
                        color = Color(0xFF334155),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = Color(0xFF334155),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                // Uygulama satırı — DÜZELTME: switch ve ikon arka planı
                // artık sabit yeşil değil, kuruluşta seçilen tema rengi
                // (vurgu) ile eşleşiyor.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            // DÜZELTME: Logo arka planı artık seçili tema
                            // rengine göre değişmiyor — sabit yeşil.
                            .background(Color(0xff068d26)),
                        contentAlignment = Alignment.Center
                    ) {
                        // DÜZELTME: R.mipmap.ic_launcher bir "uyarlanabilir simge"
                        // (adaptive icon) — Compose'un painterResource()'u bunu
                        // yükleyemeyip ÇÖKÜYORDU (uygulama komple kapanıyordu).
                        // Splash ekranında güvenle kullanılan gerçek drawable'a
                        // (R.drawable.cami) geçildi.
                        Image(
                            painter = painterResource(id = R.drawable.cami),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Ezan Vaktim",
                        color = Color(0xFF0F172A),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    // Sahte "açık" switch — vurgu rengiyle
                    Box(
                        modifier = Modifier
                            .width(46.dp)
                            .height(26.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(vurgu),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(3.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = icerik.aciklamaMetni,
                color = Color(0xFF0F172A),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Divider(color = Color(0xFFE2E8F0))
            TextButton(
                onClick = onTamamTiklandi,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(0.dp)
            ) {
                Text(text = "Tamam", color = vurgu, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
@Composable
private fun IzinSatiri(baslik: String, aciklama: String, verildiMi: Boolean, vurgu: Color, istegeBagliMi: Boolean = false, onTiklandi: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = baslik, color = LocalKurulumRenkleri.current.yaziBeyaz, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = aciklama, color = LocalKurulumRenkleri.current.yaziIkincil, fontSize = 12.sp, lineHeight = 16.sp)
                // YENİ ÖZELLİK: Zorunlu olmayan izinler için "İsteğe Bağlı"
                // etiketi — kullanıcı vermeden de kuruluma devam edebilir.
                if (istegeBagliMi) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "İsteğe Bağlı", color = vurgu, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (verildiMi) vurgu else Color(0xFF1E3A5F))
                    .clickable(enabled = !verildiMi) { onTiklandi() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (verildiMi) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Verildi", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(text = "İzin Ver", color = Color(0xFF7DD3FC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Divider(color = vurgu.copy(alpha = 0.25f))
    }
}
val sKullanimSartlariMetni = """
Şartlar ve Koşullar

Bu hüküm ve koşullar, Yasin Çolak tarafından işletilen ilgili tüm hizmetlerle birlikte mobil cihazlar için Ezan Vaktim uygulaması için geçerlidir (topluca "Uygulama"). Yasin Çolak burada "Hizmet Sağlayıcı" olarak anılmaktadır.
Uygulamayı indirerek veya kullanarak bu Şartlar ve Koşulları kabul etmiş olursunuz. Uygulamayı kullanmadan önce bunları dikkatlice okumalısınız.

Uygulamayı Kullanma Lisansı

Bu Şartlara uymanız koşuluyla, Hizmet Sağlayıcı size Uygulamayı kişisel veya şirket içi iş amaçları için bir mobil cihaza yükleme ve kullanma konusunda sınırlı, münhasır olmayan, devredilemez ve geri alınabilir bir lisans vermektedir. Uygulamayı, yürürlükteki yasa tarafından açıkça izin verilmediği sürece ve yalnızca bu kapsamda çoğaltamaz, dağıtamaz, değiştiremez, türev çalışmalar oluşturamaz, tersine mühendislik yapamaz, derleyemez veya parçalarına ayıramazsınız.

Fikri Mülkiyet

Hizmet Sağlayıcı, Uygulama üzerindeki tüm fikri mülkiyet haklarını, kodunu, tasarımını, ticari markalarını, hizmet markalarını, ticari adlarını, logolarını ve marka kimliğini ("Fikri Mülkiyet") saklı tutar. Bu Şartlarda yer alan hiçbir şey, size Hizmet Sağlayıcının ticari markalarını, logolarını veya marka kimliğini herhangi bir amaçla kullanma lisansı veya hakkı vermez. Uygulamada görüntülenen telif hakkı, ticari marka veya diğer tescilli bildirimleri kaldırmayacağınızı, değiştirmeyeceğinizi veya gizlemeyeceğinizi kabul edersiniz.

Fesih

Hizmet Sağlayıcı, bu Şartları esaslı bir şekilde ihlal etmeniz durumunda Uygulamaya veya hizmetlere erişiminizi askıya alabilir. Hizmet Sağlayıcı, ihlale ilişkin yazılı bildirimde bulunacak ve ihlal giderilebilir nitelikteyse, ihlali gidermek için bildirimi aldıktan sonra 14 gününüz olacaktır. Bu süre içinde ihlali gidermezseniz, Hizmet Sağlayıcı erişiminizi sonlandırabilir.

Hizmet Sağlayıcı, geçerli yasaları ihlal etmeniz, fikri mülkiyet haklarını çiğnemeniz veya diğer kullanıcılara veya Hizmet Sağlayıcıya zarar verebilecek bir faaliyette bulunmanız durumunda, size önceden haber vermeksizin erişiminizi derhal askıya alabilir veya sonlandırabilir.
Sözleşmenin feshedilmesiyle birlikte, Uygulamayı kullanma hakkınız sona erecek ve cihazlarınızdaki tüm kopyaları silmeniz gerekecektir.

Bu Uygulamaya erişerek ve kullanarak, bulunduğunuz yargı bölgesinde bunu yasal olarak kullanma izninizin olduğunu beyan edersiniz. Uygulamayı kullanmak için en az 13 yaşında (bulunduğunuz yargı bölgesindeki dijital rıza yaşı) olmalısınız. 13 yaşın altındaysanız, bir ebeveyn veya yasal vasi bu Şartları sizin adınıza incelemeli ve kabul etmelidir.

Uygulamanın, Uygulamanın herhangi bir parçasının veya Hizmet Sağlayıcının ticari markalarının yetkisiz kopyalanması veya değiştirilmesi kesinlikle yasaktır. Uygulamanın kaynak kodunu çıkarmaya, Uygulamayı başka dillere çevirmeye veya türev sürümler oluşturmaya yönelik her türlü girişim yasaktır. Uygulamayla ilgili tüm ticari markalar, telif hakları, veri tabanı hakları ve diğer fikri mülkiyet hakları Hizmet Sağlayıcının mülkiyetinde kalır.

Kullanıcı Tarafından Oluşturulan İçerik ve Kabul Edilebilir Kullanım

Bu Uygulama kullanıcıların içerik yayınlamasına, paylaşmasına veya yüklemesine izin veriyorsa, aşağıdaki türde içerik yayınlamayacağınızı kabul edersiniz:
Yasadışı olan veya üçüncü şahısların fikri mülkiyet haklarını (telif hakkı, marka, patent) ihlal eden içerikler.

Hakaret içeren, tehdit edici, taciz edici, iftira niteliğinde veya nefret söylemi barındıran içerikler.

Ayrımcılık, şiddet veya yasadışı faaliyete teşvik eden içerikler.

Spam, kimlik avı veya kötü amaçlı yazılım barındıran içerikler.

Başkalarının gizlilik veya kişisel veri haklarını ihlal eden içerikler.

Yanlış, yanıltıcı veya aldatıcı olan içerikler.
Açıkça şiddet veya cinsel içerik barındıran içerikler (uygun şekilde yaş sınırlaması getirilmediği sürece).
Hizmet Sağlayıcı aşağıdaki hakları saklı tutar:
Bu yönergeleri ihlal eden içeriğe erişimi kaldırmak veya devre dışı bırakmak.

Bu kuralları tekrar tekrar ihlal eden kullanıcıların hesaplarını askıya almak veya sonlandırmak.
Yasadışı içerik bildirildiğinde kolluk kuvvetleriyle işbirliği yapmak.

Bu Şartları, geçerli yasaları veya yukarıda belirtilen yönergeleri ihlal eden içerikleri denetlemek, filtrelemek veya gizlemek.

Uygulama üzerinden gönderilen içerik, uygulamanın işleyiş şekline bağlı olarak diğer kullanıcılara veya kamuoyuna açık olabilir.

İçeriğin bu Şartları ihlal ettiğine, haklarınızı çiğnediğine veya yasa dışı olduğuna inanıyorsanız, bunu Hizmet Sağlayıcıya ezanvaktimdestek@gmail.com adresinden bildirebilirsiniz. Bildirim, Hizmet Sağlayıcının içeriği tanımlaması, şikayeti değerlendirmesi ve gerekirse sizinle iletişime geçmesi için yeterli bilgiyi içermelidir.
Uygulama bu tür özellikler sunuyorsa, uygulama arayüzü üzerinden doğrudan içerik bildirebilir, diğer kullanıcıları engelleyebilir veya bildirimleri susturabilirsiniz. Hizmet Sağlayıcı, uygulama içi raporları bu Şartlarda açıklanan aynı standartlara göre inceleyecektir.

Hizmet Sağlayıcı, bildirilen içeriği inceleyebilir, gerektiğinde ek bilgi talep edebilir, içeriği kaldırabilir veya erişimi kısıtlayabilir ve uygun durumlarda sorumlu hesaba karşı işlem yapabilir. Moderasyon kararlarından etkilenen kullanıcılar, daha fazla inceleme talebinde bulunmak için ezanvaktimdestek@gmail.com adresinden Hizmet Sağlayıcı ile iletişime geçebilirler. Hizmet Sağlayıcı, itirazlara makul bir süre içinde yanıt verecek ve geçerli yasalara tabi olarak, onaylanan herhangi bir moderasyon kararının gerekçelerini açıklayacaktır.

Kullanıcı Tarafından Oluşturulan İçeriği göndererek, Hizmet Sağlayıcıya, Uygulama ve Hizmet Sağlayıcının işiyle bağlantılı olarak içeriği kullanma, çoğaltma, dağıtma, türev çalışmalar hazırlama, görüntüleme ve gerçekleştirme konusunda münhasır olmayan, dünya çapında, telifsiz bir lisans vermiş olursunuz. Bu lisans, Hizmet Sağlayıcıya içeriğinizi Uygulamadan bağımsız olarak üçüncü taraflara satma veya alt lisanslama hakkı vermez. Yayınladığınız içeriğin tüm haklarına sahip olduğunuzu veya bunları kontrol ettiğinizi ve içeriğin kullanımının bu Şartları veya geçerli yasaları ihlal etmediğini beyan ve garanti edersiniz.

İçeriğiniz kişisel veriler içerebilir. Kullanıcı Tarafından Oluşturulan İçerikle ilgili kişisel verilerin işlenmesi Gizlilik Politikası'na tabidir. Başkalarının kişisel verilerini onların izni olmadan paylaşmayın.

Hizmet Sağlayıcı, Uygulamanın mümkün olduğunca faydalı ve verimli olmasını sağlamaya kendini adamıştır. Bu nedenle, Uygulamayı değiştirme veya hizmetleri için ücret talep etme hakkını herhangi bir zamanda ve herhangi bir nedenle saklı tutar. Hizmet Sağlayıcı, Uygulama veya hizmetleri için herhangi bir ücretin size açıkça bildirileceğini garanti eder.

Uygulama, hizmeti sağlamak amacıyla Hizmet Sağlayıcıya verdiğiniz kişisel verileri saklar ve işler. Mobil cihazınızın ve uygulamaya erişiminizin güvenliğini sağlamak sizin sorumluluğunuzdadır.

Hizmet Sağlayıcı, mobil cihazınızın resmi işletim sistemi tarafından uygulanan yazılım kısıtlamalarını ve sınırlamalarını kaldırmayı içeren jailbreak veya root işlemlerini kesinlikle önermez. Bu tür işlemler, mobil cihazınızı kötü amaçlı yazılımlara, virüslere ve zararlı programlara maruz bırakabilir, mobil cihazınızın güvenlik özelliklerini tehlikeye atabilir ve Uygulamanın doğru çalışmamasına veya hiç çalışmamasına neden olabilir.

Üçüncü Taraf Hizmetleri

Google Play Hizmetleri
Lütfen, Hizmet Sağlayıcının belirli hususlardan sorumlu olmadığını unutmayın. Uygulamanın bazı işlevleri, Wi-Fi veya mobil ağ sağlayıcınız tarafından sağlanan aktif bir internet bağlantısı gerektirir. Wi-Fi erişiminin olmaması veya veri kotanızın tükenmesi nedeniyle Uygulamanın tam kapasitede çalışmaması durumunda Hizmet Sağlayıcı sorumlu tutulamaz.

Uygulamayı Wi-Fi alanı dışında kullanıyorsanız, mobil şebeke sağlayıcınızın sözleşme şartlarının geçerli olduğunu lütfen unutmayın. Sonuç olarak, uygulamaya bağlantı sırasında veri kullanımı için mobil sağlayıcınızdan veya diğer üçüncü taraflardan ücretlendirmeler alabilirsiniz. Uygulamayı kullanarak, veri dolaşımını devre dışı bırakmadan uygulamayı kendi bölgeniz (yani bölge veya ülke) dışında kullanmanız durumunda dolaşım veri ücretleri de dahil olmak üzere bu tür tüm ücretlerden sorumlu olduğunuzu kabul edersiniz. Uygulamayı kullandığınız cihazın fatura ödeyicisi siz değilseniz, fatura ödeyicisinden izin aldığınız varsayılır.

Benzer şekilde, Hizmet Sağlayıcı her zaman uygulamanın kullanımınızdan sorumlu tutulamaz. Örneğin, cihazınızın şarjının dolu olduğundan emin olmak sizin sorumluluğunuzdadır. Cihazınızın pili biterse ve Hizmete erişemezseniz, Hizmet Sağlayıcı sorumlu tutulamaz.
Bu Şartlarda yer alan hiçbir şey, yasal olarak hariç tutulamayan geçerli tüketici koruma yasaları kapsamındaki haklarınızı sınırlamaz.

Sorumluluğun Sınırlandırılması

Yasaların izin verdiği en geniş ölçüde, Hizmet Sağlayıcı, kar kaybı, veri kaybı veya iş kesintisi de dahil ancak bunlarla sınırlı olmamak üzere, dolaylı, arızi, özel, sonuçsal veya cezai zararlardan, bu tür zararların olasılığı konusunda bilgilendirilmiş olsa bile, sorumlu tutulamaz.
Ancak, Hizmet Sağlayıcı aşağıdaki hususlarda tam sorumluluğu saklı tutar:
İhmal sonucu ölüm veya kişisel yaralanma
Dolandırıcılık veya hileli yanlış beyan
Yürürlükteki yasa uyarınca hariç tutulamayan veya sınırlandırılamayan diğer herhangi bir yükümlülük
Yasaların izin verdiği en geniş ölçüde, Hizmet Sağlayıcının herhangi bir talep için toplam sorumluluğu, talebin gerçekleştiği tarihten önceki 12 ay içinde Uygulama için Hizmet Sağlayıcıya ödediğiniz tutarı veya ilgili yasa uyarınca ödenmesi gereken minimum tutarı aşamaz; hangisi daha yüksekse o geçerlidir. Uygulama ücretsiz olarak sağlanıyorsa, bu, Hizmet Sağlayıcının sorumluluğunun ilgili yasa tarafından izin verilen minimum tutarla sınırlı olduğu anlamına gelir.

Hizmet Sağlayıcı, bu Uygulama aracılığıyla sağlanan üçüncü taraf bilgilerine tamamen güvenmeniz veya üçüncü taraflarca sağlanan içerikteki yanlışlıklar nedeniyle yaşayacağınız doğrudan veya dolaylı herhangi bir kayıptan sorumlu değildir.

Tazminat

Yasaların izin verdiği en geniş ölçüde, bu Şartları ihlal etmenizden veya Uygulamayı kasıtlı olarak kötüye kullanmanızdan, bu Şartları ihlal ederek gönderdiğiniz Kullanıcı Tarafından Oluşturulan İçerik de dahil olmak üzere, kaynaklanan veya bunlarla doğrudan ilgili her türlü iddia, yükümlülük, zarar, kayıp ve masraftan (makul avukatlık ücretleri de dahil olmak üzere) Hizmet Sağlayıcıyı, iştiraklerini, yetkililerini, yöneticilerini, çalışanlarını ve temsilcilerini tazmin etmeyi ve zararsız tutmayı kabul edersiniz.

Bu tazminat, Hizmet Sağlayıcının kendi ihmalinden, bu Şartların ihlalinden veya geçerli yasanın ihlalinden kaynaklanan talepler için geçerli değildir. Tüketici tazminatının yasa ile sınırlandırıldığı yargı bölgelerinde, bu madde izin verilen azami ölçüde sınırlandırılacaktır.
Hizmet Sağlayıcı, uygulamayı bir noktada güncellemek isteyebilir. Uygulama şu anda işletim sistemi gereksinimlerine (ve uygulamayı kullanıma sunmayı düşündükleri diğer sistemlere) göre kullanılabilir durumdadır; bu gereksinimler değişebilir ve uygulamayı kullanmaya devam etmek istiyorsanız güncellemeleri indirmeniz gerekecektir. Hizmet Sağlayıcı, uygulamayı her zaman sizin için uygun ve/veya cihazınızda yüklü olan belirli işletim sistemi sürümüyle uyumlu olacak şekilde güncelleyeceğini garanti etmez. Sunulan güncellemeleri kabul etmelisiniz; kabul etmemeyi seçerseniz, Hizmet Sağlayıcı önceki sürümleri desteklemeyi bırakabilir ve Uygulama düzgün çalışmayabilir. Hizmet Sağlayıcı ayrıca uygulamayı sağlamayı bırakmak isteyebilir ve size herhangi bir zamanda fesih bildirimi vermeden kullanımını sonlandırabilir. Size aksi bildirilmedikçe, herhangi bir fesih durumunda, (a) bu şartlarda size verilen haklar ve lisanslar sona erecektir; (b) uygulamayı kullanmayı bırakmalı ve (gerekirse) cihazınızdan silmelisiniz.
Uygulanacak Hukuk ve Yargı Yetkisi
Bu Hüküm ve Koşullar, zorunlu tüketici koruma yasalarının aksini öngördüğü durumlar hariç olmak üzere, kanunlar ihtilafı kuralları hariç, Hizmet Sağlayıcının kurulduğu yargı bölgesinin kanunlarına tabidir.
Bu Şartlardan kaynaklanan veya bunlarla ilgili herhangi bir ihtilaf, geçerli yasa uyarınca yetkili mahkemelerde görülecektir. Bu maddede yer alan hiçbir şey, zorunlu yasa uyarınca yetkili bir mahkemede dava açma hakkınızı sınırlamaz.

DSA Uyumluluğu (Dijital Hizmetler Yasası)
Uygulama, Dijital Hizmetler Yasası (AB 2022/2065 Yönetmeliği, "DSA") kapsamında tanımlanan bir aracı hizmet ise, yukarıdaki şartlara ek olarak aşağıdaki hükümler geçerlidir.

İletişim Noktası: Hizmet Sağlayıcı, AB yetkilileri ve hizmet alıcılarıyla doğrudan iletişim için ezanvaktimdestek@gmail.com adresinden ulaşılabilen tek bir iletişim noktası bulundurmaktadır. Hizmet Sağlayıcının Avrupa Birliği dışında yerleşik olması durumunda, Veri Koruma Yasası'nın 13. maddesi uyarınca AB'de yasal bir temsilci atanmıştır.

İçerik Denetimi ve Gerekçe Bildirimi: Hizmet Sağlayıcı, içeriğe erişimi kısıtladığında, hesabı askıya aldığında veya sonlandırdığında ya da Uygulamanın özelliklerinin kullanılabilirliğini başka şekilde sınırladığında, etkilenen kullanıcıya açık ve spesifik bir gerekçe bildirimi sunulacaktır. Bildirimde, kısıtlamanın niteliği, kararın yasal veya sözleşmesel dayanağı ve DSA'nın 17. maddesine uygun olarak mevcut çözüm mekanizmalarına ilişkin bilgiler yer alacaktır.

Bildirim ve İşlem: Kullanıcılar ve üçüncü taraflar, bu Şartlarda belirtilen iletişim bilgileri aracılığıyla yasa dışı olduğu iddia edilen içeriklere ilişkin bildirimlerde bulunabilirler. Hizmet Sağlayıcı, bildirimleri derhal, özenle ve insan incelemesi gerektiren durumlarda otomatik karar verme olmaksızın işleme koyacaktır. Bildirimler elektronik olarak onaylanacak ve karar, DSA'nın 16. maddesine uygun olarak gecikmeksizin iletilecektir.

Mahkeme Dışı Uyuşmazlık Çözümü: İçerik kısıtlama veya hesap askıya alma kararları da dahil olmak üzere içerik denetimi kararlarına ilişkin uyuşmazlıklar, DSA'nın 21. maddesine uygun olarak sertifikalandırılmış bir mahkeme dışı uyuşmazlık çözüm organına sunulabilir. Hizmet Sağlayıcı, bu organlarla iyi niyetle işbirliği yapacaktır. Mahkeme dışı uyuşmazlık çözümünün kullanılması, geçerli yasa uyarınca yargı yoluna başvurma hakkınızı etkilemez.
Şeffaflık Raporlaması: Hizmet Sağlayıcı, DSA'nın 24. maddesine uygun olarak, alınan bildirim sayısı, alınan önlemler ve kullanılan otomatik yöntemler de dahil olmak üzere içerik denetimi faaliyetlerini kapsayan periyodik şeffaflık raporları yayınlamaktadır. Raporlar, ezanvaktimdestek@gmail.com adresinden talep üzerine temin edilebilir.

Bu DSA hükümleri, Uygulamanın DSA kapsamında bir aracı hizmet olarak nitelendirildiği ölçüde geçerlidir ve yürürlükteki tüketici koruma veya veri koruma yasası kapsamındaki hak veya yükümlülüklerin yerini almaz veya bunları sınırlamaz.

Bölünebilirlik

Bu Şartlar ve Koşulların herhangi bir hükmünün yetkili bir mahkeme tarafından geçersiz, yasadışı veya uygulanamaz olduğuna karar verilmesi halinde, söz konusu hüküm geçerli ve uygulanabilir hale getirilmesi için gerekli olan minimum ölçüde değiştirilecek ve bu Şartların geri kalan hükümleri tam olarak yürürlükte kalacaktır.

Tüm Anlaşma

Bu Hüküm ve Koşullar, Gizlilik Politikası ile birlikte, Uygulamayı kullanımınızla ilgili olarak sizinle Hizmet Sağlayıcı arasında yapılan tüm anlaşmayı oluşturur ve önceki tüm anlaşma veya anlayışların yerine geçer.
Bu Hüküm ve Koşullarda Değişiklikler
Hizmet Sağlayıcı, Şartlar ve Koşullarını periyodik olarak güncelleyebilir. Bu nedenle, herhangi bir değişiklik için bu sayfayı düzenli olarak incelemeniz önerilir. Hizmet Sağlayıcı, yeni Şartlar ve Koşulları bu sayfada yayınlayarak sizi değişikliklerden haberdar edecektir.
Bu Hüküm ve Koşulların önceki sürümleri saklanacak ve ezanvaktimdestek@gmail.com adresinden Hizmet Sağlayıcı ile iletişime geçilerek talep üzerine temin edilecektir.

Bu hüküm ve koşullar 22.09.2026 tarihinden itibaren geçerlidir.

Bize Ulaşın

Şartlar ve Koşullar hakkında herhangi bir sorunuz veya öneriniz varsa, lütfen ezanvaktimdestek@gmail.com adresinden Hizmet Sağlayıcı ile iletişime geçmekten çekinmeyin.
""".trimIndent()
val sGizlilikPolitikasiMetni = """
Gizlilik Politikası

Bu gizlilik politikası, Yasin Çolak tarafından işletilen ilgili tüm hizmetlerle birlikte mobil cihazlar için Ezan Vaktim uygulaması için geçerlidir (topluca "Uygulama"). Yasin Çolak burada "Hizmet Sağlayıcı" olarak anılmaktadır.


Bilgi Toplama ve Kullanımı
Uygulama, indirildiğinde ve kullanıldığında bilgi toplar. Bu bilgiler şunları içerebilir:

- Cihazınızın İnternet Protokolü (IP) adresi
- Ziyaret ettiğiniz uygulama sayfaları, ziyaretinizin tarihi ve saati, bu sayfalarda geçirdiğiniz süre
- Uygulamada geçirilen süre
- Kullandığınız mobil işletim sistemi


Çerezler ve İzleme Teknolojileri
Uygulama veya üçüncü taraf SDK'ları, işlevselliği, analizi veya hizmet sunumunu desteklemek için çerezler, SDK'lar, pikseller ve benzer teknolojiler kullanabilir. Geçerli yasa gerektirdiği durumlarda, Hizmet Sağlayıcı, gerekli olmayan izleme teknolojilerini kullanmadan önce onay alacaktır.


Konum Bilgileri
Bu uygulama, konum tabanlı özellikler sağlamak (namaz vakitlerini doğru hesaplamak ve kıble yönünü belirlemek), uygulamayı geliştirmek ve ilgili hizmetleri desteklemek amacıyla cihazınızın konumunu toplar.

- Konum Belirleme Hizmetleri: Hizmet Sağlayıcı, konum tabanlı özellikler veya içerik sağlamak için konum verilerini kullanabilir.
- Analiz ve İyileştirmeler: Toplu konum verileri, Hizmet Sağlayıcının kullanım kalıplarını anlamasına ve performansı iyileştirmesine yardımcı olabilir.
- Üçüncü Taraf Hizmetleri: Konum verileri, bu gizlilik politikasına ve geçerli yasalara tabi olarak, Uygulama işlevselliğini desteklemek için kullanılan üçüncü taraf hizmetleriyle paylaşılabilir.


Haklarınız
Hizmet Sağlayıcı tarafından tutulan kişisel verilerinize erişim, düzeltme veya silme talebinde bulunabilirsiniz. Bu haklarınızı kullanmak veya işleme rızaya dayalıysa rızanızı geri çekmek için ezanvaktimdestek@gmail.com adresinden Hizmet Sağlayıcı ile iletişime geçin.


Kaliforniya Gizlilik Haklarınız (CCPA/CPRA)
Eğer Kaliforniya'da ikamet ediyorsanız, hangi kişisel bilgilerin toplandığını bilme hakkına, kişisel bilgilerinizi silme hakkına, kişisel bilgilerinizin satışını veya paylaşımını reddetme hakkına ve bu haklarınızı kullanmanız nedeniyle ayrımcılığa uğramama hakkına sahipsiniz. CCPA/CPRA haklarınızı kullanmak için lütfen ezanvaktimdestek@gmail.com adresinden Hizmet Sağlayıcı ile iletişime geçin.

Hizmet Sağlayıcı, sağladığınız bilgileri önemli bilgiler ve gerekli bildirimleri göndermek için kullanabilir.

Uygulamayı kullanırken daha iyi bir deneyim sağlamak amacıyla, Hizmet Sağlayıcı sizden belirli kişisel olarak tanımlanabilir bilgileri sağlamanızı isteyebilir. Hizmet Sağlayıcının talep ettiği bilgiler, bu gizlilik politikasında açıklandığı şekilde saklanacak ve kullanılacaktır.


Üçüncü Taraf Erişimi
Yalnızca toplu ve anonimleştirilmiş veriler, Hizmet Sağlayıcının Uygulamayı ve hizmetlerini geliştirmesine yardımcı olmak amacıyla periyodik olarak harici hizmetlere iletilir. Hizmet Sağlayıcı, bilgilerinizi bu gizlilik bildiriminde açıklanan şekillerde üçüncü taraflarla paylaşabilir.

Lütfen, uygulamanın veri işleme konusunda kendi gizlilik politikalarına sahip üçüncü taraf hizmetlerini kullandığını unutmayın. Uygulama tarafından kullanılan üçüncü taraf hizmet sağlayıcılarının gizlilik politikalarına ait bağlantılar aşağıdadır:

- Google Play Hizmetleri (Google Play Services)

Hizmet Sağlayıcı, Kullanıcı Tarafından Sağlanan ve Otomatik Olarak Toplanan Bilgileri şu durumlarda açıklayabilir:

- Kanunen gerekli olduğu durumlarda, örneğin mahkeme celbine veya benzeri yasal süreçlere uymak için;
- Haklarını korumak, sizin veya başkalarının güvenliğini sağlamak, dolandırıcılığı araştırmak veya bir devlet talebine yanıt vermek için ifşa etmenin gerekli olduğuna iyi niyetle inandıklarında;
- Kendi adlarına çalışan, güvendikleri hizmet sağlayıcılarıyla işbirliği yaptıklarında (bu sağlayıcılar bilgileri bağımsız olarak kullanamazlar).


Uluslararası Veri Transferleri
Hizmet Sağlayıcı veya üçüncü taraf hizmet sağlayıcıları, kişisel verileri ikamet ettiğiniz ülke dışındaki ülkelere aktarabilir. Geçerli yasa uluslararası aktarımlar için güvenceler gerektiriyorsa, Hizmet Sağlayıcı uygun mekanizmaları kullanacaktır.


Vazgeçme Hakları ve Veri Silme
Uygulamayı kaldırarak mobil cihazınızdan daha fazla bilgi toplanmasını durdurabilirsiniz.

Kişisel verilerinizin veya hesabınızın silinmesini, ezanvaktimdestek@gmail.com adresinden Hizmet Sağlayıcı ile iletişime geçerek talep edebilirsiniz. Hizmet Sağlayıcı, talebinizi ilgili yasanın gerektirdiği süreler içinde işleme koyacaktır.


Veri Saklama Politikası
Hizmet Sağlayıcı, kişisel verileri belirtilen amaçlar için gerekli olduğu ölçüde saklar:

- Kullanıcı Tarafından Sağlanan Veriler: Uygulamayı kullandığınız süre boyunca ve sonrasında 12 ay daha saklanır.
- Otomatik Olarak Toplanan Veriler: Yasal uyumluluk için daha uzun süre saklanması gerekmedikçe, toplandığı tarihten itibaren en fazla 24 ay süreyle saklanır.
- Toplu ve Anonimleştirilmiş Veriler: Süresiz olarak saklanabilir.


Çocukların Gizliliği
Bu uygulama 13 yaşın altındaki çocuklar için tasarlanmamıştır. Hizmet Sağlayıcı, 13 yaşın altındaki çocuklardan bilerek kişisel veri toplamaz veya uygulamayı onlara pazarlamaz.

Hizmet Sağlayıcı, bir çocuğun kişisel bilgiler verdiğini tespit ederse, bu bilgileri sunucularından derhal siler. Eğer bir ebeveyn veya vasiyseniz ve çocuğunuzun Hizmet Sağlayıcıya kişisel bilgiler verdiğinin farkındaysanız, lütfen gerekli işlemlerin yapılabilmesi için ezanvaktimdestek@gmail.com adresinden bizimle iletişime geçin.


Güvenlik
Hizmet Sağlayıcı, bilgilerinizin gizliliğini korumaya önem vermektedir. Hizmet Sağlayıcı, işlediği ve sakladığı bilgileri korumak için fiziksel, elektronik ve prosedürel güvenlik önlemleri uygulamaktadır.


Veri İhlali Bildirimi
Kişisel verilerinizi etkileyen bir veri ihlali meydana gelirse, Hizmet Sağlayıcı, geçerli yasal gerekliliklere uygun olarak sizi bilgilendirecektir.


Değişiklikler
Hizmet Sağlayıcı bu Gizlilik Politikasını zaman zaman güncelleyebilir. Güncellenmiş Gizlilik Politikası yürürlük tarihiyle birlikte yayınlanacaktır.

Bu gizlilik politikası 22.09.2026 tarihinden itibaren geçerlidir.


Onayınız
Uygulamayı kullanarak ve gerekli izinleri onaylayarak bu gizlilik politikasındaki şartları kabul etmiş olursunuz. Rızanızı istediğiniz zaman izinleri kapatarak veya uygulamayı kaldırarak geri çekebilirsiniz.


Bize Ulaşın
Uygulamayı kullanırken gizlilikle ilgili herhangi bir sorunuz varsa, lütfen ezanvaktimdestek@gmail.com adresinden Hizmet Sağlayıcı ile e-posta yoluyla iletişime geçin.
""".trimIndent()