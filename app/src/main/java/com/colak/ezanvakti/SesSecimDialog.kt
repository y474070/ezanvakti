package com.colak.ezanvakti

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// Seslerimizi tanımladığımız model
data class SesItem(val id: Int, val isim: String, val dosyaAdi: String)

// Klasöre attığın tüm seslerin listesi
val sesListesi = listOf(
    SesItem(R.raw.sabah_ezani, "Sabah Ezanı", "sabah_ezani"),
    SesItem(R.raw.aksam_ezan, "Akşam Ezanı", "aksam_ezan"),
    SesItem(R.raw.ayasofya_ezan, "Ayasofya Ezanı", "ayasofya_ezan"),
    SesItem(R.raw.ikindi_ezan, "İkindi Ezanı", "ikindi_ezan"),
    SesItem(R.raw.melodi_1, "Melodi 1", "melodi_1"),
    SesItem(R.raw.melodi_2, "Melodi 2", "melodi_2"),
    SesItem(R.raw.melodi_3, "Melodi 3", "melodi_3"),
    SesItem(R.raw.melodi_4, "Melodi 4", "melodi_4"),
    SesItem(R.raw.melodi_5, "Melodi 5", "melodi_5"),
    SesItem(R.raw.melodi_6, "Melodi 6", "melodi_6"),
    SesItem(R.raw.melodi_7, "Melodi 7", "melodi_7"),
    SesItem(R.raw.melodi_8, "Melodi 8", "melodi_8"),
    SesItem(R.raw.melodi_9, "Melodi 9", "melodi_9"),
    SesItem(R.raw.melodi_10, "Melodi 10", "melodi_10"),
    SesItem(R.raw.melodi_11, "Melodi 11", "melodi_11"),
    SesItem(R.raw.melodi_12, "Melodi 12", "melodi_12"),
    SesItem(R.raw.melodi_13, "Melodi 13", "melodi_13"),
    SesItem(R.raw.melodi_14, "Melodi 14", "melodi_14"),
    SesItem(R.raw.melodi_15, "Melodi 15", "melodi_15"),
    SesItem(R.raw.melodi_16, "Melodi 16", "melodi_16"),
    SesItem(R.raw.melodi_17, "Melodi 17", "melodi_17"),
    SesItem(R.raw.melodi_18, "Melodi 18", "melodi_18"),
    SesItem(R.raw.melodi_19, "Melodi 19", "melodi_19"),
    SesItem(R.raw.melodi_20, "Melodi 20", "melodi_20"),
    SesItem(R.raw.melodi_21, "Melodi 21", "melodi_21")
)

@Composable
fun SesSecimDialog(
    gosteriliyorMu: Boolean,
    mevcutSeciliDosyaAdi: String,
    dialogArkaplanRengi: Color, // YENİ: Dışarıdan renk alıyor
    vurguRengi: Color, // YENİ: Ayarlardan seçilen tema rengi — sabit turkuaz yerine bunu kullanıyoruz
    onKapat: () -> Unit,
    onSesKaydet: (SesItem) -> Unit,
    onHepsineUygula: (SesItem) -> Unit
) {
    if (!gosteriliyorMu) return

    val context = LocalContext.current
    var seciliSes by remember {
        mutableStateOf(
            sesListesi.find { it.dosyaAdi == mevcutSeciliDosyaAdi }
                ?: sesListesi.find { it.dosyaAdi == "melodi_19" }
                ?: sesListesi.first()
        )
    }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Gece/Gündüz modunu arka plan renginin koyuluğuna göre otomatik ve kesin tespit ediyoruz
    val isDark = dialogArkaplanRengi != Color.White && dialogArkaplanRengi != Color(0xFFFFFFFF)
    val yaziRengi = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val ikincilYaziRengi = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val cizgiRengi = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.release() }
    }

    Dialog(
        onDismissRequest = {
            mediaPlayer?.release()
            onKapat()
        }
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = dialogArkaplanRengi, // Dinamik renk uygulandı (Gece: Lacivert, Gündüz: Beyaz)
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                Text(
                    text = "Bildirim Sesini Seçin",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = vurguRengi,
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 12.dp)
                )
                Divider(color = cizgiRengi)

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(sesListesi) { ses ->
                        val isSelected = seciliSes.id == ses.id

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    seciliSes = ses
                                    mediaPlayer?.release()
                                    mediaPlayer = MediaPlayer.create(context, ses.id)
                                    mediaPlayer?.start()
                                }
                                .background(if (isSelected) vurguRengi.copy(alpha = 0.15f) else Color.Transparent)
                                .padding(vertical = 14.dp, horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = vurguRengi,
                                    unselectedColor = ikincilYaziRengi
                                )
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            // DÜZELTME: Büyük sistem yazı tipinde uzun ses isimleri
                            // (ör. "Ayasofya Ezanı") satırı taşırabiliyordu. weight(1f)
                            // ile Text artık kalan alanı dolduruyor, 2 satırdan
                            // fazlasında ise "..." ile kesiliyor — taşma yok.
                            Text(
                                text = ses.isim,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) vurguRengi else yaziRengi,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Divider(color = cizgiRengi)

                // DÜZELTME: Önceden "İptal", "Tümüne Uygula" ve "Kaydet" tek bir
                // Row içinde yan yana duruyordu. Ekran küçüldüğünde veya sistem
                // yazı tipi büyütüldüğünde bu üç öğe tek satıra sığmayınca,
                // "Kaydet" butonuna kalan alan neredeyse sıfıra iniyor ve metin
                // harf harf alt alta dizilmeye ("K a y d e t") başlıyordu.
                // Artık "Kaydet" kendi satırına (aşağıya) alınıp tam genişlikte
                // gösteriliyor; üstteki yazılar da maxLines/overflow ile taşma
                // yerine "..." ile kesiliyor. Böylece hiçbir ekran/yazı tipi
                // boyutunda harf harf alt alta dizilme veya üst üste binme olmuyor.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            mediaPlayer?.release()
                            onKapat()
                        }) {
                            Text(
                                text = "İptal",
                                color = ikincilYaziRengi,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        TextButton(onClick = {
                            mediaPlayer?.release()
                            onHepsineUygula(seciliSes)
                            onKapat()
                        }) {
                            Text(
                                text = "Tümüne Uygula",
                                color = Color(0xFFF97316),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            mediaPlayer?.release()
                            onSesKaydet(seciliSes)
                            onKapat()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = vurguRengi)
                    ) {
                        Text(
                            text = "Kaydet",
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}