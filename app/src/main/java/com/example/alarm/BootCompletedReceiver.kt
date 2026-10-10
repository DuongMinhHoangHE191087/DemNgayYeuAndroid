package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Dedicated receiver for system boot completion and clock or timezone changes.
 *
 * Exported to receive system broadcasts (BOOT_COMPLETED, QUICKBOOT_POWERON, TIMEZONE_CHANGED, TIME_SET),
 * protected by android.permission.RECEIVE_BOOT_COMPLETED.
 * All internal reminder and anniversary alarms are kept in a separate,
 * non-exported receiver (ReminderAlarmReceiver) to prevent unauthorized invocation.
 */
class BootCompletedReceiver : BroadcastReceiver() {

  companion object {
    private const val TAG = "BootCompletedReceiver"

    // Intent.ACTION_TIME_SET does not exist in the SDK; the literal is the platform broadcast.
    internal fun isRescheduleAction(action: String?): Boolean =
      action == Intent.ACTION_BOOT_COMPLETED ||
        action == "android.intent.action.QUICKBOOT_POWERON" ||
        action == Intent.ACTION_TIMEZONE_CHANGED ||
        action == "android.intent.action.TIME_SET"
  }

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action
    Log.d(TAG, "onReceive triggered with action: $action")

    if (isRescheduleAction(action)) {
      val pendingResult = goAsync()
      CoroutineScope(Dispatchers.IO).launch {
        try {
          Log.d(TAG, "Rescheduling all anniversary alarms from Room DB ($action)...")
          val dao = AppDatabase.getDatabase(context).inLoveDao()
          AlarmNotificationScheduler.scheduleAllAnniversariesFromDb(context, dao)
        } catch (e: Exception) {
          Log.e(TAG, "Error rescheduling alarms on boot: ${e.message}", e)
        } finally {
          pendingResult.finish()
        }
      }
    }
  }
}
