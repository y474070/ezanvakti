package com.colak.ezanvakti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EzanVaktiSplashScreen() {
    // DÜZELTME: Hilalin/logonun çerçevesi ve alt yazı artık sabit yeşil
    // değil — Ayarlar'dan seçilen tema rengini kullanıyor.
    val context = LocalContext.current
    val vurguRengi = TemaYardimcisi.vurguRenginiOku(context)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.mevlana_bg),
            contentDescription = "Splash Arka Plan",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF1E293B),
                modifier = Modifier.size(90.dp),
                shadowElevation = 20.dp,
                border = androidx.compose.foundation.BorderStroke(2.dp, vurguRengi)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.cami),
                        contentDescription = "Cami Logosu",
                        modifier = Modifier.size(85.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Ezan Vaktim",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Kıymetli Vakitler",
                color = vurguRengi,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}