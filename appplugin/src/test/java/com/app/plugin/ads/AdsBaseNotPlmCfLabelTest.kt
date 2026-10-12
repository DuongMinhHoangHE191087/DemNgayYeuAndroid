package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.ads.adsfb.AdsFbBanner
import com.app.plugin.ads.adsfb.AdsFbFull
import com.app.plugin.ads.adsfb.AdsFbGift
import com.app.plugin.ads.adsfb.AdsFbNative
import com.app.plugin.ads.adsfb.AdsFbNtCl
import com.app.plugin.ads.adsfb.AdsFbRect
import com.app.plugin.ads.adsmax.AdsMaxBanner
import com.app.plugin.ads.adsmax.AdsMaxFull
import com.app.plugin.ads.adsmax.AdsMaxGift
import com.app.plugin.ads.adsmax.AdsMaxNative
import com.app.plugin.ads.adsmax.AdsMaxNtCl
import com.app.plugin.ads.adsmax.AdsMaxRect
import com.app.plugin.ads.adsmob.AdsMobBanner
import com.app.plugin.ads.adsmob.AdsMobFull
import com.app.plugin.ads.adsmob.AdsMobGift
import com.app.plugin.ads.adsmob.AdsMobNative
import com.app.plugin.ads.adsmob.AdsMobNtCl
import com.app.plugin.ads.adsmob.AdsMobRect
import com.app.plugin.logevent.ServerEventCapture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * D3 (DOC/66): nhãn `ad_show_fail_reason` khi mạng không có cấu hình cho placement là
 * `not_plm_cf_<eventTag>_<adsType>`, với `eventTag` hằng chữ của lớp mạng. Trước đây là
 * `not_plm_cf_<tên lớp><adsType>`, mà dưới R8 của app tên lớp bị đổi (`AdsFbFull` → `z7`, nhãn `not_plm_cf_z71`).
 * Test dùng đúng 18 lớp mạng thật của SDK và so với chuỗi viết tay, nên đổi một tag là đổi hợp đồng dashboard.
 */
class AdsBaseNotPlmCfLabelTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
    }

    private class NoCb : AdsShowCb

    /** Mỗi lớp mạng cụ thể của SDK với nhãn mong đợi, viết tay (không dựng lại từ tag). */
    private fun networks(): List<Pair<AdsBase, String>> = listOf(
        AdsMobBanner(AdsIDDef.AdmobBanner, context) to "not_plm_cf_mob_bn_0",
        AdsMobNtCl(AdsIDDef.AdmobNtCl, context) to "not_plm_cf_mob_nt_cl_0",
        AdsMobRect(AdsIDDef.AdmobRect, context) to "not_plm_cf_mob_rect_0",
        AdsMobNative(AdsIDDef.AdmobNative, context) to "not_plm_cf_mob_nt_0",
        AdsMobFull(AdsIDDef.AdmobFullInters, context) to "not_plm_cf_mob_full_0",
        AdsMobFull(AdsIDDef.AdmobFullOpen, context) to "not_plm_cf_mob_full_100",
        AdsMobGift(AdsIDDef.AdmobRewarded, context) to "not_plm_cf_mob_reward_0",
        AdsMaxBanner(AdsIDDef.MaxBanner, context) to "not_plm_cf_max_bn_6",
        AdsMaxNtCl(AdsIDDef.MaxNtCl, context) to "not_plm_cf_max_nt_cl_6",
        AdsMaxRect(AdsIDDef.MaxRect, context) to "not_plm_cf_max_rect_6",
        AdsMaxNative(AdsIDDef.MaxNative, context) to "not_plm_cf_max_nt_6",
        AdsMaxFull(AdsIDDef.MaxFullNt, context) to "not_plm_cf_max_full_60",
        AdsMaxGift(AdsIDDef.MaxRewarded, context) to "not_plm_cf_max_reward_6",
        AdsFbBanner(AdsIDDef.FBBanner, context) to "not_plm_cf_fb_bn_1",
        AdsFbNtCl(AdsIDDef.FBNtCl, context) to "not_plm_cf_fb_nt_cl_1",
        AdsFbRect(AdsIDDef.FBRect, context) to "not_plm_cf_fb_rect_1",
        AdsFbNative(AdsIDDef.FBNative, context) to "not_plm_cf_fb_nt_1",
        AdsFbFull(AdsIDDef.FBFullNt, context) to "not_plm_cf_fb_full_21",
        AdsFbGift(AdsIDDef.FBRewarded, context) to "not_plm_cf_fb_reward_1",
    )

    /**
     * Nhãn [call] gửi lên server cho từng mạng. Mỗi mạng một `ServerEventCapture` riêng: hàng đợi chỉ cho 4 request
     * đang bay, mà `HeldCall` không bao giờ trả lời, nên bắt chung 19 event chỉ thấy 4.
     */
    private fun labels(call: (AdsBase, String) -> Unit): List<Pair<String, String>> =
        networks().mapIndexed { i, (net, _) ->
            val placement = "p%02d".format(i)
            ServerEventCapture().use { server ->
                call(net, placement)
                placement to server.showFails(1).joinToString { it.second }
            }
        }

    private fun expected() = networks().mapIndexed { i, (_, label) -> "p%02d".format(i) to label }

    @Test
    fun `showAd khong co cau hinh gui nhan not_plm_cf theo eventTag, khong theo ten lop`() =
        assertEquals(expected(), labels { net, pl -> assertFalse(net.showAd(pl, NoCb())) })

    @Test
    fun `getAdForShow khong co cau hinh gui cung nhan`() =
        assertEquals(expected(), labels { net, pl -> assertNull(net.getAdForShow(pl, NoCb())) })
}
