package com.app.plugin.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BanditPolicyTest {

    private val seg = mapOf("LEVEL_FAIL_CASUAL_STEADY" to 1.0)

    @Test
    fun `mot arm duy nhat thi propensity bang 1`() {
        val b = BanditPolicy()
        val (arm, p) = b.selectArm(seg, listOf("only_arm"))
        assertEquals("only_arm", arm)
        assertEquals(1.0, p, 1e-9)
    }

    @Test
    fun `propensity luon nam trong khoang 0 den 1`() {
        val b = BanditPolicy()
        repeat(20) {
            val (_, p) = b.selectArm(seg, listOf("a", "b", "c"))
            assertTrue("propensity=$p ngoai khoang [0,1]", p in 0.0..1.0)
        }
    }

    @Test
    fun `bandit hoi tu ve arm tot hon sau du bang chung`() {
        val b = BanditPolicy()
        val key = seg.keys.first()
        // "good" thắng 60/60, "bad" thua 60/60 — chênh lệch không thể nhầm được.
        repeat(60) {
            b.update(key, "good", 1.0)
            b.update(key, "bad", 0.0)
        }
        var goodWins = 0
        repeat(50) {
            val (arm, _) = b.selectArm(seg, listOf("good", "bad"))
            if (arm == "good") goodWins++
        }
        assertTrue("bandit phai chon 'good' ap dao, thuc te $goodWins/50", goodWins >= 45)
    }

    @Test
    fun `chua co du lieu thi hai arm gan nhu can bang`() {
        val b = BanditPolicy()
        var aWins = 0
        repeat(200) {
            val (arm, _) = b.selectArm(seg, listOf("a", "b"))
            if (arm == "a") aWins++
        }
        // Prior Beta(1,1) đối xứng -> không arm nào được ưu ái. Biên rộng vì đây là ngẫu nhiên.
        assertTrue("khong duoc thien vi khi chua co du lieu, a thang $aWins/200", aWins in 60..140)
    }

    @Test
    fun `update co trong so cong phan so dung vao sufficient statistics`() {
        val b = BanditPolicy()
        val key = "k"
        b.update(key, "arm", reward01 = 1.0, weight = 0.25)
        // alpha += 0.25, beta += 0.0  -> tổng quan sát = 0.25
        assertEquals(0.25, b.observationCount(key, "arm"), 1e-9)

        b.update(key, "arm", reward01 = 0.0, weight = 0.5)
        // beta += 0.5 -> tổng quan sát = 0.75
        assertEquals(0.75, b.observationCount(key, "arm"), 1e-9)
    }

    @Test
    fun `weight bang 0 khong ghi nhan gi`() {
        val b = BanditPolicy()
        b.update("k", "arm", 1.0, weight = 0.0)
        assertEquals(0.0, b.observationCount("k", "arm"), 1e-9)
    }

    @Test
    fun `belief phan bo deu bon segment thi ca bon cung hoc`() {
        val b = BanditPolicy()
        val weights = LatentPlayerType.values().associate { "DP_$it" to 0.25 }
        for ((k, w) in weights) b.update(k, "arm", 1.0, w)
        for (k in weights.keys) {
            assertEquals("segment $k phai nhan 0.25 quan sat", 0.25, b.observationCount(k, "arm"), 1e-9)
        }
    }

    @Test
    fun `serialize roi restore giu nguyen so quan sat`() {
        val b = BanditPolicy()
        b.update("segA", "arm1", 1.0)
        b.update("segA", "arm1", 1.0)
        b.update("segB", "arm2", 0.0)
        val dump = b.serialize()

        val restored = BanditPolicy()
        restored.restore(dump)
        assertEquals(2.0, restored.observationCount("segA", "arm1"), 1e-9)
        assertEquals(1.0, restored.observationCount("segB", "arm2"), 1e-9)
    }

    @Test
    fun `restore du lieu hong khong nem exception`() {
        val b = BanditPolicy()
        listOf(null, "", "rác", "a=1", "a=x,y", "a=-1,-1", "=1,2").forEach { raw ->
            b.restore(raw) // không được ném
        }
        assertNotNull(b.serialize())
    }

    @Test
    fun `armId chua dau bang van restore dung nho tach tu phai`() {
        // serialize dùng "segmentKey|armId=alpha,beta"; nếu armId chứa '=' thì phải
        // tách ở dấu '=' CUỐI CÙNG, không phải dấu đầu tiên.
        val b = BanditPolicy()
        b.update("seg", "arm=weird", 1.0)
        val restored = BanditPolicy()
        restored.restore(b.serialize())
        assertEquals(1.0, restored.observationCount("seg", "arm=weird"), 1e-9)
    }

    @Test
    fun `selectArm nem loi khi thieu dau vao`() {
        val b = BanditPolicy()
        var threwOnEmptyArms = false
        try {
            b.selectArm(seg, emptyList())
        } catch (e: IllegalArgumentException) {
            threwOnEmptyArms = true
        }
        assertTrue("phai nem khi candidateArmIds rong", threwOnEmptyArms)

        var threwOnEmptySegments = false
        try {
            b.selectArm(emptyMap(), listOf("a", "b"))
        } catch (e: IllegalArgumentException) {
            threwOnEmptySegments = true
        }
        assertTrue("phai nem khi segmentWeights rong", threwOnEmptySegments)
    }
}
