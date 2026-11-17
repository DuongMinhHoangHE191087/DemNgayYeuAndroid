package com.example.ui.testing

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import org.junit.rules.TestWatcher
import org.junit.runner.Description

// GiftIdeasSeed points at picsum.photos, so a Compose test that shows GiftScreen would request 20 real photos.
// With this rule every AsyncImage resolves to a blank drawable at once and the tests never touch the network.
class NoNetworkImagesRule : TestWatcher() {

  override fun starting(description: Description) {
    Coil.setImageLoader(
      ImageLoader.Builder(ApplicationProvider.getApplicationContext<Context>())
        .components {
          add(object : Interceptor {
            override suspend fun intercept(chain: Interceptor.Chain): ImageResult =
              SuccessResult(ColorDrawable(), chain.request, DataSource.MEMORY)
          })
        }
        .build()
    )
  }

  override fun finished(description: Description) {
    Coil.reset()
  }
}
