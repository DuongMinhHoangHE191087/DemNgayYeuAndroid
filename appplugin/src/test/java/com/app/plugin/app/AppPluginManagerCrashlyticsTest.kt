package com.app.plugin.app

import android.util.Log
import com.app.plugin.BuildConfig
import com.app.plugin.until.LogUtil
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify

/** Task 16 (G7): Crashlytics mang phiên bản SDK (`appplugin_version`) trên mọi crash; lỗi thì log, không ném. */
class AppPluginManagerCrashlyticsTest {

    @Test fun `gan appplugin_version bang phien ban SDK`() {
        val crashlytics = mock(FirebaseCrashlytics::class.java)
        mockStatic(FirebaseCrashlytics::class.java).use { s ->
            s.`when`<FirebaseCrashlytics> { FirebaseCrashlytics.getInstance() }.thenReturn(crashlytics)

            AppPluginManager.instance.tagCrashlyticsVersion()
        }
        verify(crashlytics).setCustomKey("appplugin_version", BuildConfig.SDK_VERSION)
    }

    @Test fun `Crashlytics loi thi log, khong nem`() {
        mockStatic(FirebaseCrashlytics::class.java).use { s ->
            s.`when`<FirebaseCrashlytics> { FirebaseCrashlytics.getInstance() }
                .thenThrow(IllegalStateException("Default FirebaseApp is not initialized"))
            mockStatic(Log::class.java).use { log ->
                AppPluginManager.instance.tagCrashlyticsVersion()

                log.verify { Log.e(eq(LogUtil.TAG), contains("Crashlytics appplugin_version")) }
            }
        }
    }
}
