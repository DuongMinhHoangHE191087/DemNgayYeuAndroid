package com.app.plugin.ads

import android.app.Activity
import android.app.Dialog

/**
 * Chốt chung cho ba lớp native full (AdMob, Meta, MAX) trước khi show dialog. `BadTokenException`
 * và `IllegalStateException` từ `Dialog.show()` đều là RuntimeException của một cửa sổ đã chết:
 * bắt ở đây, trả false, để lớp gọi báo `onAdShowFail` thay vì làm app sập.
 */
internal object NtFullDialogHost {
    fun isUsable(activity: Activity?): Boolean =
        activity != null && !activity.isFinishing && !activity.isDestroyed

    fun show(dialog: Dialog, onFail: (Throwable) -> Unit): Boolean = try {
        dialog.show()
        true
    } catch (e: RuntimeException) {
        onFail(e)
        false
    }
}
