package com.app.plugin.firbase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.app.plugin.R
import com.app.plugin.app.AppPluginManager
import com.app.plugin.until.LogUtil

public class FirHelperMessageService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(LogUtil.TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(LogUtil.TAG, "Message data payload: ${remoteMessage.data}")

            // Check if data needs to be processed by long running job
            if (isLongRunningJob()) {
                // For long-running tasks (10 seconds or more) use WorkManager.
                scheduleJob(remoteMessage.data)
            } else {
                // Handle message within 10 seconds
                handleNow()
            }
        }

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Log.d(LogUtil.TAG, "Fir Message Notification Body: ${it.body}")
            it.body?.let { body -> sendNotification(it.title ?: "", body) }
        }
    }

    private fun isLongRunningJob() = false

    override fun onNewToken(token: String) {
        Log.d(LogUtil.TAG, SensitiveLogSanitizer.fcmTokenEvent("refreshed", token))

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
        sendRegistrationToServer(token)
    }

    private fun scheduleJob(data: Map<String, String>) {
        // [START dispatch_job]
        val dataBuilder = androidx.work.Data.Builder()
        for ((key, value) in data) {
            dataBuilder.putString(key, value)
        }
        val work = OneTimeWorkRequest.Builder(FirHelperWorker::class.java)
            .setInputData(dataBuilder.build())
            .build()
        WorkManager.getInstance(this).beginWith(work).enqueue()
        // [END dispatch_job]
    }

    private fun handleNow() {
        Log.d(LogUtil.TAG, "Fir Short lived task is done.")
    }

    private fun sendRegistrationToServer(token: String?) {
        Log.d(LogUtil.TAG, SensitiveLogSanitizer.fcmTokenEvent("send requested", token))
    }

    private fun sendNotification(title: String, messageBody: String) {
        // BUG-09 FIX: dùng ID động để các notification không ghi đè lên nhau
        val notificationId = System.currentTimeMillis().toInt()
        val requestCode = notificationId
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val intent = launchIntent ?: Intent()
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val channelId = getString(R.string.appplugin_default_notification_channel_id)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        var tTitl = title
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(tTitl)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel   (
                channelId,
                "Notifications",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}