package com.colak.ezanvakti

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import java.util.Calendar

class EzanAlarmReceiver : BroadcastReceiver() {

    companion object {
        private var aktifMediaPlayer: MediaPlayer? = null
        private var aktifWakeLock: PowerManager.WakeLock? = null
        private var aktifBildirimId: Int? = null

        private fun sesiSusturVeTemizle() {
            try {
                aktifMediaPlayer?.let {
                    if (it.isPlaying) it.stop()
                    it.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            aktifMediaPlayer = null

            try {
                aktifWakeLock?.let { if (it.isHeld) it.release() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            aktifWakeLock = null
        }

        fun uygulamayaGirinceSustur(context: Context) {
            sesiSusturVeTemizle()
            aktifBildirimId?.let { id ->
                try {
                    val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notifManager.cancel(id)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            aktifBildirimId = null
        }
    }

    override fun onReceive(context: Context, intent: Intent) {

        // --- GİZLİ GÖREV: 45 DAKİKA SONRA SESİ AÇMA İŞLEMİ ---
        val islem = intent.getStringExtra("ISLEM")
        if (islem == "SESI_AC") {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            try {
                val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (notifManager.isNotificationPolicyAccessGranted) {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }
            } catch (e: Exception) { e.printStackTrace() }
            return
        }

        if (islem == "SUSTUR") {
            sesiSusturVeTemizle()
            val bildirimId = intent.getIntExtra("BILDIRIM_ID", -1)
            if (bildirimId != -1) {
                val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notifManager.cancel(bildirimId)
            }
            return
        }

        if (islem == "GUNLUK_YENILE") {
            EzanAlarmManager.tumAlarmlariSessizceYenidenKur(context)
            return
        }

        val vakitAdi = intent.getStringExtra("VAKIT_ADI") ?: "Ezan Vakti"
        val dosyaAdi = intent.getStringExtra("DOSYA_ADI") ?: "melodi_19"

        // =========================================================================
        // KERAHAT / GÜNEŞ VAKTİ BİLDİRİMİNİ TAMAMEN ENGELLEME
        // =========================================================================
        // Vakit "Güneş" veya "Kerahat" ise telefona bildirim çubuğu bildirimi,
        // ses veya titreşim gönderilmez. Direkt sistem alarımı iptal edilir.
        val kerahatMi = (vakitAdi == "Güneş" || vakitAdi == "Kerahat")
        if (kerahatMi) {
            return
        }

        val prefs = context.getSharedPreferences("EzanVaktiPrefs", Context.MODE_PRIVATE)

        // =========================================================================
        // KRİTİK KONTROL! - AYARLARDAN BUTON KAPALIYSA ÇALMAYI İPTAL ET
        // =========================================================================
        val aktifMi = when (vakitAdi) {
            "İmsak" -> prefs.getBoolean("IMSAK_AKTIF", true)
            "Sabah" -> prefs.getBoolean("SABAH_AKTIF", true)
            "Öğle" -> prefs.getBoolean("OGLE_AKTIF", true)
            "İkindi" -> prefs.getBoolean("IKINDI_AKTIF", true)
            "Akşam" -> prefs.getBoolean("AKSAM_AKTIF", true)
            "Yatsı" -> prefs.getBoolean("YATSI_AKTIF", true)

            "Sabah Ezanı" -> prefs.getBoolean("SABAH_VAKTINDE", true)
            "Öğle Ezanı" -> prefs.getBoolean("OGLE_VAKTINDE", true)
            "İkindi Ezanı" -> prefs.getBoolean("IKINDI_VAKTINDE", true)
            "Akşam Ezanı" -> prefs.getBoolean("AKSAM_VAKTINDE", true)
            "Yatsı Ezanı" -> prefs.getBoolean("YATSI_VAKTINDE", true)
            else -> true
        }

        if (!aktifMi) {
            return
        }
        // =========================================================================

        val titresimAcik = prefs.getBoolean("TITRESIM_DURUMU", true)
        val namazdaSessiz = prefs.getBoolean("NAMAZ_VAKTINDE_SESSIZ", true)
        val cumaSessiz = prefs.getBoolean("CUMA_SESSIZ", false)

        // --- 1. NAMAZ VE CUMA VAKTİNDE OTOMATİK SESSİZE ALMA ---
        val tamVakitMi = vakitAdi.contains("Ezanı")
        if (tamVakitMi) {
            val isFriday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
            val isOgle = vakitAdi == "Öğle Ezanı"

            val sessizeAlinmali = namazdaSessiz || (cumaSessiz && isFriday && isOgle)

            if (sessizeAlinmali) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                if (notifManager.isNotificationPolicyAccessGranted) {
                    try {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT

                        val unmuteIntent = Intent(context, EzanAlarmReceiver::class.java).apply {
                            putExtra("ISLEM", "SESI_AC")
                        }
                        val pendingUnmute = PendingIntent.getBroadcast(
                            context,
                            999,
                            unmuteIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                        val kirkBesDakika = System.currentTimeMillis() + (45 * 60 * 1000)

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, kirkBesDakika, pendingUnmute)
                        } else {
                            alarmManager.set(AlarmManager.RTC_WAKEUP, kirkBesDakika, pendingUnmute)
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }

        // --- 2. BİLDİRİM OLUŞTURMA ---
        val channelId = "ezan_vakti_sessiz_kanal_v3"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val bildirimId = System.currentTimeMillis().toInt()

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            bildirimId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val susturIntent = Intent(context, EzanAlarmReceiver::class.java).apply {
            putExtra("ISLEM", "SUSTUR")
            putExtra("BILDIRIM_ID", bildirimId)
        }
        val susturPendingIntent = PendingIntent.getBroadcast(
            context,
            bildirimId,
            susturIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Ezan Vakti Hatırlatıcıları",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val bildirimMetni = if (tamVakitMi) {
            "$vakitAdi vakti girdi."
        } else {
            "$vakitAdi için vaktin yaklaşıyor."
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Ezan Vakti Hatırlatıcısı")
            .setContentText(bildirimMetni)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setTimeoutAfter(5 * 60 * 1000)
            .setSilent(true)
            .addAction(0, "Sustur", susturPendingIntent)
            .setDeleteIntent(susturPendingIntent)
            .build()

        notificationManager.notify(bildirimId, notification)

        // --- 3. SADECE UYGULAMAYA ÖZEL TİTREŞİM ---
        if (titresimAcik) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val pattern = longArrayOf(0, 500, 500, 500, 500, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }

        // --- 4. SEÇİLEN SESİ VE WAKE_LOCK ÇALMA ---
        sesiSusturVeTemizle()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringerMode = audioManager.ringerMode
        val aramaVarMi = audioManager.mode == AudioManager.MODE_IN_CALL || audioManager.mode == AudioManager.MODE_IN_COMMUNICATION

        val sesCalinmali = (ringerMode == AudioManager.RINGER_MODE_NORMAL) && !aramaVarMi

        if (sesCalinmali) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "EzanVakti:AlarmWakeLock"
            )
            wakeLock.acquire(10 * 60 * 1000L)
            aktifWakeLock = wakeLock
            aktifBildirimId = bildirimId

            val resId = context.resources.getIdentifier(dosyaAdi, "raw", context.packageName)
            if (resId != 0) {
                try {
                    val mediaPlayer = MediaPlayer()
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    mediaPlayer.setAudioAttributes(audioAttributes)

                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    val curVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    val sesOrani = if (maxVol > 0) curVol.toFloat() / maxVol.toFloat() else 1f
                    mediaPlayer.setVolume(sesOrani, sesOrani)

                    val afd = context.resources.openRawResourceFd(resId)
                    mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    mediaPlayer.prepare()
                    mediaPlayer.start()
                    aktifMediaPlayer = mediaPlayer

                    mediaPlayer.setOnCompletionListener { mp ->
                        mp.release()
                        if (aktifMediaPlayer == mp) aktifMediaPlayer = null
                        if (wakeLock.isHeld) {
                            wakeLock.release()
                        }
                        if (aktifWakeLock === wakeLock) aktifWakeLock = null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                    if (aktifWakeLock === wakeLock) aktifWakeLock = null
                }
            } else {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
                if (aktifWakeLock === wakeLock) aktifWakeLock = null
            }
        }
    }
}