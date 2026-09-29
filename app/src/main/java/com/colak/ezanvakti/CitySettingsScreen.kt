package com.colak.ezanvakti

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CitySettingsScreen(onTamamTiklandi: () -> Unit, onSehirSecTiklandi: () -> Unit = {}) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE)

    // SharedPreferences'tan kayıtlı şehir ve ilçe bilgilerini çekiyoruz
    val kaydedilenIl = sharedPreferences.getString("SECILEN_IL", "Konya") ?: "Konya"
    val kaydedilenIlce = sharedPreferences.getString("SECILEN_ILCE", "Karatay") ?: "Karatay"
    val gosterilecekSehir = "$kaydedilenIl, $kaydedilenIlce"

    // Bildirim anahtarlarının durumlarını SharedPreferences'tan okuma
    var sabahVaktinde by remember { mutableStateOf(sharedPreferences.getBoolean("SABAH_IMSAKTE_OKU", true)) }
    var sabahOncesi by remember { mutableStateOf(true) }
    var gunesOncesi by remember { mutableStateOf(true) }
    var ogleVaktinde by remember { mutableStateOf(true) }
    var ogleOncesi by remember { mutableStateOf(true) }
    var ikindiVaktinde by remember { mutableStateOf(true) }
    var ikindiOncesi by remember { mutableStateOf(true) }
    var aksamVaktinde by remember { mutableStateOf(true) }
    var aksamOncesi by remember { mutableStateOf(true) }
    var yatsiVaktinde by remember { mutableStateOf(true) }
    var yatsiOncesi by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(top = 24.dp)
    ) {
        // --- ÜST KISIM: DİNAMİK BÜYÜK ŞEHİR İSMİ ---
        Text(
            text = kaydedilenIl,
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // --- ALT KISIM: BEYAZ KIVRIMLI KART ALANI ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Şehir Seçim Kutusu (Tıklanabilir hale getirildi)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSehirSecTiklandi() },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = gosterilecekSehir,
                                color = Color(0xFF334155),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "Seç",
                                tint = Color(0xFF64748B)
                            )
                        }
                    }

                    // 2. Hesaplama Yöntemi Başlığı
                    Text(
                        text = "Hesaplama Yöntemi",
                        color = Color(0xFFF97316),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Diyanet Takvimi",
                        color = Color(0xFF0F172A),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Vakit Önizleme Sabit Gösterge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        VakitSaatGosterge("İmsak", "04:06")
                        VakitSaatGosterge("Güneş", "05:43")
                        VakitSaatGosterge("Öğle", "13:01")
                        VakitSaatGosterge("İkindi", "16:51")
                        VakitSaatGosterge("Akşam", "20:09")
                        VakitSaatGosterge("Yatsı", "21:40")
                    }

                    Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                    // 3. Bildirim Ayarları Başlığı
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bildirim Ayarları",
                            color = Color(0xFFF97316),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Text(text = "Vaktinde", color = Color(0xFF0D9488), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Vakit Öncesi", color = Color(0xFF0D9488), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Bildirim Satırları
                    BildirimAyarSatiri("Sabah", sabahVaktinde, { sabahVaktinde = it }, sabahOncesi, { sabahOncesi = it })
                    BildirimAyarSatiriTek("Güneş", gunesOncesi, { gunesOncesi = it })
                    BildirimAyarSatiri("Öğle", ogleVaktinde, { ogleVaktinde = it }, ogleOncesi, { ogleOncesi = it })
                    BildirimAyarSatiri("İkindi", ikindiVaktinde, { ikindiVaktinde = it }, ikindiOncesi, { ikindiOncesi = it })
                    BildirimAyarSatiri("Akşam", aksamVaktinde, { aksamVaktinde = it }, aksamOncesi, { aksamOncesi = it })
                    BildirimAyarSatiri("Yatsı", yatsiVaktinde, { yatsiVaktinde = it }, yatsiOncesi, { yatsiOncesi = it })
                }

                // --- EN ALT: TAMAM BUTONU ---
                Button(
                    onClick = {
                        // Ayarlar kaydedilirken tercihleri güncelleyebiliriz
                        sharedPreferences.edit().putBoolean("SABAH_IMSAKTE_OKU", sabahVaktinde).apply()
                        onTamamTiklandi()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) {
                    Text(text = "TAMAM", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun VakitSaatGosterge(isim: String, saat: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = isim, color = Color(0xFF64748B), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = saat, color = Color(0xFF0F172A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BildirimAyarSatiri(vakitAdi: String, switch1Durum: Boolean, switch1Degis: (Boolean) -> Unit, switch2Durum: Boolean, switch2Degis: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = vakitAdi, color = Color(0xFF334155), fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = switch1Durum, onCheckedChange = switch1Degis, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF0D9488)))
            Switch(checked = switch2Durum, onCheckedChange = switch2Degis, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF0D9488)))
        }
    }
}

@Composable
fun BildirimAyarSatiriTek(vakitAdi: String, switchDurum: Boolean, switchDegis: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = vakitAdi, color = Color(0xFF334155), fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(48.dp))
            Switch(checked = switchDurum, onCheckedChange = switchDegis, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF0D9488)))
        }
    }
}