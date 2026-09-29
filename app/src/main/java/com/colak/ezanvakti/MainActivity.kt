package com.colak.ezanvakti

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.colak.ezanvakti.ui.theme.EzanvaktiTheme
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// YENİ ÖZELLİK: Alt gezinme çubuğu artık 4 sekmeden oluşuyor: Vakitler,
// Kıble, Zikirmatik, Ayarlar. Önceden Kıble ve Ayarlar üst bardaydı,
// artık hepsi tek bir alt menüde toplanıyor.
private enum class AltMenuSekmesi { VAKITLER, KIBLE, ZIKIRMATIK, AYARLAR }

class MainActivity : ComponentActivity() {

    // YENİ ÖZELLİK: Uygulama HERHANGİ bir şekilde ön plana geldiğinde
    // (bildirime tıklanarak, uygulama ikonundan direkt açılarak, arka plandan
    // geri dönülerek — fark etmez), çalmakta olan ezan sesi otomatik sussun.
    // onResume() her durumda tetiklenir (onCreate sadece ilk açılışta çalışır,
    // bu yüzden onResume kullanılıyor).
    override fun onResume() {
        super.onResume()
        EzanAlarmReceiver.uygulamayaGirinceSustur(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedPreferences = getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE)
        val dahaOnceGectiMi = sharedPreferences.getBoolean("KURULUM_TAMAMLANDI", false)

        // DÜZELTME: Varsayılan artık AÇIK ("true") — bildirim çubuğu özelliği
        // kullanıcı hiç dokunmasa bile varsayılan olarak etkin gelsin diye.
        // "dahaOnceGectiMi" şartı kaldırıldı: hem ilk kurulumu az önce
        // tamamlamış yeni kullanıcılarda hem dönen kullanıcılarda (özellikle
        // reboot sonrası, servisler sistem tarafından durdurulduğu için)
        // uygulama her açıldığında servisin çalıştığından emin oluyoruz.
        if (sharedPreferences.getBoolean("BILDIRIM_CUBUGU_AKTIF", true)) {
            EzanBildirimServisi.baslat(this)
        }

        // DÜZELTME: Tam alarm izni (SCHEDULE_EXACT_ALARM) isteği artık uygulama
        // açılır açılmaz (kullanıcı Hoş Geldin ekranını görmeden) otomatik
        // tetiklenmiyor — WelcomeScreen'in akışına, konum izninden sonra taşındı.
        // Sadece DAHA ÖNCE kurulumunu tamamlamış (dönen) kullanıcılar için, eğer
        // izin hâlâ verilmemişse burada eski davranış korunuyor.
        if (dahaOnceGectiMi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }

        // DÜZELTME: Bildirim izni artık ilk kurulumda MainActivity açılışında
        // otomatik/erken istenmiyor — bunun yerine WelcomeScreen'in 2. adımındaki
        // "TAMAM" butonuna bağlandı (kullanıcı kurulumu bitirdiğinde istenir).
        // Sadece DAHA ÖNCE kurulumunu tamamlamış (dönen) kullanıcılar için, eğer
        // izin hâlâ verilmemişse burada eski davranış korunuyor.
        if (dahaOnceGectiMi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            // YENİ EKLENEN KOD: Cihazın font/ekran büyütme ayarını yoksay ve 1x boyutta sabit tut
            val mevcutYogunluk = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = mevcutYogunluk.density, fontScale = 1f)
            ) {
                EzanvaktiTheme {
                    var splashEkraniGosterilsinMi by remember { mutableStateOf(true) }
                    var secilenSekme by remember { mutableStateOf(AltMenuSekmesi.VAKITLER) }

                    // DÜZELTME: Görünüm Modu (Sistem/Gece/Gündüz) ve Renk Teması
                    // artık burada, TEK bir paylaşılan state olarak tutuluyor —
                    // hem alt menü çubuğu (AltMenuCubugu) hem Ayarlar ekranı AYNI
                    // state'i okuyup yazıyor. Önceden ikisi ayrı ayrı
                    // SharedPreferences okuyordu; Ayarlar'dan Gece/Gündüz
                    // değiştirilince alt menü ancak sekme değiştirilince (yeniden
                    // kompoze olunca) güncelleniyordu. Artık anında güncelleniyor.
                    var temaSecimi by remember {
                        mutableStateOf(sharedPreferences.getString("TEMA_SECIMI", "Sistem") ?: "Sistem")
                    }
                    var seciliRenkTemasi by remember {
                        mutableStateOf(TemaYardimcisi.seciliTemayiOku(this@MainActivity))
                    }

                    LaunchedEffect(Unit) {
                        delay(1500L)
                        splashEkraniGosterilsinMi = false
                    }

                    if (splashEkraniGosterilsinMi) {
                        EzanVaktiSplashScreen()
                    } else if (!dahaOnceGectiMi) {
                        // DÜZELTME: Artık ayrı bir "SettingsScreen" adımına hiç
                        // uğramıyoruz — WelcomeScreen kendi içinde 2 adımlı akışı
                        // (hoş geldin + konum izni -> şehir onayı + bildirim
                        // tercihleri -> bildirim izni) tamamen yönetiyor.
                        WelcomeScreen(
                            onDevamTiklandi = {
                                sharedPreferences.edit().putBoolean("KURULUM_TAMAMLANDI", true).apply()
                                recreate()
                            }
                        )
                    } else {
                        // DÜZELTME: Sistem "geri" tuşu, sekmelerden habersizdi —
                        // geri tuşuna basınca doğrudan uygulamayı kapatıyordu.
                        // BackHandler ile: Vakitler dışındaki bir sekmedeyken geri
                        // tuşu Vakitler'e döner; Vakitler'deyken sistemin normal
                        // davranışına (uygulamayı kapat) bırakılır.
                        if (secilenSekme != AltMenuSekmesi.VAKITLER) {
                            BackHandler { secilenSekme = AltMenuSekmesi.VAKITLER }
                        }

                        Scaffold(
                            bottomBar = {
                                AltMenuCubugu(
                                    secilenSekme = secilenSekme,
                                    onSekmeSecildi = { secilenSekme = it },
                                    temaSecimi = temaSecimi,
                                    seciliRenkTemasi = seciliRenkTemasi
                                )
                            }
                        ) { icPadding ->
                            Box(modifier = Modifier.fillMaxSize().padding(bottom = icPadding.calculateBottomPadding())) {
                                when (secilenSekme) {
                                    AltMenuSekmesi.VAKITLER -> EzanVaktiHomeScreen()
                                    AltMenuSekmesi.KIBLE -> QiblaCompassScreen(
                                        onGeriTiklandi = { secilenSekme = AltMenuSekmesi.VAKITLER }
                                    )
                                    AltMenuSekmesi.ZIKIRMATIK -> ZikirmatikScreen()
                                    AltMenuSekmesi.AYARLAR -> SettingsScreen(
                                        onGeriTiklandi = { secilenSekme = AltMenuSekmesi.VAKITLER },
                                        temaSecimiDisarida = temaSecimi,
                                        onTemaSecimiDegis = { temaSecimi = it },
                                        seciliRenkTemasiDisarida = seciliRenkTemasi,
                                        onRenkTemasiDegis = { seciliRenkTemasi = it }
                                    )
                                }
                            }
                        }
                    }
                }
            } // CompositionLocalProvider Kapanışı
        } // setContent Kapanışı
    } // onCreate Kapanışı
}

// YENİ ÖZELLİK: 4 sekmeli alt gezinme çubuğu — Vakitler, Kıble, Zikirmatik,
// Ayarlar. Uygulamanın geri kalanıyla aynı koyu/açık tema mantığını kullanır.
@Composable
private fun AltMenuCubugu(
    secilenSekme: AltMenuSekmesi,
    onSekmeSecildi: (AltMenuSekmesi) -> Unit,
    temaSecimi: String,
    seciliRenkTemasi: RenkTemasi
) {
    val isDarkMode = when (temaSecimi) {
        "Gece" -> true
        "Gündüz" -> false
        else -> isSystemInDarkTheme()
    }
    val arkaPlanRengi = if (isDarkMode) Color(0xFF0F172A) else Color.White
    val pasifRenk = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val vurguRengi = seciliRenkTemasi.renk

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(arkaPlanRengi)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        AltMenuButonu("Vakitler", Icons.Default.Schedule, secilenSekme == AltMenuSekmesi.VAKITLER, vurguRengi, pasifRenk) {
            onSekmeSecildi(AltMenuSekmesi.VAKITLER)
        }
        AltMenuButonu("Kıble", Icons.Default.Explore, secilenSekme == AltMenuSekmesi.KIBLE, vurguRengi, pasifRenk) {
            onSekmeSecildi(AltMenuSekmesi.KIBLE)
        }
        AltMenuButonu("Zikirmatik", null, secilenSekme == AltMenuSekmesi.ZIKIRMATIK, vurguRengi, pasifRenk, emoji = "📿") {
            onSekmeSecildi(AltMenuSekmesi.ZIKIRMATIK)
        }
        AltMenuButonu("Ayarlar", Icons.Default.Settings, secilenSekme == AltMenuSekmesi.AYARLAR, vurguRengi, pasifRenk) {
            onSekmeSecildi(AltMenuSekmesi.AYARLAR)
        }
    }
}

@Composable
private fun AltMenuButonu(
    etiket: String,
    ikon: ImageVector?,
    seciliMi: Boolean,
    vurguRengi: Color,
    pasifRenk: Color,
    emoji: String? = null,
    onTiklandi: () -> Unit
) {
    val renk = if (seciliMi) vurguRengi else pasifRenk
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onTiklandi() }
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        if (ikon != null) {
            Icon(imageVector = ikon, contentDescription = etiket, tint = renk, modifier = Modifier.size(24.dp))
        } else if (emoji != null) {
            Text(text = emoji, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = etiket, color = renk, fontSize = 11.sp, fontWeight = if (seciliMi) FontWeight.Bold else FontWeight.Normal)
    }
}