package com.app.plugin.ads.core

import android.view.ViewGroup
import com.app.plugin.SdkKillSwitch
import com.app.plugin.ads.AdSdkStartup
import com.app.plugin.ads.AdsGiftShowCb
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsKillSwitchTest
import com.app.plugin.ads.AdsPlacementConfigManager
import com.app.plugin.ads.PlacementDefault
import com.app.plugin.logevent.AdStatus
import com.app.plugin.logevent.AdTracker
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.LogEventTransport
import com.app.plugin.logevent.LogService
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.Constants
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * 1.7.0 Task 2 (D11, G6 "reason 100 %"): 8 đường `return false` im lặng của `showInterstitial`/`showRewarded` nay gửi
 * `ad_show_fail` với lý do sẵn có của hợp đồng; mỗi lượt gọi `show*` public của controller phát đúng một
 * `ad_show_attempt` (S9). Event đọc ở body Retrofit nhận ([ServerEventCapture]).
 */
class ShowChainTelemetryTest {

    private class NoopShowCb : AdsGiftShowCb {
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {}
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) {}
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) {}
    }

    private lateinit var helper: AdsHelper
    private val cb = NoopShowCb()

    @Before
    fun setUp() {
        SdkKillSwitch.current = SdkKillSwitch.NONE
        AdSdkStartup.resetForTest()
        helper = AdsKillSwitchTest.newAdsHelper()
    }

    @After
    fun tearDown() {
        AdTracker.updateStatus("full_home", AdStatus.NotLoaded)
        SdkKillSwitch.current = SdkKillSwitch.NONE
        AdSdkStartup.resetForTest()
    }

    private fun plcf(placement: String) = helper.getAdsPlacementConfig(placement, 1)!!

    /** Không placement nào có cấu hình: `getAdsPlacementConfig` trả `null` (nhánh "has not cf"). */
    private fun noPlacementConfig() {
        helper.placementsConfigManager = mock(AdsPlacementConfigManager::class.java)
    }

    private fun assertOneFail(placement: String, reason: String, block: () -> Boolean) {
        ServerEventCapture().use { server ->
            assertFalse(block())
            assertEquals(listOf(placement to reason), server.showFails(1))
        }
    }

    @Test fun `S1 showInterstitial cf not phat ad_show_fail reason cf_not`() {
        plcf("full_home").flagShow = -1
        assertOneFail("full_home", Constants.AdShowFailReason.CONFIG_NOT) { helper.showInterstitial("home", 0, cb, false) }
    }

    @Test fun `S2 showRewarded delta time phat not_allow_show`() {
        helper.giftTimeShow = System.currentTimeMillis() / 1000
        assertOneFail("gift_home", Constants.AdShowFailReason.NOT_ALLOW_SHOW) { helper.showRewarded("home", cb) }
    }

    @Test fun `S3 showInterstitial dang hien phat ad_showing`() {
        plcf("full_home").isShowing = true
        assertOneFail("full_home", Constants.AdShowFailReason.AD_SHOWING) { helper.showInterstitial("home", 0, cb, false) }
    }

    @Test fun `S4 showInterstitial khong co cau hinh phat not_config`() {
        noPlacementConfig()
        assertOneFail("full_home", Constants.AdShowFailReason.NOT_CONFIG) { helper.showInterstitial("home", 0, cb, false) }
    }

    @Test fun `S5 showRewarded khi quang cao full dang hien phat ad_full_showing`() {
        helper.flagAdsFullShowing.set(1)
        assertOneFail("gift_home", Constants.AdShowFailReason.AD_FULL_SHOWING) { helper.showRewarded("home", cb) }
    }

    @Test fun `S6 showRewarded cf not phat cf_not`() {
        plcf("gift_home").flagShow = -1
        assertOneFail("gift_home", Constants.AdShowFailReason.CONFIG_NOT) { helper.showRewarded("home", cb) }
    }

    @Test fun `S7 showRewarded dang hien phat ad_showing`() {
        plcf("gift_home").isShowing = true
        assertOneFail("gift_home", Constants.AdShowFailReason.AD_SHOWING) { helper.showRewarded("home", cb) }
    }

    @Test fun `S8 showRewarded khong co cau hinh phat not_config`() {
        noPlacementConfig()
        assertOneFail("gift_home", Constants.AdShowFailReason.NOT_CONFIG) { helper.showRewarded("home", cb) }
    }

    /** Không mạng nào: các map mạng (`lateinit`, dựng ở `initAds`) rỗng. */
    private fun noNetworks() {
        listOf("listAdsBn", "listAdsNtCl", "listAdsRect", "listAdsNative", "listAdsFull", "listAdsGift").forEach { name ->
            AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, mutableMapOf<Int, Any>())
        }
        AdsHelper::class.java.getDeclaredField("listAdsAll").apply { isAccessible = true }.set(helper, mutableListOf<Any>())
    }

    /** Gọi mỗi hàm `show*` public của 8 controller một lần, cộng lượt `rwi_` (1.9.0 Task 2). */
    private fun callEveryShow() {
        helper.showInterstitial("home", 0, cb, false)
        helper.showRewarded("home", cb)
        helper.showRewarded("rwi_home", cb) // 1.9.0 Task 2: rewarded interstitial (§C bước 1)
        helper.showOpenAd(PlacementDefault.PLFullSplash, cb)
        helper.showNative("home", mock(ViewGroup::class.java), cb)
        helper.getNativeFullForShow("nf", cb)
        helper.showNtCl("home", cb)
        helper.showRect("home", mock(ViewGroup::class.java), null)
        helper.showBanner("home", mock(ViewGroup::class.java), isCollapse = false, cbLoad = null, cbShow = null, isDeltaTime = false)
    }

    /** (ad_format, placement) của 8 lượt gọi trong [callEveryShow]. */
    private val everyAttempt = listOf(
        "banner" to "bn_home",
        "interstitial" to "full_home",
        "native_collapse" to "cl_home",
        "native_custom" to "nt_home",
        "native_full" to "full_nf",
        "opened" to PlacementDefault.PLFullSplash,
        "rect" to "rect_home",
        "rewarded" to "gift_home",
        "rewarded_interstitial" to "rwi_home",
    )

    /** Đặt `isLoaded` của đúng các placement trong [loaded], các placement khác của [everyAttempt] về `false`. */
    private fun setLoaded(vararg loaded: String) = everyAttempt.forEach { (_, pl) -> plcf(pl).isLoaded = pl in loaded }

    /** Mỗi lượt gọi đúng một `ad_show_attempt`, `ready` = 1 đúng với các placement trong [loaded]. */
    private fun expectedAttempts(vararg loaded: String) =
        everyAttempt.map { (format, pl) -> "$format|$pl|ready=${if (pl in loaded) 1 else 0}" }.sorted()

    private fun actualAttempts(got: List<JsonObject>) = got.map {
        "${it.get("ad_format").asString}|${it.get("placement").asString}|ready=${it.get("ready").asInt}"
    }.sorted()

    /** `Call` trả 200 (trên luồng riêng, qua [respond]): hàng đợi không kẹt ở trần 4 request đang bay. */
    private class OkCall(private val respond: (() -> Unit) -> Unit) : Call<ResponseBody> {
        override fun enqueue(callback: Callback<ResponseBody>) = respond { callback.onResponse(this, Response.success("ok".toResponseBody())) }
        override fun execute(): Response<ResponseBody> = throw UnsupportedOperationException()
        override fun isExecuted(): Boolean = true
        override fun cancel() = Unit
        override fun isCanceled(): Boolean = false
        override fun clone(): Call<ResponseBody> = OkCall(respond)
        override fun request(): Request = Request.Builder().url("http://localhost/").build()
        override fun timeout(): Timeout = Timeout.NONE
    }

    /**
     * Body tới server, mọi request trả 200 (như [ServerEventCapture] nhưng không giữ request). [close] ngừng trả lời rồi chờ
     * các luồng trả lời đã chạy xong: sender cũ đọc `logService` lúc gửi, trả lời muộn sẽ đẩy event còn chờ sang test sau.
     */
    private class OkServer : AutoCloseable {
        private val field = LogEventTransport::class.java.getDeclaredField("logService").apply { isAccessible = true }
        private val bodies = CopyOnWriteArrayList<String>()
        private val responders = mutableListOf<Thread>()
        private var open = true

        private fun respond(block: () -> Unit) = synchronized(this) {
            if (open) responders += Thread(block).also { it.start() }
        }

        init {
            LogEventServer.resetSenderForTest()
            field.set(null, object : LogService {
                override fun logActionsToServer(requestBody: RequestBody): Call<ResponseBody> {
                    bodies += Buffer().also { requestBody.writeTo(it) }.readUtf8()
                    return OkCall(::respond)
                }
            })
        }

        /** `eventData` của các `ad_show_attempt`: chờ tới khi có [count] cái (tối đa 5 s), rồi tới khi mọi event lặng. */
        fun attempts(count: Int): List<JsonObject> {
            val deadline = System.currentTimeMillis() + 5_000L
            while (named().size < count && System.currentTimeMillis() < deadline) Thread.sleep(5)
            settle()
            return named().map { it.getAsJsonObject("eventData") }
        }

        /** Chờ tới khi không body nào tới thêm trong 300 ms (tối đa 5 s): mọi event của lượt (cả `ad_show_fail`) đã gửi. */
        private fun settle() {
            val deadline = System.currentTimeMillis() + 5_000L
            do {
                val seen = bodies.size
                Thread.sleep(300)
            } while (bodies.size != seen && System.currentTimeMillis() < deadline)
        }

        private fun named() = bodies.map { JsonParser.parseString(it).asJsonObject }
            .filter { it.get("eventName").asString == "ad_show_attempt" }

        override fun close() {
            settle()
            val started = synchronized(this) { open = false; responders.toList() }
            started.forEach { it.join(5_000L) }
            field.set(null, null)
            LogEventServer.resetSenderForTest()
        }
    }

    private fun attempts(server: OkServer) = server.attempts(everyAttempt.size)

    /** 1.9.0 Task 2 W1 (hợp đồng §C): `rwi_` định tuyến trước tiền tố `gift_`; attempt mang placement của app. */
    @Test fun `W1 showRewarded rwi_ phat ad_show_attempt placement rwi_home, khong gift_rwi_home`() {
        noNetworks()
        OkServer().use { server ->
            helper.showRewarded("rwi_home", cb)
            val got = server.attempts(1)
            assertEquals(1, got.size)
            assertEquals("rwi_home", got[0].get("placement").asString)
            assertEquals("rewarded_interstitial", got[0].get("ad_format").asString)
        }
    }

    @Test fun `S9 moi show phat dung mot ad_show_attempt`() {
        // Lượt đi tiếp (cấu hình mặc định, không mạng nào) và lượt bị cổng chặn (kill switch ads): mỗi lời gọi một event,
        // `ready` = `plcf.isLoaded` lúc gọi. R-2 I1: `AdTracker` theo slot mạng (placement dùng chung slot mặc định không
        // bao giờ Loaded; `isLoaded = false` không hạ nó) nên đặt lệch nhau: interstitial Loaded cũ trong AdTracker nhưng
        // slot chưa load → 0; native, app open slot đã load nhưng AdTracker không Loaded → 1.
        noNetworks()
        AdTracker.updateStatus("full_home", AdStatus.Loaded)
        setLoaded("nt_home", PlacementDefault.PLFullSplash, "rect_home")
        OkServer().use { server ->
            callEveryShow()
            assertEquals(
                "lượt đi tiếp",
                expectedAttempts("nt_home", PlacementDefault.PLFullSplash, "rect_home"),
                actualAttempts(attempts(server))
            )
        }
        // R-2 n1: lượt đi tiếp lần hai, native và app open chưa load (ready = 0 trên đường `logAdStartAction`).
        setLoaded("full_home")
        OkServer().use { server ->
            callEveryShow()
            assertEquals("lượt đi tiếp, slot chưa load", expectedAttempts("full_home"), actualAttempts(attempts(server)))
        }
        SdkKillSwitch.current = SdkKillSwitch(ads = true)
        setLoaded("full_home", "gift_home", "cl_home")
        OkServer().use { server ->
            callEveryShow()
            assertEquals("lượt bị chặn", expectedAttempts("full_home", "gift_home", "cl_home"), actualAttempts(attempts(server)))
        }
        // R-2 n2: nhánh "has not cf" của showNative kết thúc hàm không có `return` (K2 không quét được): đúng một attempt.
        SdkKillSwitch.current = SdkKillSwitch.NONE
        noPlacementConfig()
        OkServer().use { server ->
            helper.showNative("home", mock(ViewGroup::class.java), cb)
            assertEquals("native không có cấu hình", listOf("native_custom|nt_home|ready=0"), actualAttempts(server.attempts(1)))
        }
    }
}
