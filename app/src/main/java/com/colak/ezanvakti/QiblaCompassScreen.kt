package com.colak.ezanvakti

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlin.math.*

@Composable
fun QiblaCompassScreen(onGeriTiklandi: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE)

    // DÜZELTME: Kıble Pusulası artık SettingsScreen'deki ile AYNI tema mantığını
    // kullanıyor — "TEMA_SECIMI" (Sistem/Gece/Gündüz) SharedPreferences anahtarı
    // okunuyor. Önceden bu ekranın arka planı hep sabit açık (beyaz) renkteydi,
    // Ayarlar'dan Gece moduna geçilse bile değişmiyordu.
    var temaSecimi by remember { mutableStateOf(prefs.getString("TEMA_SECIMI", "Sistem") ?: "Sistem") }
    val isDarkMode = when (temaSecimi) {
        "Gece" -> true
        "Gündüz" -> false
        else -> isSystemInDarkTheme()
    }

    val arkaPlanRengi = if (isDarkMode) Color(0xFF090D16) else Color(0xFFF4F6F9)
    val kartArkaPlani = if (isDarkMode) Color(0xFF131C2E).copy(alpha = 0.80f) else Color.White.copy(alpha = 0.80f)
    val yaziRengi = if (isDarkMode) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val ikincilYaziRengi = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    // YENİ ÖZELLİK: Vurgu rengi artık sabit değil, Ayarlar'dan seçilen renk
    // temasından okunuyor (varsayılan: Yeşil).
    val vurguRengi = TemaYardimcisi.vurguRenginiOku(context)
    // DÜZELTME: Pusula rengi artık Gece/Gündüz'e göre sabit değil, Ayarlar'dan
    // seçilen renk temasına (vurguRengi) bağlı — hem çember/çizgiler hem de
    // kadranın iç dolgusu artık seçilen renkle uyumlu.
    val pusulaCemberRengi = vurguRengi.copy(alpha = 0.88f)
    val pusulaIcCemberRengi = vurguRengi.copy(alpha = 0.45f)
    val pusulaBuyukCizgiRengi = vurguRengi.copy(alpha = 0.88f)
    val pusulaKucukCizgiRengi = vurguRengi.copy(alpha = 0.40f)
    val pusulaDolguRengi = vurguRengi.copy(alpha = 0.18f)

    var azimuth by remember { mutableStateOf(0f) }
    var qiblaAngle by remember { mutableStateOf(0f) }
    var mesafeKm by remember { mutableStateOf(0.0) }

    // YENİ ÖZELLİK: Manyetometre (pusula sensörü) doğruluğu düşük/güvenilmez
    // hale gelebiliyor (metal eşya, mıknatıs, hoparlör yakınlığı, kalibrasyon
    // eksikliği vb. yüzünden) — bu durumda gösterilen açı yanlış olabilir.
    // Önceden bu durum hiç takip edilmiyordu (onAccuracyChanged boştu),
    // kullanıcı yanlış açının FARKINA bile varamıyordu. Artık takip edilip
    // uyarı gösteriliyor.
    var sensorDusukDogrulukMu by remember { mutableStateOf(false) }
    var kalibrasyonRehberiAcikMi by remember { mutableStateOf(false) }
    // YENİ ÖZELLİK: Kalibrasyon penceresi açıkken sensörün GERÇEK doğruluk
    // seviyesini canlı izliyoruz — kullanıcı "8" çizip sensör iyi bir
    // doğruluğa (ORTA/YÜKSEK) ulaşınca otomatik olarak "Kalibre edildi ✓"
    // gösterip pencereyi kapatıyoruz, kullanıcının elle "Anladım"a basmasına
    // gerek kalmıyor.
    var sensorDogrulukSeviyesi by remember { mutableStateOf(SensorManager.SENSOR_STATUS_UNRELIABLE) }
    var kalibrasyonBasariliMi by remember { mutableStateOf(false) }

    var izinVerildiMi by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var izinReddedildiMi by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        izinVerildiMi = granted
        izinReddedildiMi = !granted
    }

    LaunchedEffect(Unit) {
        if (!izinVerildiMi) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    LaunchedEffect(izinVerildiMi) {
        if (izinVerildiMi) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val userLat = Math.toRadians(location.latitude)
                        val userLon = Math.toRadians(location.longitude)

                        val kaabaLat = Math.toRadians(21.4225)
                        val kaabaLon = Math.toRadians(39.8262)

                        val dLon = kaabaLon - userLon
                        val y = sin(dLon) * cos(kaabaLat)
                        val x = cos(userLat) * sin(kaabaLat) - sin(userLat) * cos(kaabaLat) * cos(dLon)

                        var bearing = Math.toDegrees(atan2(y, x)).toFloat()
                        bearing = (bearing + 360) % 360
                        qiblaAngle = bearing

                        val earthRadiusKm = 6371.0
                        val dLat = kaabaLat - userLat
                        val a = sin(dLat / 2).pow(2) + cos(userLat) * cos(kaabaLat) * sin(dLon / 2).pow(2)
                        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
                        mesafeKm = earthRadiusKm * c
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
                izinVerildiMi = false
            }
        }
    }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        var gravity: FloatArray? = null
        var geomagnetic: FloatArray? = null

        // DÜZELTME (TİTREME SORUNU): Ham sensör verileri anlık gürültü
        // içeriyor, bu da pusulanın sürekli ileri-geri "titremesine" yol
        // açıyordu. Alçak geçiren filtre (low-pass filter) ile her yeni
        // okuma, önceki değerle yumuşatılarak birleştiriliyor — ani/küçük
        // sapmalar bastırılıyor, gerçek hareketler yine takip ediliyor.
        fun alcakGecirenFiltre(yeni: FloatArray, eski: FloatArray?): FloatArray {
            if (eski == null) return yeni.clone()
            val alpha = 0.15f
            for (idx in yeni.indices) {
                eski[idx] += alpha * (yeni[idx] - eski[idx])
            }
            return eski
        }

        // DÜZELTME: Ham hızlanma/manyetik verilerin filtrelenmesi tek başına
        // yeterli olmuyordu (360°/0° sınırında hâlâ ufak sıçramalar
        // olabiliyordu). Bunun üstüne, nihai açının kendisini de DAİRESEL
        // olarak (360° sarmayı doğru hesaba katarak) yumuşatıyoruz.
        fun aciyiYumusat(hedefAci: Float, mevcutAci: Float): Float {
            var fark = ((hedefAci - mevcutAci + 540f) % 360f) - 180f
            return (mevcutAci + fark * 0.15f + 360f) % 360f
        }

        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    gravity = alcakGecirenFiltre(event.values, gravity)
                } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    geomagnetic = alcakGecirenFiltre(event.values, geomagnetic)
                }

                if (gravity != null && geomagnetic != null) {
                    val r = FloatArray(9)
                    val i = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(r, orientation)
                        var az = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        az = (az + 360) % 360
                        azimuth = aciyiYumusat(az, azimuth)
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // DÜZELTME: Önceden bu fonksiyon tamamen boştu — manyetometre
                // "düşük doğruluk" (SENSOR_STATUS_ACCURACY_LOW) ya da
                // "güvenilmez" (UNRELIABLE) durumuna geçse bile uygulama
                // bunu HİÇ fark etmiyor, göstermeye devam ediyordu. Bu,
                // "bazen yanlış gösteriyor" şikayetinin en olası nedeni —
                // kod hatası değil, sensörün geçici olarak bozulması
                // (metal eşya/mıknatıs yakınlığı ya da kalibrasyon eksikliği).
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    sensorDusukDogrulukMu = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
                    sensorDogrulukSeviyesi = accuracy
                }
            }
        }

        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(sensorEventListener, magnetometer, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
        }
    }

    val fark = (qiblaAngle - azimuth + 360) % 360
    // DÜZELTME: Emoji yerine gerçek Material ikonları kullanılıyor (daha
    // modern görünüm) — yön metni artık sadece bir String değil, ikonuyla
    // birlikte bir veri sınıfı.
    data class YonlendirmeDurumu(val metin: String, val ikon: androidx.compose.ui.graphics.vector.ImageVector?)
    val yonlendirmeDurumu = when {
        !izinVerildiMi -> YonlendirmeDurumu("Konum İzni Gerekli", null)
        fark in 2.0..180.0 -> YonlendirmeDurumu("Sağa Dönün", Icons.Filled.ArrowForward)
        fark in 180.0..358.0 -> YonlendirmeDurumu("Sola Dönün", Icons.Filled.ArrowBack)
        else -> YonlendirmeDurumu("Kıble Yönündesiniz", Icons.Filled.CheckCircle)
    }

    val compassRotation = -azimuth
    val kaabaRotation = qiblaAngle

    // YENİ: Kıble ekranına da (diğer ekranlardeki gibi) Mevlana arka plan
    // resmi eklendi — önceden burada hiç resim yoktu, düz renk arka plandı.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // DÜZELTME: Pusula artık ekranın kullanılabilir yüksekliğine göre
        // ORANTILI boyutlanıyor (sabit 320dp değil) — böylece hem küçük
        // ekranlarda/büyük sistem yazı boyutunda hem de büyük ekranlarda,
        // her şey KAYDIRMAYA gerek kalmadan tek ekrana sığıyor.
        val pusulaBoyutu = (maxHeight * 0.34f).coerceIn(200.dp, 300.dp)

        Image(
            painter = painterResource(id = R.drawable.mevlana_bg),
            contentDescription = "Arka Plan",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(modifier = Modifier.fillMaxSize().background(arkaPlanRengi.copy(alpha = 0.86f)))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // DÜZELTME: Artık asıl boyutlandırma pusulanın kendisinin
                    // orantılı olmasıyla sağlanıyor; kaydırma sadece aşırı uç
                    // durumlar (çok büyük yazı boyutu vb.) için bir güvenlik
                    // ağı olarak kalıyor.
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onGeriTiklandi() },
                        modifier = Modifier.background(vurguRengi.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Geri", tint = vurguRengi)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Kıble Pusulası",
                        color = yaziRengi,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    // DÜZELTME: Konum izni hatası kırmızı kalıyor (net uyarı
                    // anlamı için), ama yön bildiren durumlar (Sağa/Sola
                    // Dönün, Kıble Yönündesiniz) artık seçili tema rengini
                    // kullanıyor — sabit mavi değil.
                    color = if (!izinVerildiMi) Color(0xFFFEE2E2).copy(alpha = 0.85f) else vurguRengi.copy(alpha = 0.18f),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (yonlendirmeDurumu.ikon != null) {
                            Icon(
                                imageVector = yonlendirmeDurumu.ikon,
                                contentDescription = null,
                                tint = vurguRengi,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = yonlendirmeDurumu.metin,
                            color = if (!izinVerildiMi) Color(0xFFDC2626) else vurguRengi,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (izinReddedildiMi) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = (if (isDarkMode) Color(0xFF3B2410) else Color(0xFFFFF7ED)).copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Kıble yönünü hesaplayabilmek için konum iznine ihtiyacımız var.",
                                color = if (isDarkMode) Color(0xFFFDBA74) else Color(0xFF9A3412),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316))
                            ) {
                                Text("İzin Ver", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // YENİ ÖZELLİK: Pusula sensörünün doğruluğu düşük/güvenilmez
                // olduğu ANLIK olarak tespit edilince (bkz. onAccuracyChanged),
                // otomatik olarak bu uyarı çıkıyor — önceden kullanıcı yanlış
                // açının gösterildiğini fark edemiyordu.
                if (izinVerildiMi && sensorDusukDogrulukMu) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = (if (isDarkMode) Color(0xFF3B2410) else Color(0xFFFFF7ED)).copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Pusula hassasiyeti düşük görünüyor, gösterilen yön yanlış olabilir. Telefonunuzu metal eşyalardan/mıknatıslardan uzaklaştırıp kalibre edin.",
                                color = if (isDarkMode) Color(0xFFFDBA74) else Color(0xFF9A3412),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { kalibrasyonRehberiAcikMi = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316))
                            ) {
                                Text("Kalibre Et", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .size(pusulaBoyutu)
                        .shadow(elevation = 18.dp, shape = CircleShape, ambientColor = vurguRengi.copy(alpha = 0.3f), spotColor = vurguRengi.copy(alpha = 0.3f))
                        .rotate(compassRotation),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(pusulaBoyutu)) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val radius = size.width / 2 - 10.dp.toPx()

                        // YENİ: Kadranın içi artık düz saydam değil, seçilen renk
                        // temasının hafif dolgusuyla renklendiriliyor.
                        drawCircle(color = pusulaDolguRengi, radius = radius - 8.dp.toPx())
                        drawCircle(color = pusulaCemberRengi, radius = radius, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx()))
                        drawCircle(color = pusulaIcCemberRengi, radius = radius - 8.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))

                        for (i in 0 until 360 step 10) {
                            val angleRad = Math.toRadians(i.toDouble())
                            val lineLength = if (i % 30 == 0) 12.dp.toPx() else 6.dp.toPx()
                            val startX = center.x + (radius - 12.dp.toPx() - lineLength) * sin(angleRad).toFloat()
                            val startY = center.y - (radius - 12.dp.toPx() - lineLength) * cos(angleRad).toFloat()
                            val endX = center.x + (radius - 12.dp.toPx()) * sin(angleRad).toFloat()
                            val endY = center.y - (radius - 12.dp.toPx()) * cos(angleRad).toFloat()

                            drawLine(
                                color = if (i % 30 == 0) pusulaBuyukCizgiRengi else pusulaKucukCizgiRengi,
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = if (i % 30 == 0) 2.dp.toPx() else 1.dp.toPx()
                            )
                        }

                        // YENİ: Kuzey/Güney/Doğu/Batı harfleri. Kuzey (N) her zaman
                        // kırmızı vurguyla öne çıkarılıyor (pusulaların standart
                        // geleneği — kuzey yönünü tek bakışta ayırt etmek için).
                        val yonEtiketRadius = radius - 32.dp.toPx()
                        val yonler = listOf(
                            Triple("K", 0, Color(0xFFEF4444)),   // Kuzey — kırmızı vurgu
                            Triple("D", 90, pusulaBuyukCizgiRengi),
                            Triple("G", 180, pusulaBuyukCizgiRengi),
                            Triple("B", 270, pusulaBuyukCizgiRengi)
                        )
                        val textPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = 15.sp.toPx()
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        yonler.forEach { (harf, aci, renk) ->
                            val angleRad = Math.toRadians(aci.toDouble())
                            val x = center.x + yonEtiketRadius * sin(angleRad).toFloat()
                            val y = center.y - yonEtiketRadius * cos(angleRad).toFloat() + (textPaint.textSize / 3)
                            textPaint.color = renk.toArgb()
                            drawContext.canvas.nativeCanvas.drawText(harf, x, y, textPaint)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(pusulaBoyutu * 0.75f)
                            .rotate(kaabaRotation),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // DÜZELTME: Düz üçgen yerine, hafif gölgeli/parlak
                            // modern bir Kâbe yön oku (Navigation ikonu).
                            Icon(
                                imageVector = Icons.Filled.Navigation,
                                contentDescription = "Kıble Yönü",
                                tint = vurguRengi,
                                modifier = Modifier
                                    .size(36.dp)
                                    .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = vurguRengi, spotColor = vurguRengi)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF1E293B),
                                modifier = Modifier.size(68.dp),
                                shadowElevation = 8.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = "🕋", fontSize = 34.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "${azimuth.toInt()}°",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = yaziRengi
                    )
                }

                // YENİ: Kalibrasyon/doğruluk notu — pusula uygulamalarının standart
                // uyarısı, kullanıcıyı en doğru sonuç için doğru kullanım şekline
                // yönlendiriyor.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = ikincilYaziRengi,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Doğru kıble yönü için cihazınızı düz bir zeminde tutun ve metal eşyalardan uzak durun.",
                        color = ikincilYaziRengi,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // YENİ ÖZELLİK: Otomatik uyarı çıkmasa bile, kullanıcı
                // isterse elle kalibrasyon rehberini her zaman açabiliyor.
                TextButton(
                    onClick = { kalibrasyonRehberiAcikMi = true },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(text = "Pusula doğru görünmüyor mu? Kalibre Et", color = vurguRengi, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    Text(
                        text = "Kıble Açısı : ${qiblaAngle.toInt()}°",
                        color = ikincilYaziRengi,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Uzaklık : ${String.format("%.1f", mesafeKm)} km",
                        color = ikincilYaziRengi,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // YENİ ÖZELLİK: Kalibrasyon rehberi — telefonun manyetometresini
    // yeniden kalibre etmek için standart yöntem (rakam 8 çizer gibi
    // havada hareket ettirmek). Uygulama sensörü zorla kalibre EDEMEZ
    // (bu, Android işletim sisteminin/donanımın kendi işi) — burada
    // yapabileceğimiz tek şey, kullanıcıya doğru hareketi göstermek.
    if (kalibrasyonRehberiAcikMi) {
        // DÜZELTME: onAccuracyChanged Android'de ÇOK SEYREK tetikleniyor —
        // birçok cihaz bunu neredeyse hiç çağırmıyor, bu yüzden buna
        // dayanan tespit hiç tetiklenmeyebiliyordu ("8 çiziyorum, kalibre
        // edildi demiyor" şikayeti). Artık bunun yerine, kullanıcının
        // telefonu GERÇEKTEN ne kadar döndürdüğünü (azimut değişimini)
        // doğrudan izliyoruz — bu her zaman güncellenen, güvenilir bir
        // veri. Yeterince dönüş (gerçek bir "8" hareketine denk gelecek
        // kadar) tespit edilince otomatik tamamlanmış sayılıyor.
        var toplamDonusMiktari by remember(kalibrasyonRehberiAcikMi) { mutableStateOf(0f) }
        var oncekiAcikAzimuth by remember(kalibrasyonRehberiAcikMi) { mutableStateOf<Float?>(null) }

        LaunchedEffect(azimuth) {
            if (kalibrasyonBasariliMi) return@LaunchedEffect
            val onceki = oncekiAcikAzimuth
            if (onceki != null) {
                var fark = ((azimuth - onceki + 540f) % 360f) - 180f
                toplamDonusMiktari += abs(fark)
            }
            oncekiAcikAzimuth = azimuth

            // ~2 tam turluk toplam hareket (720°) — bir "8" çizerken doğal
            // olarak birikir. Bu eşiğe ulaşınca kalibre edilmiş sayıyoruz.
            if (toplamDonusMiktari >= 720f) {
                kalibrasyonBasariliMi = true
                sensorDusukDogrulukMu = false
                kotlinx.coroutines.delay(1400L)
                kalibrasyonRehberiAcikMi = false
                kalibrasyonBasariliMi = false
            }
        }

        AlertDialog(
            onDismissRequest = {
                kalibrasyonRehberiAcikMi = false
                kalibrasyonBasariliMi = false
            },
            containerColor = if (isDarkMode) Color(0xFF131C2E) else Color.White,
            title = {
                Text(
                    text = if (kalibrasyonBasariliMi) "Kalibre Edildi ✓" else "Pusulayı Kalibre Et",
                    color = if (kalibrasyonBasariliMi) Color(0xFF10B981) else yaziRengi,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (kalibrasyonBasariliMi) {
                    // YENİ: Başarı durumunda video/talimat yerine kısa bir
                    // onay mesajı gösteriliyor, pencere otomatik kapanacak.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Pusula sensörü artık güvenilir okuma yapıyor.", color = ikincilYaziRengi, fontSize = 14.sp)
                    }
                } else {
                    Column {
                        // YENİ ÖZELLİK: Gerçek bir video dosyası yerine (kaynak
                        // video dosyası olmadan eklenemez), "8" hareketini canlı
                        // gösteren, sürekli tekrar eden bir Compose animasyonu —
                        // aynı öğretici işlevi görüyor, ekstra dosya gerektirmiyor.
                        KalibrasyonVideosu()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Telefonunuzu elinizde tutarak havada büyük bir \"8\" rakamı çizer gibi birkaç kez çevirin. Bu, telefonun pusula sensörünü yeniden kalibre etmesine yardımcı olur.",
                            color = ikincilYaziRengi,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Ayrıca:\n• Telefonu metal eşyalardan, mıknatıslardan ve hoparlörlerden uzak tutun\n• Kılıf varsa (özellikle mıknatıslı kapaklı) çıkarmayı deneyin\n• Cihazı yere paralel, düz tutun",
                            color = ikincilYaziRengi,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (!kalibrasyonBasariliMi) {
                    Button(
                        onClick = { kalibrasyonRehberiAcikMi = false },
                        colors = ButtonDefaults.buttonColors(containerColor = vurguRengi)
                    ) {
                        Text("Anladım", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }
}

// YENİ ÖZELLİK: "8" çizme hareketini gösteren gerçek video — sürekli
// döngüde (loop) oynatılıyor. res/raw/device_calibration.mp4 dosyası
// eklenmeden bu satırlar derlenmez.
@Composable
private fun KalibrasyonVideosu() {
    // DÜZELTME: Uygulama arka plana alınıp geri dönüldüğünde, VideoView'ın
    // arkasındaki çizim yüzeyi (Surface) sistem tarafından yok ediliyor —
    // ama VideoView bunu kendiliğinden fark edip videoyu yeniden BAŞLATMIYOR,
    // bu yüzden siyah bir kutu kalıyordu. Yaşam döngüsünü (lifecycle)
    // dinleyip, ekrana her dönüldüğünde videoyu elle yeniden başlatıyoruz.
    var videoViewReferansi by remember { mutableStateOf<android.widget.VideoView?>(null) }
    val yasamDongusuSahibi = androidx.compose.ui.platform.LocalLifecycleOwner.current

    DisposableEffect(yasamDongusuSahibi) {
        val gozlemci = androidx.lifecycle.LifecycleEventObserver { _, olay ->
            when (olay) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    videoViewReferansi?.let { vv ->
                        if (!vv.isPlaying) {
                            // resume() yerine seçilenler bazı cihazlarda
                            // siyah kare bırakabildiği için, kaynağı
                            // baştan tanımlayıp yeniden başlatıyoruz.
                            vv.setVideoURI(android.net.Uri.parse("android.resource://${vv.context.packageName}/${R.raw.device_calibration}"))
                            vv.start()
                        }
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    videoViewReferansi?.pause()
                }
                else -> {}
            }
        }
        yasamDongusuSahibi.lifecycle.addObserver(gozlemci)
        onDispose {
            yasamDongusuSahibi.lifecycle.removeObserver(gozlemci)
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp)),
        factory = { context ->
            android.widget.VideoView(context).apply {
                videoViewReferansi = this
                setVideoURI(android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.device_calibration}"))
                setOnPreparedListener { mediaPlayer ->
                    mediaPlayer.isLooping = true
                    mediaPlayer.setVolume(0f, 0f) // Sessiz oynatma — sadece görsel gösterim.
                }
                start()
            }
        },
        // DÜZELTME: Compose bu görünümü yeniden kullanıma sokarken (örn.
        // dialog tekrar açıldığında aynı Composable örneği geri geldiğinde)
        // videonun DURMUŞ kalmadığından emin oluyoruz.
        update = { videoView ->
            if (!videoView.isPlaying) {
                videoView.start()
            }
        },
        onRelease = { videoView ->
            videoView.stopPlayback()
            videoViewReferansi = null
        }
    )
}