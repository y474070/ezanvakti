package com.colak.ezanvakti

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class ZikirRengi(val kodAdi: String, val gosterilenIsim: String, val renk: Color) {
    ALTIN("altin", "Altın", Color(0xFFCBA135)),
    YESIL("yesil", "Yeşil", Color(0xFF0F8B5D)),
    MAVI("mavi", "Mavi", Color(0xFF1E4FA0)),
    MOR("mor", "Mor", Color(0xFF5B2A78)),
    PEMBE("pembe", "Pembe", Color(0xFFD98CAE));

    companion object {
        fun kodAdindanBul(kodAdi: String?): ZikirRengi =
            entries.find { it.kodAdi == kodAdi } ?: ALTIN
    }
}

enum class ZikirGorunumModu { BUTON, TESBIH }

data class ZikirTanimi(
    val id: String,
    val ad: String,
    val metin: String,
    val hedefSayi: Int
)

private const val ZIKIRLERIM_ANAHTARI = "ZIKIRLERIM_LISTESI_JSON"

private fun zikirleriKaydet(context: Context, liste: List<ZikirTanimi>) {
    val prefs = context.getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE)
    prefs.edit().putString(ZIKIRLERIM_ANAHTARI, Gson().toJson(liste)).apply()
}

private fun zikirleriYukle(context: Context): List<ZikirTanimi> {
    val prefs = context.getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE)
    val json = prefs.getString(ZIKIRLERIM_ANAHTARI, null)
    if (json == null) {
        val varsayilanListe = listOf(
            ZikirTanimi(
                id = "salavat",
                ad = "Salavat",
                metin = "Allahümme salli alâ seyyidinâ Muhammedin ve alâ âli seyyidinâ Muhammed",
                hedefSayi = 100
            )
        )
        zikirleriKaydet(context, varsayilanListe)
        return varsayilanListe
    }
    return try {
        val tip = object : TypeToken<List<ZikirTanimi>>() {}.type
        Gson().fromJson<List<ZikirTanimi>>(json, tip) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}


private fun bugununGunKodu(): String =
    java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale("tr", "TR")).format(java.util.Date())

data class ZikirGecmisiKaydi(val id: String, val ad: String, val adet: Int, val tarihMillis: Long)

private const val ZIKIR_GECMISI_ANAHTARI = "ZIKIR_GECMISI_JSON"

private fun gecmisiKaydet(context: Context, liste: List<ZikirGecmisiKaydi>) {
    val prefs = context.getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE)
    prefs.edit().putString(ZIKIR_GECMISI_ANAHTARI, Gson().toJson(liste)).apply()
}

private fun gecmisiYukle(context: Context): List<ZikirGecmisiKaydi> {
    val prefs = context.getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE)
    val json = prefs.getString(ZIKIR_GECMISI_ANAHTARI, null) ?: return emptyList()
    return try {
        val tip = object : TypeToken<List<ZikirGecmisiKaydi>>() {}.type
        Gson().fromJson<List<ZikirGecmisiKaydi>>(json, tip) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

private fun tarihFormatla(millis: Long): String {
    val fmt = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale("tr", "TR"))
    return fmt.format(java.util.Date(millis))
}

private enum class ZikirmatikSayfa { SAYAC, LISTEM, GECMIS }

private const val ZIKIR_HEDEF_SAYI = 99

private fun titret(context: Context, turTamamlandiMi: Boolean) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val efekt = if (turTamamlandiMi) {

            VibrationEffect.createWaveform(
                longArrayOf(0, 110, 90, 110),
                intArrayOf(0, 255, 0, 255),
                -1
            )
        } else {
            VibrationEffect.createOneShot(100, 255)
        }
        vibrator.vibrate(efekt)
    } else {
        @Suppress("DEPRECATION")
        if (turTamamlandiMi) vibrator.vibrate(longArrayOf(0, 110, 90, 110), -1)
        else vibrator.vibrate(100)
    }
}

@Composable
fun ZikirmatikScreen() {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE)

    val temaSecimi = remember { sharedPreferences.getString("TEMA_SECIMI", "Sistem") ?: "Sistem" }
    val isDarkMode = when (temaSecimi) {
        "Gece" -> true
        "Gündüz" -> false
        else -> isSystemInDarkTheme()
    }
    val arkaPlanKatmanRengi =
        if (isDarkMode) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.65f)
    val yaziRengi = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val ikincilYaziRengi = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    var sayac by remember { mutableStateOf(sharedPreferences.getInt("ZIKIR_SAYAC", 0)) }
    var turSayisi by remember { mutableStateOf(sharedPreferences.getInt("ZIKIR_TUR", 0)) }
    var seciliRenk by remember {
        mutableStateOf(
            ZikirRengi.kodAdindanBul(
                sharedPreferences.getString(
                    "ZIKIR_RENGI",
                    ZikirRengi.ALTIN.kodAdi
                )
            )
        )
    }
    var gorunumModu by remember {
        mutableStateOf(
            if (sharedPreferences.getString(
                    "ZIKIR_GORUNUM_MODU",
                    "TESBIH"
                ) == "BUTON"
            ) ZikirGorunumModu.BUTON else ZikirGorunumModu.TESBIH
        )
    }
    var sesAktif by remember {
        mutableStateOf(
            sharedPreferences.getBoolean(
                "ZIKIR_SES_AKTIF",
                true
            )
        )
    }
    var titresimAktif by remember {
        mutableStateOf(
            sharedPreferences.getBoolean(
                "ZIKIR_TITRESIM_AKTIF",
                true
            )
        )
    }
    var ayarlarAcikMi by remember { mutableStateOf(false) }

    var sayfa by remember { mutableStateOf(ZikirmatikSayfa.SAYAC) }
    BackHandler(enabled = sayfa != ZikirmatikSayfa.SAYAC) {
        sayfa = ZikirmatikSayfa.SAYAC
    }
    var zikirlerim by remember { mutableStateOf(zikirleriYukle(context)) }
    var aktifZikir by remember { mutableStateOf<ZikirTanimi?>(null) }
    var yeniZikirFormuAcikMi by remember { mutableStateOf(false) }
    var gecmisSifirlaOnayAcikMi by remember { mutableStateOf(false) }
    var otomatikSayacPaneliAcikMi by remember { mutableStateOf(false) }
    var sayacSifirlaOnayAcikMi by remember { mutableStateOf(false) }
    var olcumBasladiMi by remember { mutableStateOf(false) }
    var olcumBaslangicZamani by remember { mutableStateOf(0L) }
    var otomatikSayimAktifMi by remember { mutableStateOf(false) }

    fun otomatikSureAnahtari(): String =
        if (aktifZikir != null) "ZIKIR_OTOMATIK_SURE_MS_${aktifZikir!!.id}" else "ZIKIR_OTOMATIK_SURE_MS_VARSAYILAN"

    var birZikirSuresiMs by remember(aktifZikir) {
        mutableStateOf(
            sharedPreferences.getLong(
                otomatikSureAnahtari(),
                0L
            )
        )
    }
    var zikirlerimDuzenlemeModu by remember { mutableStateOf(false) }
    var duzenlenenZikir by remember { mutableStateOf<ZikirTanimi?>(null) }
    var silinecekZikir by remember { mutableStateOf<ZikirTanimi?>(null) }
    var aktifZikirToplam by remember { mutableStateOf(0) }
    val zikirGecmisiMap = remember {
        androidx.compose.runtime.mutableStateMapOf<String, ZikirGecmisiKaydi>().apply {
            gecmisiYukle(context).forEach { put(it.id, it) }
        }
    }
    val gecmisKaydetKapsam = rememberCoroutineScope()
    fun gecmisiArkaPlandaKaydet() {
        val anlikListe = zikirGecmisiMap.values.toList()
        gecmisKaydetKapsam.launch(Dispatchers.IO) {
            gecmisiKaydet(context, anlikListe)
        }
    }

    fun sayacAnahtari(): String =
        if (aktifZikir != null) "ZIKIR_SAYAC_${aktifZikir!!.id}" else "ZIKIR_SAYAC"

    fun turAnahtari(): String =
        if (aktifZikir != null) "ZIKIR_TUR_${aktifZikir!!.id}" else "ZIKIR_TUR"
    LaunchedEffect(aktifZikir) {
        sayac = sharedPreferences.getInt(sayacAnahtari(), 0)
        turSayisi = sharedPreferences.getInt(turAnahtari(), 0)
        aktifZikirToplam = if (aktifZikir != null) {
            zikirGecmisiMap["${aktifZikir!!.id}_${bugununGunKodu()}"]?.adet ?: 0
        } else 0
        otomatikSayimAktifMi = false
        olcumBasladiMi = false
    }

    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    var butonSesId by remember { mutableStateOf(0) }
    var tesbihSesId by remember { mutableStateOf(0) }
    var kontrolNoktasiSesId by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        butonSesId = soundPool.load(context, R.raw.buton, 1)
        tesbihSesId = soundPool.load(context, R.raw.tesbih, 1)
        kontrolNoktasiSesId = soundPool.load(context, R.raw.tesbih_bildirim, 1)
    }
    DisposableEffect(Unit) {
        onDispose { soundPool.release() }
    }

    fun sesCal(sesId: Int) {
        if (sesAktif && sesId != 0) {
            soundPool.play(sesId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun sayaciArttir(sesId: Int) {
        sayac += 1
        sesCal(sesId)
        if (aktifZikir != null) {
            val kayitId = "${aktifZikir!!.id}_${bugununGunKodu()}"
            val mevcutKayit = zikirGecmisiMap[kayitId]
            val guncellenmisKayit = ZikirGecmisiKaydi(
                id = kayitId,
                ad = aktifZikir!!.ad,
                adet = (mevcutKayit?.adet ?: 0) + 1,
                tarihMillis = System.currentTimeMillis()
            )
            zikirGecmisiMap[kayitId] = guncellenmisKayit
            gecmisiArkaPlandaKaydet()
            aktifZikirToplam = guncellenmisKayit.adet
        }
        val hedef = aktifZikir?.hedefSayi ?: ZIKIR_HEDEF_SAYI
        val turTamamlandiMi = sayac >= hedef
        val kontrolNoktasi1 = (hedef / 3).coerceAtLeast(1)
        val kontrolNoktasi2 = (hedef * 2 / 3).coerceAtLeast(kontrolNoktasi1 + 1)
        val kontrolNoktasiMi =
            turTamamlandiMi || sayac == kontrolNoktasi1 || sayac == kontrolNoktasi2
        if (kontrolNoktasiMi) {
            sesCal(kontrolNoktasiSesId)
            if (titresimAktif) {
                titret(context, turTamamlandiMi)
            }
        }

        if (turTamamlandiMi) {
            sayac = 0
            turSayisi += 1
            sharedPreferences.edit().putInt(turAnahtari(), turSayisi).apply()
        }
        sharedPreferences.edit().putInt(sayacAnahtari(), sayac).apply()
    }

    fun sayaciSifirla() {
        sayac = 0
        turSayisi = 0
        sharedPreferences.edit().putInt(sayacAnahtari(), 0).putInt(turAnahtari(), 0).apply()
    }

    LaunchedEffect(otomatikSayimAktifMi) {
        if (otomatikSayimAktifMi && birZikirSuresiMs > 0L) {
            while (otomatikSayimAktifMi) {
                kotlinx.coroutines.delay(birZikirSuresiMs)
                if (otomatikSayimAktifMi) {
                    sayaciArttir(if (gorunumModu == ZikirGorunumModu.BUTON) butonSesId else tesbihSesId)
                }
            }
        }
    }


    val yerelYogunluk = LocalDensity.current
    val metricler = context.resources.displayMetrics

    // Ekranı daha küçük göstermek için bölen sayıyı 360f'ten 420f'e çıkardık.
    // Bu sayı büyüdükçe ekrandaki ögeler küçülür.
    val gercekPikselGenislik = metricler.widthPixels.toFloat()

    val sabitYoğunluk = remember(gercekPikselGenislik) {
        androidx.compose.ui.unit.Density(
            density = gercekPikselGenislik / 420f,
            fontScale = 1f
        )
    }
    CompositionLocalProvider(LocalDensity provides sabitYoğunluk) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.mevlana_bg),
                contentDescription = "Arka Plan",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(modifier = Modifier
                .fillMaxSize()
                .background(arkaPlanKatmanRengi))

            Column(modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (sayfa == ZikirmatikSayfa.LISTEM) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                sayfa = ZikirmatikSayfa.SAYAC
                                zikirlerimDuzenlemeModu = false
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Geri",
                                    tint = yaziRengi
                                )
                            }
                            Text(
                                text = "Zikirlerim",
                                color = yaziRengi,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    } else if (sayfa == ZikirmatikSayfa.GECMIS) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { sayfa = ZikirmatikSayfa.SAYAC }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Geri",
                                    tint = yaziRengi
                                )
                            }
                            Text(
                                text = "Zikir Geçmişi",
                                color = yaziRengi,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    } else {
                        Text(
                            text = "Zikirmatik",
                            color = yaziRengi,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    if (sayfa == ZikirmatikSayfa.SAYAC) {
                        Row(verticalAlignment = Alignment.CenterVertically) {

                            TextButton(onClick = { sayfa = ZikirmatikSayfa.LISTEM }) {
                                Text(
                                    text = "Zikirlerim",
                                    color = yaziRengi,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(onClick = { sayfa = ZikirmatikSayfa.GECMIS }) {
                                Text(
                                    text = "Geçmiş",
                                    color = yaziRengi,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(onClick = { ayarlarAcikMi = true }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Zikirmatik Ayarları",
                                    tint = yaziRengi,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    } else if (sayfa == ZikirmatikSayfa.GECMIS) {

                        TextButton(onClick = { gecmisSifirlaOnayAcikMi = true }) {
                            Text(
                                text = "Sıfırla",
                                color = Color(0xFFEF4444),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (sayfa == ZikirmatikSayfa.LISTEM) {
                        IconButton(onClick = {
                            zikirlerimDuzenlemeModu = !zikirlerimDuzenlemeModu
                        }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Düzenle",
                                tint = if (zikirlerimDuzenlemeModu) seciliRenk.renk else yaziRengi,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (sayfa) {
                    ZikirmatikSayfa.SAYAC -> {
                        OlcekliIcerik { olcek ->
                            when (gorunumModu) {
                                ZikirGorunumModu.BUTON -> ButonGorunumu(
                                    sayac = sayac,
                                    turSayisi = turSayisi,
                                    renk = seciliRenk.renk,
                                    yaziRengi = yaziRengi,
                                    ikincilYaziRengi = ikincilYaziRengi,
                                    isDarkMode = isDarkMode,
                                    aktifZikir = aktifZikir,
                                    aktifZikirToplam = aktifZikirToplam,
                                    olcek = olcek,
                                    onArttir = { if (!otomatikSayimAktifMi) sayaciArttir(butonSesId) }
                                )

                                ZikirGorunumModu.TESBIH -> TesbihGorunumu(
                                    sayac = sayac,
                                    turSayisi = turSayisi,
                                    renk = seciliRenk.renk,
                                    yaziRengi = yaziRengi,
                                    ikincilYaziRengi = ikincilYaziRengi,
                                    isDarkMode = isDarkMode,
                                    aktifZikir = aktifZikir,
                                    aktifZikirToplam = aktifZikirToplam,
                                    olcek = olcek,
                                    onArttir = { if (!otomatikSayimAktifMi) sayaciArttir(tesbihSesId) }
                                )
                            }
                        }
                    }

                    ZikirmatikSayfa.LISTEM -> ZikirlerimListesi(
                        zikirlerim = zikirlerim,
                        yaziRengi = yaziRengi,
                        ikincilYaziRengi = ikincilYaziRengi,
                        renk = seciliRenk.renk,
                        isDarkMode = isDarkMode,
                        duzenlemeModu = zikirlerimDuzenlemeModu,
                        onSecildi = {
                            aktifZikir = it
                            sayfa = ZikirmatikSayfa.SAYAC
                        },
                        onDuzenleTiklandi = { duzenlenenZikir = it },
                        onSilTiklandi = { silinecekZikir = it },
                        onEkleTiklandi = { yeniZikirFormuAcikMi = true }
                    )

                    ZikirmatikSayfa.GECMIS -> ZikirGecmisiListesi(
                        gecmis = zikirGecmisiMap.values.toList(),
                        yaziRengi = yaziRengi,
                        ikincilYaziRengi = ikincilYaziRengi,
                        renk = seciliRenk.renk,
                        isDarkMode = isDarkMode
                    )
                }
            }

            if (sayfa == ZikirmatikSayfa.SAYAC) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = { sayacSifirlaOnayAcikMi = true },
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Sayacı Sıfırla",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    IconButton(
                        onClick = {
                            if (otomatikSayimAktifMi) {
                                otomatikSayimAktifMi = false
                            } else {
                                otomatikSayacPaneliAcikMi = true
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (otomatikSayimAktifMi) seciliRenk.renk else lerp(
                                    seciliRenk.renk,
                                    Color.Black,
                                    0.4f
                                )
                            )
                    ) {
                        Icon(
                            imageVector = if (otomatikSayimAktifMi) Icons.Filled.Stop else Icons.Filled.Timer,
                            contentDescription = "Otomatik Sayaç",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        if (yeniZikirFormuAcikMi) {
            YeniZikirFormu(
                isDarkMode = isDarkMode,
                renk = seciliRenk.renk,
                onKaydet = { yeniZikir ->
                    val guncelListe = zikirlerim + yeniZikir
                    zikirlerim = guncelListe
                    zikirleriKaydet(context, guncelListe)
                    yeniZikirFormuAcikMi = false
                },
                onIptal = { yeniZikirFormuAcikMi = false }
            )
        }
        if (duzenlenenZikir != null) {
            YeniZikirFormu(
                isDarkMode = isDarkMode,
                renk = seciliRenk.renk,
                duzenlenecekZikir = duzenlenenZikir,
                onKaydet = { guncellenmisZikir ->
                    val guncelListe =
                        zikirlerim.map { if (it.id == guncellenmisZikir.id) guncellenmisZikir else it }
                    zikirlerim = guncelListe
                    zikirleriKaydet(context, guncelListe)
                    duzenlenenZikir = null
                },
                onIptal = { duzenlenenZikir = null }
            )
        }

        if (otomatikSayacPaneliAcikMi) {
            val panelArkaPlan =
                if (isDarkMode) TemaYardimcisi.kartArkaPlaniniHesapla(seciliRenk.renk, isDarkMode)
                    .copy(alpha = 0.75f) else Color.White.copy(alpha = 0.75f)
            val panelYaziRengi = if (isDarkMode) Color.White else Color(0xFF0F172A)
            val panelIkincilRengi = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

            AlertDialog(
                onDismissRequest = {
                    otomatikSayacPaneliAcikMi = false
                },
                containerColor = panelArkaPlan,
                title = {
                    Text(
                        text = "Otomatik Sayaç",
                        color = panelYaziRengi,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    run {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(panelYaziRengi.copy(alpha = 0.06f))
                                    .clickable {
                                        if (!olcumBasladiMi) {
                                            otomatikSayimAktifMi = false
                                            olcumBaslangicZamani = System.currentTimeMillis()
                                            olcumBasladiMi = true
                                        } else {
                                            val gecenSure =
                                                System.currentTimeMillis() - olcumBaslangicZamani
                                            if (gecenSure > 200L) {


                                                val yuvarlanmisSure =
                                                    (((gecenSure + 25) / 50) * 50).coerceAtLeast(50L)
                                                birZikirSuresiMs = yuvarlanmisSure
                                                sharedPreferences.edit().putLong(
                                                    otomatikSureAnahtari(),
                                                    yuvarlanmisSure
                                                ).apply()
                                            }
                                            olcumBasladiMi = false
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (olcumBasladiMi) "Okumayı Bitir" else "Okumaya Başla",
                                    color = panelYaziRengi,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                if (!olcumBasladiMi) {
                                    Text(
                                        text = "${birZikirSuresiMs} ms",
                                        color = panelIkincilRengi,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "\"Okumaya Başla\"ya basıp zikri bir kez normal hızınızda okuyun, bitirince tekrar dokunun — süre otomatik ölçülür.",
                                color = panelIkincilRengi,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Manuel Ayarla",
                                color = panelYaziRengi,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))


                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(seciliRenk.renk.copy(alpha = 0.18f))
                                        .clickable {
                                            val yeni = (birZikirSuresiMs - 50L).coerceAtLeast(50L)
                                            birZikirSuresiMs = yeni
                                            sharedPreferences.edit()
                                                .putLong(otomatikSureAnahtari(), yeni).apply()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "−",
                                        color = seciliRenk.renk,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "${birZikirSuresiMs} ms",
                                    color = panelYaziRengi,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(seciliRenk.renk.copy(alpha = 0.18f))
                                        .clickable {
                                            val yeni = birZikirSuresiMs + 50L
                                            birZikirSuresiMs = yeni
                                            sharedPreferences.edit()
                                                .putLong(otomatikSureAnahtari(), yeni).apply()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+",
                                        color = seciliRenk.renk,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(22.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { otomatikSayacPaneliAcikMi = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "İptal",
                                        color = panelIkincilRengi,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        sharedPreferences.edit()
                                            .putLong(otomatikSureAnahtari(), birZikirSuresiMs)
                                            .apply()
                                        otomatikSayimAktifMi = true






                                        otomatikSayacPaneliAcikMi = false
                                    },
                                    enabled = birZikirSuresiMs > 0L,
                                    modifier = Modifier.weight(1f),


                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        seciliRenk.renk
                                    )
                                ) {
                                    Text(
                                        "Başlat",
                                        color = seciliRenk.renk,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },


                confirmButton = {}
            )
        }


        if (silinecekZikir != null) {
            val onayArkaPlan = if (isDarkMode) TemaYardimcisi.kartArkaPlaniniHesapla(
                seciliRenk.renk,
                isDarkMode
            ) else Color.White
            val onayYaziRengi = if (isDarkMode) Color.White else Color(0xFF0F172A)
            val onayIkincilRengi = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
            AlertDialog(
                onDismissRequest = { silinecekZikir = null },
                containerColor = onayArkaPlan,
                title = {
                    Text(
                        text = "Zikri Sil",
                        color = onayYaziRengi,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "\"${silinecekZikir!!.ad}\" adlı zikri silmek istediğinize emin misiniz?",
                        color = onayIkincilRengi
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val guncelListe = zikirlerim.filterNot { it.id == silinecekZikir!!.id }
                        zikirlerim = guncelListe
                        zikirleriKaydet(context, guncelListe)




                        if (aktifZikir?.id == silinecekZikir!!.id) {
                            aktifZikir = null
                        }
                        silinecekZikir = null
                    }) {
                        Text("Onayla", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { silinecekZikir = null }) {
                        Text("İptal", color = onayIkincilRengi)
                    }
                }
            )
        }



        if (sayacSifirlaOnayAcikMi) {
            val onayArkaPlan = if (isDarkMode) TemaYardimcisi.kartArkaPlaniniHesapla(
                seciliRenk.renk,
                isDarkMode
            ) else Color.White
            val onayYaziRengi = if (isDarkMode) Color.White else Color(0xFF0F172A)
            val onayIkincilRengi = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
            AlertDialog(
                onDismissRequest = { sayacSifirlaOnayAcikMi = false },
                containerColor = onayArkaPlan,
                title = {
                    Text(
                        text = "Sayacı Sıfırla",
                        color = onayYaziRengi,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Mevcut sayaç ve tur sıfırlanacak, emin misiniz?",
                        color = onayIkincilRengi
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        sayaciSifirla()
                        sayacSifirlaOnayAcikMi = false
                    }) {
                        Text("Onayla", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { sayacSifirlaOnayAcikMi = false }) {
                        Text("İptal", color = onayIkincilRengi)
                    }
                }
            )
        }


        if (gecmisSifirlaOnayAcikMi) {
            val onayArkaPlan = if (isDarkMode) TemaYardimcisi.kartArkaPlaniniHesapla(
                seciliRenk.renk,
                isDarkMode
            ) else Color.White
            val onayYaziRengi = if (isDarkMode) Color.White else Color(0xFF0F172A)
            val onayIkincilRengi = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
            AlertDialog(
                onDismissRequest = { gecmisSifirlaOnayAcikMi = false },
                containerColor = onayArkaPlan,
                title = {
                    Text(
                        text = "Zikir Geçmişini Sıfırla",
                        color = onayYaziRengi,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Emin misiniz? Tüm zikir geçmişi silinecek, bu işlem geri alınamaz.",
                        color = onayIkincilRengi
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        zikirGecmisiMap.clear()
                        gecmisiArkaPlandaKaydet()



                        aktifZikirToplam = 0
                        gecmisSifirlaOnayAcikMi = false
                    }) {
                        Text("Onayla", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { gecmisSifirlaOnayAcikMi = false }) {
                        Text("İptal", color = onayIkincilRengi)
                    }
                }
            )
        }

        if (ayarlarAcikMi) {
            ZikirAyarlarDialog(
                isDarkMode = isDarkMode,
                seciliRenk = seciliRenk,
                gorunumModu = gorunumModu,
                sesAktif = sesAktif,
                titresimAktif = titresimAktif,
                onSesDegis = {
                    sesAktif = it
                    sharedPreferences.edit().putBoolean("ZIKIR_SES_AKTIF", it).apply()
                },
                onTitresimDegis = {
                    titresimAktif = it
                    sharedPreferences.edit().putBoolean("ZIKIR_TITRESIM_AKTIF", it).apply()
                },
                onRenkSec = {
                    seciliRenk = it
                    sharedPreferences.edit().putString("ZIKIR_RENGI", it.kodAdi).apply()
                },
                onModSec = {
                    gorunumModu = it
                    sharedPreferences.edit().putString("ZIKIR_GORUNUM_MODU", it.name).apply()
                },
                onSifirlaOnaylandi = { sayaciSifirla() },
                onKapat = { ayarlarAcikMi = false }
            )
        }
    }
}

@Composable
private fun OlcekliIcerik(icerik: @Composable (olcek: Float) -> Unit) {
    val context = LocalContext.current
    val metricler = context.resources.displayMetrics

    val gercekPikselYukseklik = metricler.heightPixels.toFloat()
    val gercekPikselGenislik = metricler.widthPixels.toFloat()
    // Yukarıdaki ile aynı değeri (420f) kullanmalıyız
    val ozelYogunluk = gercekPikselGenislik / 420f
    val bagimsizYukseklikDp = gercekPikselYukseklik / ozelYogunluk

    val olcek = remember(bagimsizYukseklikDp) {
        val referansYukseklikDp = 720f // 640f'ten 720f'e çıkardık
        (bagimsizYukseklikDp / referansYukseklikDp).coerceIn(0.6f, 1f)
    }
    icerik(olcek)
}

@Composable
private fun SayacGostergesi(
    sayac: Int,
    turSayisi: Int,
    renk: Color,
    yaziRengi: Color,
    ikincilYaziRengi: Color,
    isDarkMode: Boolean,
    olcek: Float = 1f
) {


    val pilArkaPlani = TemaYardimcisi.kartArkaPlaniniHesapla(renk, isDarkMode).copy(alpha = 0.75f)


    val vurguYaziRengi = if (isDarkMode) renk else Color.Black
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(pilArkaPlani)
            .padding(horizontal = (32 * olcek).dp, vertical = (16 * olcek).dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$sayac",
                color = vurguYaziRengi,
                fontSize = (72 * olcek).sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "$turSayisi Tur",
                color = vurguYaziRengi,
                fontSize = (27 * olcek).sp,
                fontWeight = FontWeight.ExtraBold,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        offset = androidx.compose.ui.geometry.Offset(1.5f, 1.5f),
                        blurRadius = 4f
                    )
                )
            )
        }
    }
}

@Composable
private fun ButonGorunumu(
    sayac: Int,
    turSayisi: Int,
    renk: Color,
    yaziRengi: Color,
    ikincilYaziRengi: Color,
    isDarkMode: Boolean,
    aktifZikir: ZikirTanimi?,
    aktifZikirToplam: Int,
    olcek: Float = 1f,
    onArttir: () -> Unit
) {


    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = interactionSource, indication = null) { onArttir() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {


            if (aktifZikir != null) {


                val pilArkaPlaniIsimMetin =
                    TemaYardimcisi.kartArkaPlaniniHesapla(renk, isDarkMode).copy(alpha = 0.75f)
                val isimYaziRengi = if (isDarkMode) Color.White else Color(0xFF64748B)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(pilArkaPlaniIsimMetin)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${aktifZikir.ad}  $aktifZikirToplam",
                        color = isimYaziRengi,
                        fontSize = (18 * olcek).sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (aktifZikirToplam >= aktifZikir.hedefSayi) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Hedefe ulaşıldı",
                            tint = renk,
                            modifier = Modifier.size((20 * olcek).dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height((10 * olcek).dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(pilArkaPlaniIsimMetin)
                        .padding(horizontal = 18.dp, vertical = (10 * olcek).dp)
                ) {
                    Text(
                        text = aktifZikir.metin,
                        color = yaziRengi,
                        fontSize = (14 * olcek).sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height((18 * olcek).dp))
            }
            DaireselSayacGostergesi(
                sayac = sayac,
                turSayisi = turSayisi,
                hedef = aktifZikir?.hedefSayi ?: ZIKIR_HEDEF_SAYI,
                renk = renk,
                ortaDaireRengi = renk.copy(alpha = 0.65f),
                isDarkMode = isDarkMode,
                olcek = olcek
            )
        }
    }
}


@Composable
private fun DaireselSayacGostergesi(
    sayac: Int,
    turSayisi: Int,
    hedef: Int,
    renk: Color,
    ortaDaireRengi: Color,
    isDarkMode: Boolean,
    olcek: Float = 1f
) {
    val ilerleme = (sayac % hedef) / hedef.toFloat()
    val animeliIlerleme by animateFloatAsState(
        targetValue = ilerleme,
        animationSpec = tween(280),
        label = "ilerleme"
    )


    val merkezYaziRengi = if (isDarkMode) Color.White else Color.Black

    Box(modifier = Modifier.size((270 * olcek).dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = (22 * olcek).dp.toPx()
            val cap = strokeWidth
            val capinSize = Size(size.width - cap, size.height - cap)
            val topLeft = Offset(cap / 2f, cap / 2f)


            drawArc(
                color = Color.White.copy(alpha = 0.92f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = capinSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            val sweep = 360f * animeliIlerleme
            if (sweep > 0.5f) {
                drawArc(
                    color = renk,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = capinSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )


                val aciRadyan = Math.toRadians((-90f + sweep).toDouble())
                val yaricap = capinSize.minDimension / 2f
                val merkezX = size.width / 2f
                val merkezY = size.height / 2f
                val topuzX = merkezX + yaricap * cos(aciRadyan).toFloat()
                val topuzY = merkezY + yaricap * sin(aciRadyan).toFloat()
                drawCircle(
                    color = renk,
                    radius = strokeWidth * 0.62f,
                    center = Offset(topuzX, topuzY)
                )
            }
        }

        Box(
            modifier = Modifier
                .size((205 * olcek).dp)
                .clip(CircleShape)
                .background(ortaDaireRengi),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$sayac",
                    color = merkezYaziRengi,
                    fontSize = (58 * olcek).sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$turSayisi Tur",
                    color = merkezYaziRengi.copy(alpha = 0.85f),
                    fontSize = (20 * olcek).sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}


private fun boncukFirca(renk: Color): Brush {
    return Brush.radialGradient(
        colors = listOf(
            lerp(renk, Color.White, 0.55f),
            renk,
            lerp(renk, Color.Black, 0.35f)
        )
    )
}


@Composable
private fun TesbihGorunumu(
    sayac: Int,
    turSayisi: Int,
    renk: Color,
    yaziRengi: Color,
    ikincilYaziRengi: Color,
    isDarkMode: Boolean,
    aktifZikir: ZikirTanimi?,
    aktifZikirToplam: Int,
    olcek: Float = 1f,
    onArttir: () -> Unit
) {
    val esikMesafePx = 34f
    val maksimumKaymaPx = 75f
    val kaymaAnim = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()


    val birakmaAnimasyonu = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height((24 * olcek).dp))


        if (aktifZikir != null) {


            val pilArkaPlaniIsimMetin =
                TemaYardimcisi.kartArkaPlaniniHesapla(renk, isDarkMode).copy(alpha = 0.75f)
            val isimYaziRengi = if (isDarkMode) Color.White else Color(0xFF64748B)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(pilArkaPlaniIsimMetin)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${aktifZikir.ad}  $aktifZikirToplam",
                    color = isimYaziRengi,
                    fontSize = (16 * olcek).sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (aktifZikirToplam >= aktifZikir.hedefSayi) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Hedefe ulaşıldı",
                        tint = renk,
                        modifier = Modifier.size((18 * olcek).dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height((6 * olcek).dp))
        }
        SayacGostergesi(
            sayac,
            turSayisi,
            renk,
            yaziRengi,
            ikincilYaziRengi,
            isDarkMode,
            olcek = olcek
        )
        Spacer(modifier = Modifier.height((16 * olcek).dp))


        if (aktifZikir != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        TemaYardimcisi.kartArkaPlaniniHesapla(renk, isDarkMode).copy(alpha = 0.75f)
                    )
                    .padding(horizontal = 20.dp, vertical = (10 * olcek).dp)
            ) {
                Text(
                    text = aktifZikir.metin,
                    color = yaziRengi,
                    fontSize = (14 * olcek).sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height((16 * olcek).dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            coroutineScope.launch {
                                kaymaAnim.stop()
                                kaymaAnim.snapTo(0f)
                            }
                        },
                        onDragEnd = {
                            val yeterinceCekildiMi = kaymaAnim.value >= esikMesafePx
                            coroutineScope.launch {
                                kaymaAnim.animateTo(0f, animationSpec = birakmaAnimasyonu)
                            }
                            if (yeterinceCekildiMi) onArttir()
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                kaymaAnim.animateTo(0f, animationSpec = birakmaAnimasyonu)
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()


                            val yeniDeger =
                                (kaymaAnim.value + dragAmount).coerceIn(0f, maksimumKaymaPx)
                            coroutineScope.launch { kaymaAnim.snapTo(yeniDeger) }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {


            val cekisIlerlemesi = (kaymaAnim.value / esikMesafePx).coerceIn(0f, 1f)








            Box(
                modifier = Modifier
                    .height((TESBIH_GORUNUM_YUKSEKLIGI * olcek).dp)
                    .fillMaxWidth()
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {

                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .align(Alignment.Center)
                        .background(lerp(renk, Color.Black, 0.55f))
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset(0, kaymaAnim.value.roundToInt()) }
                ) {
                    TesbihIpiGorseli(renk = renk, cekisIlerlemesi = cekisIlerlemesi, olcek = olcek)
                }
            }
        }

        Spacer(modifier = Modifier.height((12 * olcek).dp))
        Text(text = "Parmağını bastır, çek ve bırak", color = ikincilYaziRengi, fontSize = 13.sp)
        Spacer(modifier = Modifier.height((24 * olcek).dp))
    }
}

private const val TESBIH_BONCUK_SAYISI = 13
private const val TESBIH_MIN_BOYUT_DP = 20f
private const val TESBIH_MAX_BOYUT_DP = 48f
private const val TESBIH_ETKI_ALANI_YUVA = 3f
private const val TESBIH_GORUNUM_YUKSEKLIGI = 335f


@Composable
private fun TesbihIpiGorseli(renk: Color, cekisIlerlemesi: Float, olcek: Float = 1f) {
    val ortaYuva = (TESBIH_BONCUK_SAYISI - 1) / 2
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((6 * olcek).dp)
    ) {
        repeat(TESBIH_BONCUK_SAYISI) { index ->
            val j = index - ortaYuva
            val boyut = boncukBoyutuHesapla(
                j,
                cekisIlerlemesi,
                TESBIH_MIN_BOYUT_DP * olcek,
                TESBIH_MAX_BOYUT_DP * olcek
            )
            Box(
                modifier = Modifier
                    .size(boyut.dp)
                    .clip(CircleShape)
                    .background(boncukFirca(renk))
            )
        }
    }
}


private fun boncukBoyutuHesapla(
    j: Int,
    cekisIlerlemesi: Float,
    minBoyut: Float,
    maxBoyut: Float
): Float {
    val mesafe = kotlin.math.abs(j + cekisIlerlemesi)
    if (mesafe >= TESBIH_ETKI_ALANI_YUVA) return minBoyut
    val faktor = (1f + cos((Math.PI * mesafe / TESBIH_ETKI_ALANI_YUVA)).toFloat()) / 2f
    return minBoyut + (maxBoyut - minBoyut) * faktor
}

@Composable
private fun ZikirAyarlarDialog(
    isDarkMode: Boolean,
    seciliRenk: ZikirRengi,
    gorunumModu: ZikirGorunumModu,
    sesAktif: Boolean,
    titresimAktif: Boolean,
    onSesDegis: (Boolean) -> Unit,
    onTitresimDegis: (Boolean) -> Unit,
    onRenkSec: (ZikirRengi) -> Unit,
    onModSec: (ZikirGorunumModu) -> Unit,
    onSifirlaOnaylandi: () -> Unit,
    onKapat: () -> Unit
) {


    val arkaPlan =
        if (isDarkMode) TemaYardimcisi.kartArkaPlaniniHesapla(seciliRenk.renk, isDarkMode)
            .copy(alpha = 0.75f) else Color.White.copy(alpha = 0.75f)
    val yaziRengi = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val ikincilYaziRengi = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)


    var onaySoruluyorMu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onKapat,
        containerColor = arkaPlan,
        title = {
            Text(
                text = "Zikirmatik Ayarları",
                color = yaziRengi,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                ZikirAcKapaSatiri("Ses", sesAktif, seciliRenk.renk, yaziRengi, onSesDegis)
                Spacer(modifier = Modifier.height(4.dp))
                ZikirAcKapaSatiri(
                    "Titreşim",
                    titresimAktif,
                    seciliRenk.renk,
                    yaziRengi,
                    onTitresimDegis
                )

                Spacer(modifier = Modifier.height(20.dp))
                Divider(color = ikincilYaziRengi.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Renk",
                    color = ikincilYaziRengi,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ZikirRengi.entries.forEach { renk ->
                        val seciliMi = renk == seciliRenk
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(renk.renk)
                                .border(
                                    width = if (seciliMi) 3.dp else 0.dp,
                                    color = if (isDarkMode) Color.White else Color(0xFF0F172A),
                                    shape = CircleShape
                                )
                                .clickable { onRenkSec(renk) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Görünüm",
                    color = ikincilYaziRengi,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ZikirModSekmesi(
                        "Buton",
                        gorunumModu == ZikirGorunumModu.BUTON,
                        seciliRenk.renk,
                        yaziRengi
                    ) {
                        onModSec(ZikirGorunumModu.BUTON)
                    }
                    ZikirModSekmesi(
                        "Tesbih",
                        gorunumModu == ZikirGorunumModu.TESBIH,
                        seciliRenk.renk,
                        yaziRengi
                    ) {
                        onModSec(ZikirGorunumModu.TESBIH)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                TextButton(onClick = { onaySoruluyorMu = true }) {
                    Text(
                        text = "Sayacı Sıfırla",
                        color = Color(0xFFEF4444),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onKapat) {
                Text(text = "Kapat", color = seciliRenk.renk, fontWeight = FontWeight.Bold)
            }
        }
    )

    if (onaySoruluyorMu) {
        AlertDialog(
            onDismissRequest = { onaySoruluyorMu = false },
            containerColor = arkaPlan,
            title = {
                Text(
                    text = "Sayacı Sıfırla",
                    color = yaziRengi,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(text = "Sıfırlamayı onaylıyor musunuz?", color = ikincilYaziRengi) },
            confirmButton = {
                TextButton(onClick = {
                    onSifirlaOnaylandi()
                    onaySoruluyorMu = false
                    onKapat()
                }) {
                    Text(text = "Onayla", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onaySoruluyorMu = false }) {
                    Text(text = "Vazgeç", color = ikincilYaziRengi)
                }
            }
        )
    }
}

@Composable
private fun ZikirAcKapaSatiri(
    etiket: String,
    acikMi: Boolean,
    vurguRengi: Color,
    yaziRengi: Color,
    onDegis: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = etiket, color = yaziRengi, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Switch(
            checked = acikMi,
            onCheckedChange = onDegis,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = vurguRengi
            )
        )
    }
}

@Composable
private fun ZikirModSekmesi(
    etiket: String,
    seciliMi: Boolean,
    vurguRengi: Color,
    yaziRengi: Color,
    onTiklandi: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (seciliMi) vurguRengi.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                1.dp,
                if (seciliMi) vurguRengi else Color(0xFF64748B).copy(alpha = 0.4f),
                RoundedCornerShape(10.dp)
            )
            .clickable { onTiklandi() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = etiket,
            color = if (seciliMi) vurguRengi else yaziRengi,
            fontSize = 13.sp,
            fontWeight = if (seciliMi) FontWeight.Bold else FontWeight.Normal
        )
    }
}


@Composable
private fun ZikirlerimListesi(
    zikirlerim: List<ZikirTanimi>,
    yaziRengi: Color,
    ikincilYaziRengi: Color,
    renk: Color,
    isDarkMode: Boolean,
    duzenlemeModu: Boolean,
    onSecildi: (ZikirTanimi) -> Unit,
    onDuzenleTiklandi: (ZikirTanimi) -> Unit,
    onSilTiklandi: (ZikirTanimi) -> Unit,
    onEkleTiklandi: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (zikirlerim.isEmpty()) {
            Text(
                text = "Henüz zikir eklemedin — sağ alttaki + butonuyla ekleyebilirsin.",
                color = ikincilYaziRengi,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(zikirlerim) { zikir ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                TemaYardimcisi.kartArkaPlaniniHesapla(renk, isDarkMode)
                                    .copy(alpha = 0.55f)
                            )
                            .border(1.dp, renk.copy(alpha = 0.35f), RoundedCornerShape(16.dp))


                            .clickable {
                                if (duzenlemeModu) onDuzenleTiklandi(zikir) else onSecildi(zikir)
                            }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = zikir.ad,
                                color = yaziRengi,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = zikir.metin,
                                color = ikincilYaziRengi,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }


                        if (duzenlemeModu) {
                            Spacer(modifier = Modifier.width(12.dp))
                            IconButton(onClick = { onSilTiklandi(zikir) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Sil",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }


        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(60.dp)
                .clip(CircleShape)
                .background(renk)
                .clickable { onEkleTiklandi() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Yeni Zikir Ekle",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}


@Composable
private fun YeniZikirFormu(
    isDarkMode: Boolean,
    renk: Color,
    duzenlenecekZikir: ZikirTanimi? = null,
    onKaydet: (ZikirTanimi) -> Unit,
    onIptal: () -> Unit
) {
    val arkaPlan =
        if (isDarkMode) TemaYardimcisi.kartArkaPlaniniHesapla(renk, isDarkMode) else Color.White
    val yaziRengi = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val ikincilYaziRengi = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)


    var ad by remember { mutableStateOf(duzenlenecekZikir?.ad ?: "") }
    var hedefMetni by remember { mutableStateOf(duzenlenecekZikir?.hedefSayi?.toString() ?: "") }
    var zikirMetni by remember { mutableStateOf(duzenlenecekZikir?.metin ?: "") }

    val alanRenkleri = TextFieldDefaults.colors(
        focusedTextColor = yaziRengi,
        unfocusedTextColor = yaziRengi,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = renk,
        unfocusedIndicatorColor = ikincilYaziRengi.copy(alpha = 0.4f),
        cursorColor = renk,
        focusedLabelColor = renk,
        unfocusedLabelColor = ikincilYaziRengi,

        // EKLENEN KISIM: Damla (handle) ve metin seçme arka plan rengini temaya uydurur
        selectionColors = TextSelectionColors(
            handleColor = renk,
            backgroundColor = renk.copy(alpha = 0.4f)
        )
    )

    AlertDialog(
        onDismissRequest = onIptal,
        containerColor = arkaPlan,
        title = {
            Text(
                text = if (duzenlenecekZikir != null) "Zikri Düzenle" else "Yeni Zikir Ekle",
                color = yaziRengi,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            CompositionLocalProvider(
                LocalTextSelectionColors provides TextSelectionColors(
                    handleColor = renk,
                    backgroundColor = renk.copy(alpha = 0.4f)
                )
            ) {
                Column {
                    TextField(
                        value = ad,
                        onValueChange = { ad = it },
                        label = { Text("Zikir Adı") },
                        singleLine = true,
                        colors = alanRenkleri,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = hedefMetni,
                        onValueChange = { girilen ->
                            if (girilen.all { it.isDigit() }) hedefMetni = girilen
                        },
                        label = { Text("Kaç Adet Çekilecek") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        colors = alanRenkleri,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = zikirMetni,
                        onValueChange = { zikirMetni = it },
                        label = { Text("Zikir Metni") },
                        minLines = 3,
                        colors = alanRenkleri,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {


                    val hedef = hedefMetni.toIntOrNull()
                    if (ad.isNotBlank() && zikirMetni.isNotBlank() && hedef != null && hedef > 0) {
                        onKaydet(
                            ZikirTanimi(


                                id = duzenlenecekZikir?.id ?: System.currentTimeMillis().toString(),
                                ad = ad.trim(),
                                metin = zikirMetni.trim(),
                                hedefSayi = hedef
                            )
                        )
                    }
                }
            ) {
                Text("Kaydet", color = renk, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onIptal) {
                Text("İptal", color = ikincilYaziRengi)
            }
        }
    )
}


@Composable
private fun ZikirGecmisiListesi(
    gecmis: List<ZikirGecmisiKaydi>,
    yaziRengi: Color,
    ikincilYaziRengi: Color,
    renk: Color,
    isDarkMode: Boolean
) {
    if (gecmis.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Henüz tamamlanmış bir zikir yok.",
                color = ikincilYaziRengi,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }
        return
    }

    val siraliGecmis = remember(gecmis) { gecmis.sortedByDescending { it.tarihMillis } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(siraliGecmis) { kayit ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        TemaYardimcisi.kartArkaPlaniniHesapla(renk, isDarkMode).copy(alpha = 0.55f)
                    )
                    .border(1.dp, renk.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = kayit.ad,
                        color = yaziRengi,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tarihFormatla(kayit.tarihMillis),
                        color = ikincilYaziRengi,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = "${kayit.adet} adet",
                    color = renk,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}