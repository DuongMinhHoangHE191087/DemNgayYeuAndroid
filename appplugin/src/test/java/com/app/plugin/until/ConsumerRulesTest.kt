package com.app.plugin.until

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Điều 8.4: keep rule cho thứ R8 không nhìn thấy. Ba nhóm rule dưới đây từng thiếu (đo 2026-09-24):
 * bản release của app demo sập lúc mở vì `WorkDatabase_Impl` mất constructor, và Crashlytics/FCM
 * chết câm vì registrar Firebase mất `<init>()`.
 */
class ConsumerRulesTest {
    /**
     * So cả dòng đã trim, không so chuỗi con: rule bị `#` comment (hay dính vào dòng khác) không
     * được qua.
     */
    private val ruleLines = File("consumer-rules.pro").readLines().map { it.trim() }

    private fun assertRule(rule: String) =
        assertTrue("consumer-rules.pro thiếu dòng rule (nguyên dòng, không bị #): $rule", ruleLines.contains(rule))

    @Test
    fun `giu constructor cua RoomDatabase ma WorkManager cua GMA keo theo`() {
        assertRule("-keep class * extends androidx.room.RoomDatabase { <init>(); }")
    }

    @Test
    fun `giu constructor cua InputMerger va ListenableWorker`() {
        assertRule("-keep class * extends androidx.work.InputMerger { <init>(); }")
        assertRule(
            "-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }")
    }

    @Test
    fun `W2u giu BuildInfo va truong ID, khong co rule bo log LogUtil`() {
        assertRule("-keep class com.app.plugin.BuildInfo")
        assertRule("-keepclassmembers class com.app.plugin.BuildInfo { public static java.lang.String ID; }")
        assertTrue("rule bỏ log SDK chỉ ở docs/integration/appplugin-strip-logs.pro (D4)", ruleLines.none { it.startsWith("-assumenosideeffects") && "LogUtil" in it })
    }

    @Test
    fun `giu constructor cua ComponentRegistrar cua Firebase`() {
        assertRule("-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }")
    }

    /**
     * Không phải thiếu keep mà là R8 gộp ngang: ClassValueCache của kotlinx.serialization (lúc đó
     * tưởng do firebase-sessions kéo theo; thật ra là chartboost-sdk và vungle-ads) bị gộp chung lớp
     * với LogFileManager của Crashlytics, nên ART API < 34 in NoClassDefFoundError java.lang.ClassValue lúc
     * Crashlytics khởi tạo (đo 2026-09-24, emulator API 28). Nâng kotlinx.serialization lên 1.8.1 không
     * hết; tách ba lớp này ra thì hết. Từ 1.6.1 G2 rule -assumevalues dưới đây xoá hẳn ba lớp;
     * keep này giữ lại làm lớp phòng hờ khi rule đó không còn khớp.
     */
    @Test
    fun `khong cho R8 gop cac lop ClassValue cua kotlinx-serialization`() {
        listOf("ClassValueCache", "ClassValueParametrizedCache", "ClassValueReferences").forEach {
            assertRule("-keep,allowobfuscation,allowshrinking class kotlinx.serialization.internal.$it { *; }")
        }
    }

    /**
     * Đường thứ hai của cùng lỗi (1.6.1 G2): R8 vẫn outline lời gọi ClassValue.get (API 34) trong
     * ClassValueCache/ClassValueParametrizedCache ra lớp tổng hợp dùng chung
     * `AndroidAttribution$$ExternalSyntheticApiModelOutline0` của Unity Ads (mapping 1.6.0 và 1.6.1:
     * cùng lớp `lc`, `m(ClassValueReferences,java.lang.Class)`), nên ART API 28/26 xác minh lớp đó
     * lúc Unity dùng và in ClassNotFoundException java.lang.ClassValue; API 28 thêm 147 ms tranh
     * khoá. Keep ở trên không chặn được API outlining, và R8 9.2 không có rule nào tắt outlining theo
     * lớp. Cho R8 biết useClassValue luôn false thì nhánh ClassValue trong
     * createCache/createParametrizedCache là mã chết, ba lớp ClassValue* bị xoá, và không còn outline
     * nào trỏ tới ClassValueReferences.
     */
    @Test
    fun `R8 coi useClassValue cua kotlinx-serialization la false de khong con outline ClassValue`() {
        assertRule(
            "-assumevalues class kotlinx.serialization.internal.CachingKt { static boolean useClassValue return false; }")
    }

    /**
     * R8 bỏ qua câm rule -assumevalues nếu trường nó nhắm tới không còn (kotlinx.serialization là
     * phụ thuộc bắc cầu của chartboost-sdk/vungle-ads, đổi phiên bản mà không ai để ý). Soi
     * đúng bản đã resolve trên classpath test (1.5.1, cùng bản app release): trường phải còn là
     * `static boolean`. Không khởi tạo lớp (initialize=false): chỉ đọc khai báo. Cổng cuối vẫn
     * là tools/release_gate.sh (mapping không được có ClassValue).
     */
    @Test
    fun `truong useClassValue ma rule assumevalues nham toi van la static boolean`() {
        val cls = Class.forName("kotlinx.serialization.internal.CachingKt", false, javaClass.classLoader)
        val field = try {
            cls.getDeclaredField(USE_CLASS_VALUE)
        } catch (e: NoSuchFieldException) {
            fail("CachingKt không còn trường $USE_CLASS_VALUE (kotlinx.serialization đổi?): rule -assumevalues " +
                "trong consumer-rules.pro sẽ không khớp, outline ClassValue quay lại; trường hiện có: " +
                cls.declaredFields.joinToString { "${Modifier.toString(it.modifiers)} ${it.type} ${it.name}" })
            return
        }
        assertTrue("$USE_CLASS_VALUE phải static (rule viết `static boolean`)", Modifier.isStatic(field.modifiers))
        assertEquals("$USE_CLASS_VALUE phải kiểu boolean", java.lang.Boolean.TYPE, field.type)
    }

    private companion object {
        const val USE_CLASS_VALUE = "useClassValue"
    }
}
