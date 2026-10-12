package com.app.plugin.ads.core

import android.os.Bundle
import com.app.plugin.ads.AdsKillSwitchTest
import com.app.plugin.logevent.ServerEventCapture
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify

/** ROB-4 (2.3.0): tên event Firebase `aad_*` luôn <= 40 ký tự (FA bỏ tên dài hơn); cắt placement, giữ prefix + hậu tố platform/format. */
class AdEventNameTest {
    private val helper = AdsKillSwitchTest.newAdsHelper()

    /** Tên event gửi Firebase cho start/loaded/fail của một placement. */
    private fun names(pl: String, platform: String, format: String): List<String> {
        val firebase = mock(FirebaseAnalytics::class.java)
        ServerEventCapture().use {
            mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { k ->
                k.`when`<FirebaseAnalytics> { Firebase.analytics }.thenReturn(firebase)
                helper.onAdLoadStart(pl, pl, format, platform, "id")
                helper.onAdLoaded(pl, pl, format, platform, "id", "net")
                helper.onAdLoadFail(pl, pl, format, platform, "id2", "err")
            }
        }
        val c = ArgumentCaptor.forClass(String::class.java)
        verify(firebase, atLeastOnce()).logEvent(c.capture(), org.mockito.ArgumentMatchers.nullable(Bundle::class.java))
        return c.allValues.filter { it.startsWith("aad_") }
    }

    @Test fun `ROB4 placement gift_premium_template khong vuot 40 ky tu va giu prefix va hau to`() {
        val n = names("gift_premium_template", "admob", "rewarded")
        assertEquals("lst/led/lfa: $n", listOf("aad_lst_", "aad_led_", "aad_lfa_"), n.map { it.take(8) })
        n.forEach { assertTrue("dài ${it.length}: $it", it.length <= 40 && it.endsWith("_gg_rewarded") && it.contains("gift_premium_")) }
    }

    @Test fun `ROB4 placement 30 ky tu moi platform format deu cat xac dinh du 40 ky tu`() {
        val pl = "abcdefghij_abcdefghij_abcdefghij"
        for ((plat, fmt) in listOf("admob" to "rewarded_interstitial", "facebook" to "native_custom", "applovin" to "interstitial")) {
            val a = names(pl, plat, fmt)
            assertEquals(3, a.size)
            a.forEach { assertTrue("dài ${it.length}: $it", it.length <= 40) }
            assertEquals("xác định", a, names(pl, plat, fmt))
        }
    }

    @Test fun `ROB4 placement ngan giu nguyen`() {
        assertEquals(listOf("aad_lst_full_splash_gg_int", "aad_led_full_splash_gg_int", "aad_lfa_full_splash_gg_int"), names("full_splash", "admob", "interstitial"))
    }
}
