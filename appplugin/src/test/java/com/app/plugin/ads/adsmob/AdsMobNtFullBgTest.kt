package com.app.plugin.ads.adsmob

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.RelativeLayout
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsIDDef
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

/**
 * 2.1.0 P2 (M12, R-C1a m3): `AdsMobPlNtFull.addBg4Ad` chỉ là hàm uỷ quyền một dòng sang `AdsMobNtFullBackground`
 * (C1a tách file). Đỏ khi: delegator thành no-op hay trỏ nhầm sang `hideBlackSafeAreaView`.
 */
class AdsMobNtFullBgTest {
    @Test
    fun `NB1 addBg4Ad cua placement uy quyen sang background va hien lop phu safe-area`() {
        val pl = AdsMobPlNtFull(mock(Context::class.java), mock(AdsBase::class.java), AdsIDDef.AdmobFullNt, "full_2_default")
        val background = pl.javaClass.getDeclaredField("background").apply { isAccessible = true }.get(pl)
        val overlay = mock(RelativeLayout::class.java)
        background.javaClass.getDeclaredField("parentBlackSafeAreaView").apply { isAccessible = true }.set(background, overlay)
        // sSafeTop = 0: bỏ nhánh đọc cutout của cửa sổ (đã có số đo), chỉ còn nhánh "lớp phủ có sẵn → hiện lại".
        background.javaClass.getDeclaredField("sSafeTop").apply { isAccessible = true }.setInt(background, 0)

        pl.addBg4Ad(mock(Activity::class.java))

        verify(overlay).visibility = View.VISIBLE
    }
}
