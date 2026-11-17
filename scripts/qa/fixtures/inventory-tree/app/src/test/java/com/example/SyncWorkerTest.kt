package com.example

import org.junit.Test

class SyncWorkerTest {
  @Test
  fun pushesPending() {}

  @Test fun retriesOnFailure() {}

  @Test(expected = IllegalStateException::class)
  @Ignore
  fun `fails on bad token`() {}

  // @Test
  fun notATest() {}

  @Test
  fun a() {}

  @Test
  fun b() {}
}