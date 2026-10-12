package com.example.ui.util

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileUtilsThreadSafetyTest {

  @Test
  fun parseDateToMillis_isThreadSafe() {
    val input = "15/08/1998"
    val expected = ProfileUtils.parseDateToMillis(input)
    val threads = 8
    val callsPerThread = 2000

    val pool = Executors.newFixedThreadPool(threads)
    val startLatch = CountDownLatch(1)
    try {
      val futures = List(threads) {
        pool.submit(Callable {
          startLatch.await()
          (1..callsPerThread).count { ProfileUtils.parseDateToMillis(input) != expected }
        })
      }
      startLatch.countDown()

      val outcomes = futures.map { runCatching { it.get(60, TimeUnit.SECONDS) } }
      val errors = outcomes.mapNotNull { it.exceptionOrNull() }
      assertTrue("worker threads threw: $errors", errors.isEmpty())

      val wrong = outcomes.mapNotNull { it.getOrNull() }.sum()
      assertTrue("$wrong of ${threads * callsPerThread} calls returned a wrong value", wrong == 0)
    } finally {
      pool.shutdownNow()
    }
  }
}
