package com.app.plugin.ads.adsmob

import com.app.plugin.until.AppUtil
import com.google.android.gms.ads.ResponseInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * 1.9.0 Task 1 (D11, Hợp đồng A): một mapper lớp adapter → nhãn mạng, `AppUtil.extractAdNetwork`;
 * `AdsMobMy.getAdnetName` uỷ quyền cho nó. Trước 1.9.0 hai bản chép lệch nhau: AdMob ra tên lớp viết thường.
 */
class AdNetworkMapperTest {
    private fun info(adapterClass: String?): ResponseInfo = mock(ResponseInfo::class.java).also {
        `when`(it.mediationAdapterClassName).thenReturn(adapterClass)
    }

    // B9
    @Test
    fun `getAdnetName cua AdMob bang extractAdNetwork bang admob`() {
        val cls = "com.google.ads.mediation.admob.AdMobAdapter"
        assertEquals("extractAdNetwork", "admob", AppUtil.extractAdNetwork(cls))
        assertEquals(
            "getAdnetName must equal extractAdNetwork",
            AppUtil.extractAdNetwork(cls),
            AdsMobMy.instance.getAdnetName(info(cls)),
        )
    }

    // B10
    @Test
    fun `bay lop adapter that va source_null ra cung nhan o ca hai duong`() {
        val expected = linkedMapOf(
            "com.google.ads.mediation.admob.AdMobAdapter" to "admob",
            "com.google.ads.mediation.facebook.FacebookMediationAdapter" to "facebook",
            "com.google.ads.mediation.pangle.PangleMediationAdapter" to "pangle",
            "com.google.ads.mediation.mintegral.MintegralMediationAdapter" to "mintegral",
            "com.google.ads.mediation.unity.UnityMediationAdapter" to "unity",
            "com.google.ads.mediation.vungle.VungleMediationAdapter" to "vungle",
            "com.google.ads.mediation.applovin.AppLovinMediationAdapter" to "applovin",
        )
        for ((cls, label) in expected) {
            assertEquals("extractAdNetwork($cls)", label, AppUtil.extractAdNetwork(cls))
            assertEquals("getAdnetName($cls)", label, AdsMobMy.instance.getAdnetName(info(cls)))
        }
        assertEquals("responseInfo null", "source_null", AdsMobMy.instance.getAdnetName(null))
        assertEquals("adapter class null", "source_null", AdsMobMy.instance.getAdnetName(info(null)))
    }
}
