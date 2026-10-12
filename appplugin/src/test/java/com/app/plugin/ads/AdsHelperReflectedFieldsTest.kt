package com.app.plugin.ads

import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * research-g8 §2.2 luật 1 (plan 1.5.0 R1): 16 field private này được test đọc/ghi bằng `getDeclaredField(name)`
 * (đo 2026-09-27: `grep -rhoE '(setField|field|getDeclaredField)(<[^(]*>)?\((helper, )?"[A-Za-z0-9]+"' src/test/java/com/app/plugin/ads`,
 * giữ tên là field của AdsHelper; các tên còn lại thuộc AdsPlBase, AdsMobMy…). 14 tên đầu là danh sách R1 của
 * plan; `pendingFull2` và `full2TransitionActive` do Task 0 thêm: Full2HostDestroyTest đọc chúng qua `field<T>("…")`
 * mà grep của plan (không có `<…>`) không bắt. Controller tách khỏi AdsHelper không được mang chúng đi: đọc qua hàm `internal` của façade.
 * R1 còn đòi "cùng tên, `private`": field JVM phải private và không static — bắt `@JvmField`/`lateinit` không private
 * (field lộ theo visibility của property) và field dời vào `companion object` (thành static). `private` → `internal`
 * trên property thường không đổi field JVM (vẫn private): chỉ lộ ở dump thành getter `…$appplugin` — api_facade_check.sh
 * cho qua (internal), reviewer đọc diff dump.
 */
class AdsHelperReflectedFieldsTest {
    private val names = listOf(
        "listAdsBn", "listAdsNtCl", "listAdsRect", "listAdsNative", "listAdsFull", "listAdsGift", "listAdsAll",
        "fullScreenAdPolicy", "postAdCooldown", "openFlag4Resume", "mContext", "mActivity", "pendingRemoveAdsMask",
        "full2MainHandler", "pendingFull2", "full2TransitionActive",
    )

    @Test
    fun `field test doc bang reflection van la field cua AdsHelper`() {
        names.forEach { AdsHelper::class.java.getDeclaredField(it) } // NoSuchFieldException = đỏ
    }

    @Test
    fun `field test doc bang reflection van private va khong static`() {
        names.forEach {
            val m = AdsHelper::class.java.getDeclaredField(it).modifiers
            assertTrue(
                "AdsHelper.$it phải private, không static (R1); modifiers = ${Modifier.toString(m)}",
                Modifier.isPrivate(m) && !Modifier.isStatic(m),
            )
        }
    }
}
