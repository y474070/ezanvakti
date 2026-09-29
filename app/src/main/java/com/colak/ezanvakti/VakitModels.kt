package com.colak.ezanvakti

import com.google.gson.annotations.SerializedName

// ÖNEMLİ DÜZELTME:
// ezanvakti.emushaf.net (EzanVaktiAPI) JSON alanlarını BÜYÜK harfle başlayarak döndürür:
// "Imsak", "Gunes", "Ogle", "Ikindi", "Aksam", "Yatsi", "MiladiTarihKisa", "HicriTarihKisa" ...
// Gson varsayılan olarak büyük/küçük harf duyarlı eşleştirme yaptığı için,
// alan adları küçük harfle yazılmış eski modelde TÜM değerler null geliyordu
// ve bu yüzden uygulama her zaman "00:00" gösteriyordu.
// @SerializedName ile gerçek JSON anahtarlarını Kotlin alanlarına eşliyoruz.
data class DiyanetVakitModel(
    @SerializedName("Imsak") val imsak: String?,
    @SerializedName("Gunes") val gunes: String?,
    @SerializedName("Ogle") val ogle: String?,
    @SerializedName("Ikindi") val ikindi: String?,
    @SerializedName("Aksam") val aksam: String?,
    @SerializedName("Yatsi") val yatsi: String?,
    @SerializedName("MiladiTarihKisa") val miladiTarihKisa: String?,
    @SerializedName("HicriTarihKisa") val hicriTarihKisa: String?
)