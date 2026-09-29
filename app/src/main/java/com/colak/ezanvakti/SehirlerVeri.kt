package com.colak.ezanvakti

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

data class IlceModel(val ilceAdi: String, val diyanetKodu: String)
data class IlModel(val ilAdi: String, val ilceler: List<IlceModel>)

private const val TURKIYE_ULKE_KODU = "2"
private const val TAG = "EzanVaktiDebug"

object JsonYardimcisi {

    data class SehirBilgisi(val sehirId: String, val sehirAdi: String)

    data class IlcelerSonucu(
        val ilceler: List<IlceModel>,
        val kaynakCanliMi: Boolean
    )

    private val inMemoryIlceCache = mutableMapOf<String, List<IlceModel>>()

    private fun anahtarDeneVeAl(map: Map<String, Any>, adaylar: List<String>): String? {
        for (aday in adaylar) {
            val deger = map[aday]
            if (deger != null) {
                val metin = deger.toString().trim()
                if (metin.isNotEmpty() && metin != "null") return metin
            }
        }
        return null
    }

    private fun sehirIdCikar(map: Map<String, Any>): String? =
        anahtarDeneVeAl(map, listOf("SehirID", "sehirID", "SehirId", "sehirId", "Id", "id"))

    private fun sehirAdiCikar(map: Map<String, Any>): String? =
        anahtarDeneVeAl(map, listOf("SehirAdi", "sehirAdi", "SehirAdı", "Adi", "adi", "Name", "name"))

    private fun ilceIdCikar(map: Map<String, Any>): String? =
        anahtarDeneVeAl(map, listOf("IlceID", "ilceID", "IlceId", "ilceId", "Id", "id"))

    private fun ilceAdiCikar(map: Map<String, Any>): String? =
        anahtarDeneVeAl(map, listOf("IlceAdi", "ilceAdi", "İlçeAdi", "Adi", "adi", "Name", "name"))

    private fun normalize(s: String): String = s.trim().lowercase(java.util.Locale.ROOT)

    // DÜZELTME: Tek bir isteğin çok uzun sürüp kullanıcıyı bekletmemesi için
    // deneme sayısı ve gecikme azaltıldı (önceden 2 deneme x 15sn zaman aşımı
    // = en kötü durumda 30sn'ye kadar bekleme oluyordu).
    private suspend fun <T> tekrarDenemeli(etiket: String, deneme: Int = 2, gecikmeMs: Long = 250L, blok: suspend () -> T): T? {
        repeat(deneme) { index ->
            try {
                val sonuc = blok()
                Log.d(TAG, "[$etiket] BAŞARILI (deneme ${index + 1})")
                return sonuc
            } catch (e: Exception) {
                Log.e(TAG, "[$etiket] HATA (deneme ${index + 1}/$deneme): ${e.javaClass.simpleName} - ${e.message}")
                if (index < deneme - 1) delay(gecikmeMs)
            }
        }
        Log.e(TAG, "[$etiket] TÜM DENEMELER BAŞARISIZ")
        return null
    }

    suspend fun getSehirlerCanli(context: Context): Pair<List<SehirBilgisi>, Boolean> {
        val sehirlerHam = tekrarDenemeli("SEHIRLER") {
            withContext(Dispatchers.IO) { EzanApiService.instance.getSehirlerHam(TURKIYE_ULKE_KODU) }
        }

        Log.d(TAG, "SEHIRLER ham cevap boyutu: ${sehirlerHam?.size ?: "null"}")
        if (!sehirlerHam.isNullOrEmpty()) {
            Log.d(TAG, "SEHIRLER ilk kayıt örneği (alan adlarını görmek için): ${sehirlerHam.first()}")
        }

        if (sehirlerHam.isNullOrEmpty()) {
            Log.w(TAG, "SEHIRLER canlı boş/null geldi -> statik dosyaya düşülüyor")
            val statik = getSehirlerListesiStatik(context)
            return Pair(statik.map { SehirBilgisi(sehirId = "", sehirAdi = it.ilAdi) }, false)
        }

        val liste = sehirlerHam.mapNotNull { sehir ->
            val id = sehirIdCikar(sehir)
            val adi = sehirAdiCikar(sehir)
            if (id == null || adi == null) {
                Log.w(TAG, "SEHIRLER bir kayıt ayrıştırılamadı (id=$id, adi=$adi). Ham veri: $sehir")
            }
            if (id != null && adi != null) SehirBilgisi(sehirId = id, sehirAdi = adi) else null
        }

        Log.d(TAG, "SEHIRLER ayrıştırılan il sayısı: ${liste.size} / ham: ${sehirlerHam.size}")
        val konyaVarMi = liste.any { normalize(it.sehirAdi) == normalize("Konya") }
        Log.d(TAG, "SEHIRLER listesinde 'Konya' var mı: $konyaVarMi")

        return if (liste.isNotEmpty()) Pair(liste, true) else {
            Log.w(TAG, "SEHIRLER hiçbir kayıt ayrıştırılamadı -> statik dosyaya düşülüyor")
            val statik = getSehirlerListesiStatik(context)
            Pair(statik.map { SehirBilgisi(sehirId = "", sehirAdi = it.ilAdi) }, false)
        }
    }

    suspend fun getIlcelerCanli(context: Context, sehir: SehirBilgisi): IlcelerSonucu {
        inMemoryIlceCache[sehir.sehirAdi]?.let {
            Log.d(TAG, "ILCELER '${sehir.sehirAdi}' bellek önbelleğinden döndü (${it.size} ilçe)")
            return IlcelerSonucu(it, kaynakCanliMi = true)
        }

        Log.d(TAG, "ILCELER '${sehir.sehirAdi}' isteniyor, sehirId='${sehir.sehirId}'")

        if (sehir.sehirId.isNotBlank()) {
            val ilcelerHam = tekrarDenemeli("ILCELER-${sehir.sehirAdi}") {
                withContext(Dispatchers.IO) { EzanApiService.instance.getIlcelerHam(sehir.sehirId) }
            }

            Log.d(TAG, "ILCELER '${sehir.sehirAdi}' ham cevap boyutu: ${ilcelerHam?.size ?: "null"}")
            if (!ilcelerHam.isNullOrEmpty()) {
                Log.d(TAG, "ILCELER '${sehir.sehirAdi}' ilk kayıt örneği: ${ilcelerHam.first()}")
            }

            val ilceler = ilcelerHam?.mapNotNull { ilce ->
                val id = ilceIdCikar(ilce)
                val adi = ilceAdiCikar(ilce)
                if (id == null || adi == null) {
                    Log.w(TAG, "ILCELER '${sehir.sehirAdi}' bir kayıt ayrıştırılamadı (id=$id, adi=$adi). Ham veri: $ilce")
                }
                if (id != null && adi != null) IlceModel(ilceAdi = adi, diyanetKodu = id) else null
            }

            if (!ilceler.isNullOrEmpty()) {
                Log.d(TAG, "ILCELER '${sehir.sehirAdi}' CANLI başarılı: ${ilceler.size} ilçe")
                inMemoryIlceCache[sehir.sehirAdi] = ilceler
                return IlcelerSonucu(ilceler, kaynakCanliMi = true)
            } else {
                Log.w(TAG, "ILCELER '${sehir.sehirAdi}' canlı çekildi ama ayrıştırılamadı/boş")
            }
        } else {
            Log.w(TAG, "ILCELER '${sehir.sehirAdi}' için sehirId boş, canlı istek atlanıyor")
        }

        val statikIl = getSehirlerListesiStatik(context).find { normalize(it.ilAdi) == normalize(sehir.sehirAdi) }
        val yedekIlceler = statikIl?.ilceler ?: emptyList()
        Log.w(TAG, "ILCELER '${sehir.sehirAdi}' statik yedeğe düşüldü, bulunan ilçe sayısı: ${yedekIlceler.size}")
        if (yedekIlceler.isNotEmpty()) {
            inMemoryIlceCache[sehir.sehirAdi] = yedekIlceler
        }
        return IlcelerSonucu(yedekIlceler, kaynakCanliMi = false)
    }

    fun getSehirlerListesiStatik(context: Context): List<IlModel> {
        return try {
            val jsonString = context.assets.open("sehirler.json").bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<IlModel>>() {}.type
            Gson().fromJson<List<IlModel>>(jsonString, listType) ?: emptyList()
        } catch (ioException: IOException) {
            ioException.printStackTrace()
            emptyList()
        } catch (jsonException: JsonSyntaxException) {
            jsonException.printStackTrace()
            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * DÜZELTME (YENİ): Konum tespitinden (Geocoder) gelen il/ilçe İSİMLERİNİ,
     * gerçek Diyanet ilçe KODUNA çevirir. Önceden konum tespiti sadece
     * "SECILEN_IL"/"SECILEN_ILCE" metnini kaydediyor, "SECILEN_ILCE_KODU"'nu
     * HİÇ ayarlamıyordu — bu yüzden konuma izin verilse ve "bulundu" mesajı
     * çıksa bile vakitler asla çekilemiyordu (kod her zaman boş kalıyordu).
     *
     * Eşleştirme sırası: önce ilçe adı birebir eşleşen, sonra adı içeren/
     * içinde geçen, bulunamazsa o ilin "Merkez" ilçesi kullanılır. Hiçbiri
     * yoksa null döner (çağıran taraf kullanıcıyı manuel seçime yönlendirmeli).
     */
    /**
     * DÜZELTME (YENİ): Konum tespitinden (Geocoder) gelen il/ilçe İSİMLERİNİ,
     * gerçek Diyanet ilçe KODUNA çevirir. Önceden konum tespiti sadece
     * "SECILEN_IL"/"SECILEN_ILCE" metnini kaydediyor, "SECILEN_ILCE_KODU"'nu
     * HİÇ ayarlamıyordu — bu yüzden konuma izin verilse ve "bulundu" mesajı
     * çıksa bile vakitler asla çekilemiyordu (kod her zaman boş kalıyordu).
     *
     * Eşleştirme sırası: önce ilçe adı birebir eşleşen, sonra adı içeren/
     * içinde geçen, bulunamazsa o ilin "Merkez" ilçesi kullanılır. Hiçbiri
     * yoksa null döner (çağıran taraf kullanıcıyı manuel seçime yönlendirmeli).
     *
     * YENİ ÖZELLİK: Artık sadece ilçe kodu değil, o ilçenin bağlı olduğu İLİN
     * kendi kodu (sehirKodu) da dönüyor — Bayram Namazı API'si (/bayram-namazi/
     * {SEHIR_KODU}) ilçe değil ŞEHİR seviyesinde kod istiyor.
     */
    data class KonumKoduSonucu(val ilceKodu: String, val sehirKodu: String)

    suspend fun konumdanKoduCoz(context: Context, ilAdi: String, ilceAdi: String): KonumKoduSonucu? {
        val (sehirler, _) = getSehirlerCanli(context)
        val normIl = normalize(ilAdi)
        val sehir = sehirler.find { normalize(it.sehirAdi) == normIl }
            ?: sehirler.find { normalize(it.sehirAdi).contains(normIl) || normIl.contains(normalize(it.sehirAdi)) }
            ?: return null

        val sonuc = getIlcelerCanli(context, sehir)
        val normIlce = normalize(ilceAdi)

        val ilceModel = sonuc.ilceler.find { normalize(it.ilceAdi) == normIlce }
            ?: sonuc.ilceler.find { normalize(it.ilceAdi).contains(normIlce) || normIlce.contains(normalize(it.ilceAdi)) }
            ?: sonuc.ilceler.find { normalize(it.ilceAdi) == "merkez" || normalize(it.ilceAdi) == normIl }
            ?: sonuc.ilceler.firstOrNull()

        val ilceKodu = ilceModel?.diyanetKodu ?: return null
        // sehir.sehirId statik yedekte boş olabilir (çevrimdışıyken) — bu
        // durumda Bayram Namazı özelliği o gün için sessizce atlanır, uygulama
        // çökmez.
        return KonumKoduSonucu(ilceKodu = ilceKodu, sehirKodu = sehir.sehirId)
    }
}