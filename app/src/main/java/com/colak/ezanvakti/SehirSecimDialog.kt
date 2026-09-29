package com.colak.ezanvakti

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.platform.LocalContext
import java.text.Collator
import java.util.Locale

// DÜZELTME: Kotlin'in varsayılan String sıralaması Unicode kod noktasına
// göre çalışıyor — bu, Türkçe alfabede "Ç" harfini "Z"den SONRAYA
// koyuyordu (yanlış). Türkçe'ye özel Collator ile doğru alfabetik sırayı
// (A B C Ç D E F G Ğ H I İ ... Z) elde ediyoruz.
private val turkceCollator: Collator = Collator.getInstance(Locale("tr", "TR"))

@Composable
fun SehirSecimDialog(
    // YENİ ÖZELLİK: Artık 4. bir parametre (sehirKodu) de dönüyor — Bayram
    // Namazı API'si ilçe değil ŞEHİR seviyesinde kod istiyor.
    onSecildi: (String, String, String, String) -> Unit,
    onKapat: () -> Unit,
    vurguRengi: Color = TemaYardimcisi.vurguRenginiOku(LocalContext.current)
) {
    val context = LocalContext.current

    // DÜZELTME (PERFORMANS): İl listesi hemen (tek istek) çekilir. İlçeler artık
    // kullanıcı bir ile tıkladığında, sadece o il için lazy olarak çekiliyor —
    // bu yüzden dialog açılışı artık neredeyse anlık.
    var tumSehirler by remember { mutableStateOf<List<JsonYardimcisi.SehirBilgisi>>(emptyList()) }
    var illerYukleniyorMu by remember { mutableStateOf(true) }
    var illerCanliMi by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val (liste, canliMi) = JsonYardimcisi.getSehirlerCanli(context)
        tumSehirler = liste.sortedWith(compareBy(turkceCollator) { it.sehirAdi })
        illerCanliMi = canliMi
        illerYukleniyorMu = false
    }

    var secilenSehir by remember { mutableStateOf<JsonYardimcisi.SehirBilgisi?>(null) }
    var secilenIlIlceleri by remember { mutableStateOf<List<IlceModel>>(emptyList()) }
    var ilceYukleniyorMu by remember { mutableStateOf(false) }
    var ilceCanliMi by remember { mutableStateOf(true) }

    var geciciSecilenIlce by remember { mutableStateOf<IlceModel?>(null) }
    var aramaMetni by remember { mutableStateOf("") }

    // DÜZELTME: API bazı iller için merkez ilçeyi "Merkez" yerine doğrudan
    // ilin kendi adıyla döndürüyor (ör. Konya ilinin merkez ilçesi API'de
    // "Konya" olarak geliyor, kullanıcı için kafa karıştırıcı). Burada:
    //   1) İlçe adı, il adıyla aynıysa (ör. "Konya" ilçesi, "Konya" ili
    //      içinde) veya zaten "Merkez" ise -> ekranda "Merkez" olarak
    //      gösteriyoruz (gerçek diyanetKodu değişmiyor, sadece görünen isim).
    //   2) Bu merkez girdisini listenin EN BAŞINA alıyoruz, geri kalanını
    //      alfabetik sıralıyoruz.
    fun merkeziBasaAlVeEtiketle(sehirAdi: String, ilceler: List<IlceModel>): List<IlceModel> {
        val sehirNorm = sehirAdi.trim().lowercase()
        val (merkezOlanlar, digerleri) = ilceler.partition { ilce ->
            val ilceNorm = ilce.ilceAdi.trim().lowercase()
            ilceNorm == sehirNorm || ilceNorm == "merkez"
        }
        val merkezEtiketli = merkezOlanlar
            .distinctBy { it.diyanetKodu }
            .map { it.copy(ilceAdi = "Merkez") }
        val digerleriSirali = digerleri.sortedWith(compareBy(turkceCollator) { it.ilceAdi })
        return merkezEtiketli + digerleriSirali
    }

    fun ilSec(sehir: JsonYardimcisi.SehirBilgisi) {
        secilenSehir = sehir
        geciciSecilenIlce = null
        secilenIlIlceleri = emptyList()
        ilceYukleniyorMu = true
    }

    // Bir il seçildiğinde SADECE O İLİN ilçelerini lazy çek
    LaunchedEffect(secilenSehir) {
        val sehir = secilenSehir
        if (sehir != null) {
            val sonuc = JsonYardimcisi.getIlcelerCanli(context, sehir)
            secilenIlIlceleri = merkeziBasaAlVeEtiketle(sehir.sehirAdi, sonuc.ilceler)
            ilceCanliMi = sonuc.kaynakCanliMi
            ilceYukleniyorMu = false
        }
    }

    AlertDialog(
        onDismissRequest = { onKapat() },
        containerColor = Color(0xFF1E293B),
        title = {
            Text(
                text = if (secilenSehir == null) "İl Seçiniz" else "${secilenSehir!!.sehirAdi} - İlçe Seçiniz",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                if (!illerCanliMi && !illerYukleniyorMu) {
                    Text(
                        text = "⚠️ İnternet bağlantısı kurulamadı, çevrimdışı liste gösteriliyor.",
                        color = Color(0xFFFBBF24),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (illerYukleniyorMu) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = vurguRengi)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "Şehirler yükleniyor...", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        }
                    }
                } else if (tumSehirler.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Şehir listesi yüklenemedi. Lütfen internet bağlantınızı kontrol edip tekrar deneyin.",
                            color = Color(0xFFF87171),
                            fontSize = 13.sp
                        )
                    }
                } else if (secilenSehir == null) {
                    val filtrelenmisIller = tumSehirler.filter {
                        it.sehirAdi.contains(aramaMetni, ignoreCase = true)
                    }

                    // DÜZELTME: "cursorColor" sadece yanıp sönen imleç
                    // çizgisini boyuyor — konum ayarlarken çıkan sürükleme
                    // tutamacı (teardrop/handle) AYRI bir renk kaynağından
                    // (LocalTextSelectionColors) geliyor ve sabit pembe
                    // kalıyordu. Burada açıkça tema rengine bağlıyoruz.
                    val secimRenkleri = TextSelectionColors(
                        handleColor = vurguRengi,
                        backgroundColor = vurguRengi.copy(alpha = 0.3f)
                    )
                    CompositionLocalProvider(LocalTextSelectionColors provides secimRenkleri) {
                        OutlinedTextField(
                            value = aramaMetni,
                            onValueChange = { aramaMetni = it },
                            placeholder = { Text("Şehir ara...", color = Color(0xFF94A3B8)) },
                            // DÜZELTME: Kenarlık, yazı imleci (cursor) artık sabit
                            // yeşil/pembe değil — seçili tema rengini kullanıyor.
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = vurguRengi,
                                unfocusedBorderColor = Color(0xFF475569),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = vurguRengi
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(filtrelenmisIller) { sehir ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { ilSec(sehir) },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF334155))
                            ) {
                                Text(
                                    text = sehir.sehirAdi,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                } else if (ilceYukleniyorMu) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = vurguRengi)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "İlçeler yükleniyor...", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        }
                    }
                } else {
                    if (!ilceCanliMi) {
                        Text(
                            text = "⚠️ Bu ilin ilçeleri çevrimdışı listeden geldi, kodlar güncel olmayabilir.",
                            color = Color(0xFFFBBF24),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(secilenIlIlceleri) { ilce ->
                            val seciliMi = geciciSecilenIlce?.ilceAdi == ilce.ilceAdi
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        geciciSecilenIlce = ilce
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (seciliMi) vurguRengi.copy(alpha = 0.35f) else vurguRengi.copy(alpha = 0.16f)
                                )
                            ) {
                                Text(
                                    text = ilce.ilceAdi,
                                    color = vurguRengi,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    if (secilenSehir != null) {
                        secilenSehir = null
                        geciciSecilenIlce = null
                        secilenIlIlceleri = emptyList()
                    } else {
                        onKapat()
                    }
                }) {
                    Text(text = if (secilenSehir != null) "Geri" else "İptal", color = Color(0xFF94A3B8))
                }

                if (secilenSehir != null) {
                    Button(
                        onClick = {
                            if (geciciSecilenIlce != null) {
                                onSecildi(secilenSehir!!.sehirAdi, geciciSecilenIlce!!.ilceAdi, geciciSecilenIlce!!.diyanetKodu, secilenSehir!!.sehirId)
                            }
                        },
                        enabled = geciciSecilenIlce != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = vurguRengi,
                            disabledContainerColor = vurguRengi.copy(alpha = 0.25f)
                        )
                    ) {
                        Text(text = "Kaydet", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    )
}