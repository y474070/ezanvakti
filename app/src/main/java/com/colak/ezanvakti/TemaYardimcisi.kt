package com.colak.ezanvakti

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// YENİ ÖZELLİK: Uygulama genelinde tek bir yerden yönetilen vurgu (accent) rengi.
// Kullanıcı Ayarlar'dan 5 hazır renk arasından seçim yapabiliyor; her ekran
// (HomeScreen, SettingsScreen, QiblaCompassScreen) bu renk paletini SharedPreferences
// üzerinden okuyor, böylece tek bir seçim tüm uygulamaya anında yansıyor.
enum class RenkTemasi(val kodAdi: String, val gosterilenIsim: String, val renk: Color) {
    ALTIN("altin", "Altın", Color(0xFFF59E0B)),
    YESIL("yesil", "Yeşil", Color(0xFF10B981)),
    MAVI("mavi", "Mavi", Color(0xFF3B82F6)),
    MOR("mor", "Mor", Color(0xFF8B5CF6)),
    TOZ_PEMBE("toz_pembe", "Toz Pembe", Color(0xFFE5989B)),
    // DÜZELTME: Saf siyah (#000000) seçilince, bu rengi kullanan yazı/ikonlar
    // (örn. Hicri tarih) koyu arka planda tamamen kayboluyordu (siyah üstüne
    // siyah). Okunabilir kalması için koyu gri kullanılıyor — arka plan
    // katmanları da (kartArkaPlaniniHesapla vb.) bu rengi kullandığı için,
    // eski lacivert görünüme de artık daha yakın duruyor.
    SIYAH("siyah", "Siyah", Color(0xFF64748B));

    companion object {
        fun kodAdindanBul(kodAdi: String?): RenkTemasi {
            return entries.find { it.kodAdi == kodAdi } ?: ALTIN
        }
    }
}

object TemaYardimcisi {
    private const val ANAHTAR = "RENK_TEMASI"

    fun vurguRenginiOku(context: Context): Color {
        val prefs = context.getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE)
        val kodAdi = prefs.getString(ANAHTAR, RenkTemasi.ALTIN.kodAdi)
        return RenkTemasi.kodAdindanBul(kodAdi).renk
    }

    fun seciliTemayiOku(context: Context): RenkTemasi {
        val prefs = context.getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE)
        return RenkTemasi.kodAdindanBul(prefs.getString(ANAHTAR, RenkTemasi.ALTIN.kodAdi))
    }

    fun temayiKaydet(context: Context, tema: RenkTemasi) {
        val prefs = context.getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString(ANAHTAR, tema.kodAdi).apply()
    }

    // YENİ ÖZELLİK: Uygulamadaki TÜM sabit lacivert (#0F172A/#1E293B/#131C2E
    // gibi) arka planların yerini alan, seçili tema rengine göre üretilen
    // koyu/açık ton. Kartlar, dialoglar, sayfa arka planları — hepsi artık
    // bu iki fonksiyondan birini çağırarak rengini tema seçiminden alıyor.

    // Kart/diyalog arka planı (örn. "Konum ve Veriler" kutusu, dialoglar).
    fun kartArkaPlaniniHesapla(vurguRengi: Color, isDarkMode: Boolean): Color {
        return if (isDarkMode) lerp(vurguRengi, Color.Black, 0.86f)
        else lerp(vurguRengi, Color.White, 0.90f)
    }

    // Tüm sayfanın arka plan katmanı (en dipteki koyu/açık ton).
    fun sayfaArkaPlaniniHesapla(vurguRengi: Color, isDarkMode: Boolean): Color {
        return if (isDarkMode) lerp(vurguRengi, Color.Black, 0.93f)
        else lerp(vurguRengi, Color.White, 0.94f)
    }
}