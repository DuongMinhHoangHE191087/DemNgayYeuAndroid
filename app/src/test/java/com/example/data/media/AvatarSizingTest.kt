package com.example.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarSizingTest {

  @Test
  fun targetSize_downscalesKeepingAspectRatio() {
    assertEquals(512 to 384, AvatarSizing.targetSize(4000, 3000))
    assertEquals(384 to 512, AvatarSizing.targetSize(3000, 4000))
  }

  @Test
  fun targetSize_neverUpscales() {
    assertEquals(300 to 200, AvatarSizing.targetSize(300, 200))
    assertEquals(512 to 512, AvatarSizing.targetSize(512, 512))
  }

  @Test
  fun targetSize_keepsEachSideAtLeastOnePixel() {
    assertEquals(512 to 1, AvatarSizing.targetSize(1000, 1))
    assertEquals(512 to 1, AvatarSizing.targetSize(10000, 10))
  }

  @Test
  fun targetSize_nonPositiveInputReturnsOneByOne() {
    assertEquals(1 to 1, AvatarSizing.targetSize(0, 0))
  }

  @Test
  fun inSampleSize_isLargestPowerOfTwoThatStaysAtOrAboveTarget() {
    assertEquals(4, AvatarSizing.inSampleSize(4000, 3000))
    assertEquals(2, AvatarSizing.inSampleSize(1024, 768))
    assertEquals(1, AvatarSizing.inSampleSize(600, 400))
    assertEquals(1, AvatarSizing.inSampleSize(511, 511))
    assertEquals(8, AvatarSizing.inSampleSize(5000, 100))
    assertEquals(1, AvatarSizing.inSampleSize(0, 0))
  }
}
