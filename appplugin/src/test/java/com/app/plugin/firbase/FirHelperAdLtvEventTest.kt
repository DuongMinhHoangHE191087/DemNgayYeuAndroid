package com.app.plugin.firbase

import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`

/**
 * #5 (DOC/66, 2026-09-25): 5 event tROAS `AdLTV_OneDay_Top*Percent` là hợp đồng đóng băng — Google Ads nhập chúng làm
 * conversion theo đúng tên. `FirHelper.logAdTopRev` bắn đúng tên cho đúng mốc `ThresholdTopAdRev`
 * (0.094 → Top50 … 0.468 → Top10) khi doanh thu cộng dồn vượt mốc. Tên viết tay ở đây, không lấy từ `EventNames`:
 * đổi giá trị hằng là test này đỏ.
 */
class FirHelperAdLtvEventTest {

    /** Các event Firebase mà `logAdTopRev(pre, curr)` bắn, theo thứ tự. */
    private fun eventsFor(pre: Double, curr: Double): List<String> {
        val firebase = mock(FirebaseAnalytics::class.java)
        val names = mutableListOf<String>()
        `when`(firebase.logEvent(anyString(), any())).thenAnswer { names += it.getArgument<String>(0); null }
        mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { analyticsKt ->
            analyticsKt.`when`<FirebaseAnalytics> { Firebase.analytics }.thenReturn(firebase)
            FirHelper::class.java.getDeclaredMethod("logAdTopRev", Double::class.java, Double::class.java)
                .apply { isAccessible = true }
                .invoke(FirHelper.instance, pre, curr)
        }
        return names
    }

    @Test
    fun `vuot ca 5 moc thi ban 5 ten dong bang theo thu tu`() {
        assertEquals(
            listOf(
                "AdLTV_OneDay_Top50Percent",
                "AdLTV_OneDay_Top40Percent",
                "AdLTV_OneDay_Top30Percent",
                "AdLTV_OneDay_Top20Percent",
                "AdLTV_OneDay_Top10Percent",
            ),
            eventsFor(pre = 0.0, curr = 1.0)
        )
    }

    @Test
    fun `moi moc dung mot ten`() {
        val thresholds = FirHelper.instance.ThresholdTopAdRev
        val expected = listOf("Top50", "Top40", "Top30", "Top20", "Top10").map { "AdLTV_OneDay_${it}Percent" }
        val actual = thresholds.map { t -> eventsFor(pre = t - 0.0001, curr = t) }
        assertEquals(expected.map { listOf(it) }, actual)
    }

    @Test
    fun `khong vuot moc nao thi khong ban`() {
        assertEquals(emptyList<String>(), eventsFor(pre = 0.0, curr = 0.05))
        assertEquals(emptyList<String>(), eventsFor(pre = 0.5, curr = 0.9))
    }
}
