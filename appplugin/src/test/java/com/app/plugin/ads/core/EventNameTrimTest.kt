package com.app.plugin.ads.core

import android.os.Bundle
import com.app.plugin.ads.AdsKillSwitchTest
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify

/** 2.4.0 CARRY-T (minors-230 #2, #3): `trimEnd('_')` của tên event bị cắt, và nhánh prefix `aad_imp_` (onAdImpression) — phủ cho ROB-4 / AdEventNameTest. */
class EventNameTrimTest {
    private val helper = AdsKillSwitchTest.newAdsHelper()

    /** Tên event `aad_imp_*` Firebase nhận khi [placement] hiện quảng cáo rewarded của admob. */
    private fun impressionNames(placement: String): List<String> {
        val firebase = mock(FirebaseAnalytics::class.java)
        mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { k ->
            k.`when`<FirebaseAnalytics> { Firebase.analytics }.thenReturn(firebase)
            helper.onAdImpression(placement, placement, "rewarded", "admob", "id", "net")
        }
        val c = ArgumentCaptor.forClass(String::class.java)
        verify(firebase, atLeastOnce()).logEvent(c.capture(), nullable(Bundle::class.java))
        return c.allValues.filter { it.startsWith("aad_imp_") }
    }

    @Test fun `TR1 cat placement dung ngay dau gach duoi thi bo gach cuoi khong de lai hai gach lien`() {
        // prefix aad_imp_ (8) + hậu tố _gg_rewarded (12) -> ngân sách 20: take(20) của placement kết thúc bằng '_'.
        val n = impressionNames("abcdefghijklmnopqrs_xyz")
        assertEquals(listOf("aad_imp_abcdefghijklmnopqrs_gg_rewarded"), n)
        assertFalse("không '__' trước hậu tố: $n", n.single().contains("__"))
    }

    @Test fun `TR1b nhanh aad_imp_ placement ngan giu nguyen khong cat`() {
        assertEquals(listOf("aad_imp_full_splash_gg_rewarded"), impressionNames("full_splash"))
    }
}
