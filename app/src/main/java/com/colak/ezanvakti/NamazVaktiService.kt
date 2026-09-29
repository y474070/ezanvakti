package com.colak.ezanvakti

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface EzanApiService {
    @GET("vakitler/{ilceKodu}")
    suspend fun getVakitler(
        @Path("ilceKodu") ilceKodu: String
    ): List<DiyanetVakitModel>

    @GET("sehirler/{ulkeKodu}")
    suspend fun getSehirlerHam(
        @Path("ulkeKodu") ulkeKodu: String
    ): List<Map<String, Any>>

    @GET("ilceler/{sehirKodu}")
    suspend fun getIlcelerHam(
        @Path("sehirKodu") sehirKodu: String
    ): List<Map<String, Any>>

    // YENİ ÖZELLİK: Bayram Namazı saatleri — bir şehrin TÜM ilçeleri için
    // (ilçe kodu değil, ŞEHİR kodu ile sorgulanıyor). Alan adlarını
    // doğrulayamadığım için (SehirlerVeri.kt'deki gibi) ham Map olarak
    // dönüyor, esnek anahtar çözümlemesiyle okunacak.
    @GET("bayram-namazi/{sehirKodu}")
    suspend fun getBayramNamaziHam(
        @Path("sehirKodu") sehirKodu: String
    ): List<Map<String, Any>>

    companion object {
        private const val BASE_URL = "https://ezanvakti.emushaf.net/"

        // DÜZELTME: 81 il için art arda çok sayıda istek atıldığında (şehir listesi
        // canlı çekilirken) varsayılan zaman aşımı süreleri bazı isteklerde yetersiz
        // kalıp o ilin tamamen listeden düşmesine yol açıyordu (ör. Konya kayboluyordu).
        // Bağlantı/okuma zaman aşımını makul bir süreye çıkarıyoruz.
        private val okHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .build()
        }

        val instance: EzanApiService by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(EzanApiService::class.java)
        }
    }
}