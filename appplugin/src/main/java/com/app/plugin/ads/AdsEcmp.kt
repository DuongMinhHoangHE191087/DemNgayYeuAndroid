package com.app.plugin.ads

public class AdsEcmp(adEcmps: AdsEcmps) {
    public var adEcmps: AdsEcmps

    public var adsId: String = ""
    public var adnetname: String = ""
    public var isLoading: Boolean = false
    public var isLoaded: Boolean = false
    public var isDefault: Boolean = false
    public var flagShow: Int = 0
    public var timeShow: Long = 0
    public var time4Count: Long = 0
    public var countTimeShow: Long = 0
    public var revenue: Double = 0.0
    /** Lúc có quảng cáo mới (theo [AdsEcmps.nowMs], giờ hệ thống); 0 khi chưa có lần nào. */
    public var loadedAtMillis: Long = 0
        private set
    public var adObject: Any? = null
        set(value) {
            if (value != null && value !== field) loadedAtMillis = adEcmps.nowMs()
            field = value
        }

    /** Tái dùng quảng cáo đã chuẩn bị giữa các lượt show: chỉ khi load chưa tới 1 giờ. */
    public fun isFreshForReuse(nowMillis: Long = adEcmps.nowMs()): Boolean =
        isLoaded && !isLoading && adObject != null && loadedAtMillis > 0 &&
            nowMillis - loadedAtMillis in 0 until 3_600_000L

    public var adsMem: MutableList<Any> = mutableListOf()
    /**
     * Mã lượt load banner/rect của mục: tăng 1 mỗi request (Task L mục 1, trước đây `System.currentTimeMillis()` nên hai
     * request trong cùng một mili giây trùng mã). Listener chụp giá trị lúc gửi request và so với giá trị hiện tại để
     * biết callback là của lượt mới nhất ("refresh") hay của view lượt trước ("not turn"). Chỉ đọc/ghi trên main.
     */
    public var turnLoadId: Long = 0

    init {
        this.adEcmps = adEcmps
    }

    public fun checkDestroyAd() {
        adsMem.removeIf { adEcmps.adPl.destroyAd(it) }
//        val it = adsMem?.iterator()
//        while (it?.hasNext() == true) {
//            val item = it.next()
//            if (adEcmps?.adPl?.destroyAd(item) == true) {
//                it.remove()
//            }
//        }
    }
    public fun getAdsPl(): AdsPlBase? {
        return adEcmps.adPl
    }
}