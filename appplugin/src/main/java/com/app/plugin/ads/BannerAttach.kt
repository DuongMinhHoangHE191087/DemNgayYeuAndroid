package com.app.plugin.ads

import android.view.View
import android.view.ViewGroup
import com.app.plugin.until.LogUtil

/**
 * Gắn banner hay rect vào container. Khi Activity chủ bị huỷ, `AdsHelper.clearActivity()` đã gỡ banner,
 * rect và native collapsible khỏi container của nó bằng [detach] (từ Task 7a). View trong cache vẫn có
 * thể còn cha lúc gắn lại: gỡ ở đó lỗi, hay app gắn sang container khác khi container cũ còn sống. Mà
 * `addView` một view đang có cha thì ném IllegalStateException, nên gỡ khỏi cha cũ trước ở đây.
 * Trả về true khi view đã nằm trong [parent] sau lời gọi.
 */
internal object BannerAttach {
    fun attach(parent: ViewGroup, view: View): Boolean {
        val old = view.parent
        if (old === parent) return true
        if (old is ViewGroup) {
            try {
                old.removeView(view)
            } catch (e: Exception) {
                LogUtil.logE("banner detach failed: ${e.message}")
                return false
            }
        }
        if (view.parent != null) {
            if (old is ViewGroup) {
                LogUtil.logE("banner still attached after detach, skip addView")
            } else {
                LogUtil.logE("banner parent is not a ViewGroup (${old?.javaClass?.simpleName}), skip addView")
            }
            return false
        }
        parent.addView(view)
        return true
    }

    /** Gỡ view khỏi container đang giữ nó (nếu có). Dùng khi Activity chủ bị huỷ: view quảng cáo sống
     *  lâu hơn Activity (context Application) và còn trỏ vào container cũ thì Activity cũ không được GC. */
    fun detach(view: View?) {
        val old = view?.parent as? ViewGroup ?: return
        try {
            old.removeView(view)
        } catch (e: Exception) {
            LogUtil.logE("detach failed: ${e.message}")
        }
    }
}
