package com.app.plugin.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.`when`

/** 1.6.4 Task 3 (1.6.3 T1-R2'): `dataBucketConfig` đọc từng khoá meta-data đúng chỗ và về mặc định khi rỗng/vắng/lỗi. */
class AppPluginBaseDataBucketConfigTest {
    private fun config(pm: PackageManager): Pair<String, String> {
        val app = spy(AppPluginBase())
        doReturn(pm).`when`(app).packageManager
        doReturn("test.pkg").`when`(app).packageName
        val m = AppPluginBase::class.java.getDeclaredMethod("dataBucketConfig").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return m.invoke(app) as Pair<String, String>
    }

    private fun pmWith(info: ApplicationInfo): PackageManager =
        mock(PackageManager::class.java).also { `when`(it.getApplicationInfo(anyString(), anyInt())).thenReturn(info) }

    @Test fun `dataBucketConfig doc dung cap khoa tu meta-data`() {
        val bundle = mock(Bundle::class.java)
        `when`(bundle.getString("DATABUCKETS_BASE_URL")).thenReturn("https://x.example/")
        `when`(bundle.getString("DATABUCKETS_API_KEY")).thenReturn("k1")
        val info = ApplicationInfo().apply { metaData = bundle }
        assertEquals("https://x.example/" to "k1", config(pmWith(info)))
    }

    @Test fun `dataBucketConfig ve mac dinh khi rong vang hoac loi`() {
        val empty = mock(Bundle::class.java)
        `when`(empty.getString("DATABUCKETS_BASE_URL")).thenReturn("")
        `when`(empty.getString("DATABUCKETS_API_KEY")).thenReturn("")
        assertEquals("rỗng", "" to "", config(pmWith(ApplicationInfo().apply { metaData = empty })))
        assertEquals("metaData null", "" to "", config(pmWith(ApplicationInfo().apply { metaData = null })))
        val failing = mock(PackageManager::class.java)
        `when`(failing.getApplicationInfo(anyString(), anyInt())).thenThrow(PackageManager.NameNotFoundException())
        assertEquals("NameNotFoundException", "" to "", config(failing))
    }
}
