package com.colak.ezanvakti

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // DÜZELTME: Önceki sürüm burada context.startActivity(...) çağırıyordu
        // — bu, telefon her açıldığında (ya da uygulama güncellendiğinde)
        // uygulamanın kullanıcı hiçbir şey yapmadan EKRANA GELMESİNE
        // (kendi kendine açılmasına) sebep oluyordu. Artık startActivity
        // KULLANILMIYOR.
        //
        // Android, cihaz yeniden başlatıldığında AlarmManager ile kurulmuş
        // TÜM alarmları otomatik siler; bu yüzden alarmların yeniden
        // kurulması hâlâ gerekli — ama bunu UI açmadan, önbellekteki son
        // vakit verisiyle SESSİZCE yapıyoruz (EzanAlarmManager.tumAlarmlariSessizceYenidenKur).
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == Intent.ACTION_PACKAGE_REPLACED
        ) {
            EzanAlarmManager.tumAlarmlariSessizceYenidenKur(context)
            // YENİ ÖZELLİK: Vakit alarmları önceden sadece uygulama
            // açıldığında/telefon açılışında yenileniyordu — günler geçtikçe
            // (uygulama tekrar açılmadan) hiç güncellenmiyordu. Artık her
            // gece yarısı kendini otomatik yenileyen "günlük tazeleme
            // alarmı" da burada (yeniden) kuruluyor.
            EzanAlarmManager.gunlukYenilemeAlarmiKur(context)
        }
    }
}