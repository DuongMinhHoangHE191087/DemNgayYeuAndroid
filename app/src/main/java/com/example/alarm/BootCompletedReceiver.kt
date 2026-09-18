package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Dedicated receiver for system boot completion events.
 *
 * Exported to receive system broadcast ACTION_BOOT_COMPLETED, protected
 * by android.permission.RECEIVE_BOOT_COMPLETED.
 * All internal reminder and anniversary alarms are kept in a separate,
 * non-exported receiver (ReminderAlarmReceiver) to prevent unauthorized invocation.
 */
class BootCompletedReceiver : BroadcastReceiver() {

  companion object {
    private const val TAG = "BootCompletedReceiver"
  }

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action
    Log.d(TAG, "onReceive triggered with action: $action")

    if (action == Intent.ACTION_BOOT_COMPLETED ||
      action == "android.intent.action.QUICKBOOT_POWERON"
    ) {
      val pendingResult = goAsync()
      CoroutineScope(Dispatchers.IO).launch {
        try {
          Log.d(TAG, "Device booted. Rescheduling all anniversary alarms from Room DB...")
          AlarmNotificationScheduler.scheduleAllAnniversariesFromDb(context)
          AlarmNotificationScheduler.scheduleDailyMorningCheck(context)
        } catch (e: Exception) {
          Log.e(TAG, "Error rescheduling alarms on boot: ${e.message}", e)
        } finally {
          pendingResult.finish()
        }
      }
    }
  }
}
