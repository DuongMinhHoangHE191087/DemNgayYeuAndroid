package com.app.plugin.segment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EcpmBucketResolverTest {

    @Test
    fun `empty window returns null bucket`() {
        val resolver = EcpmBucketResolver.newForTesting()
        assertNull(resolver.currentBucket())
    }

    @Test
    fun `single value at threshold counts as HIGH`() {
        val resolver = EcpmBucketResolver.newForTesting()
        val bucket = resolver.recordImpression(500_000L)
        assertEquals(EcpmBucket.HIGH, bucket)
        assertEquals(EcpmBucket.HIGH, resolver.currentBucket())
    }

    @Test
    fun `single value below threshold counts as LOW`() {
        val resolver = EcpmBucketResolver.newForTesting()
        val bucket = resolver.recordImpression(499_999L)
        assertEquals(EcpmBucket.LOW, bucket)
    }

    @Test
    fun `single value above threshold counts as HIGH`() {
        val resolver = EcpmBucketResolver.newForTesting()
        val bucket = resolver.recordImpression(1_000_000L)
        assertEquals(EcpmBucket.HIGH, bucket)
    }

    @Test
    fun `mean below threshold across mixed values is LOW`() {
        val resolver = EcpmBucketResolver.newForTesting()
        // 4 values @ 100k + 1 @ 1M = mean 280k → below 500k threshold
        repeat(4) { resolver.recordImpression(100_000L) }
        val bucket = resolver.recordImpression(1_000_000L)
        assertEquals(EcpmBucket.LOW, bucket)
    }

    @Test
    fun `mean at exactly threshold is HIGH`() {
        val resolver = EcpmBucketResolver.newForTesting()
        // 5 values @ 500k → mean exactly 500k → HIGH
        repeat(5) { resolver.recordImpression(500_000L) }
        assertEquals(EcpmBucket.HIGH, resolver.currentBucket())
    }

    @Test
    fun `window drops oldest when full`() {
        val resolver = EcpmBucketResolver.newForTesting()
        // Fill window with 10 high values (mean = 1M → HIGH)
        repeat(10) { resolver.recordImpression(1_000_000L) }
        assertEquals(EcpmBucket.HIGH, resolver.currentBucket())

        // Add 10 low values, one at a time, dropping a high each time
        // After the 10th low value, all 10 slots should be at 100k → LOW
        repeat(10) { resolver.recordImpression(100_000L) }
        assertEquals(EcpmBucket.LOW, resolver.currentBucket())
    }

    @Test
    fun `partial window after drop computes mean from remaining values`() {
        val resolver = EcpmBucketResolver.newForTesting()
        // Fill with 10 high values
        repeat(10) { resolver.recordImpression(1_000_000L) }
        // Add 5 low values - window is now [5×high, 5×low], mean 550k → still HIGH
        repeat(5) { resolver.recordImpression(100_000L) }
        assertEquals(EcpmBucket.HIGH, resolver.currentBucket())
        // Add 1 more low - window becomes [4×high, 6×low], mean 460k → LOW
        val bucket = resolver.recordImpression(100_000L)
        assertEquals(EcpmBucket.LOW, bucket)
    }
}
