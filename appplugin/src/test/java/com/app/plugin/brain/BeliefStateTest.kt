package com.app.plugin.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeliefStateTest {

    private val eps = 1e-9

    @Test
    fun `prior khoi tao la dong deu va posterior tong bang 1`() {
        val b = BeliefState("p1")
        assertTrue(b.isStillPrior())
        assertEquals(1.0, b.posterior.values.sum(), eps)
        LatentPlayerType.values().forEach {
            assertEquals(0.25, b.posterior[it]!!, eps)
        }
    }

    @Test
    fun `update giu posterior chuan hoa ve tong 1`() {
        val b = BeliefState("p1")
        b.update(
            mapOf(
                LatentPlayerType.HIGH_SKILL_LOW_TOLERANCE to 0.9,
                LatentPlayerType.HIGH_SKILL_HIGH_TOLERANCE to 0.1,
                LatentPlayerType.LOW_SKILL_HIGH_CHURN_RISK to 0.3,
                LatentPlayerType.CASUAL_STEADY to 0.5
            )
        )
        assertEquals(1.0, b.posterior.values.sum(), 1e-9)
        assertFalse(b.isStillPrior())
    }

    @Test
    fun `update dich chuyen posterior dung huong likelihood cao nhat`() {
        val b = BeliefState("p1")
        val target = LatentPlayerType.LOW_SKILL_HIGH_CHURN_RISK
        b.update(
            LatentPlayerType.values().associateWith { if (it == target) 0.9 else 0.1 }
        )
        assertEquals(target, b.mostLikelyType())
        assertTrue("posterior cua target phai vuot 0.25", b.posterior[target]!! > 0.25)
    }

    @Test
    fun `cap nhat lap lai cung mot huong lam tang dan do tin cay`() {
        val b = BeliefState("p1")
        val target = LatentPlayerType.HIGH_SKILL_HIGH_TOLERANCE
        val likelihood = LatentPlayerType.values().associateWith { if (it == target) 0.8 else 0.2 }

        var previous = b.confidence()
        repeat(4) {
            b.update(likelihood)
            val now = b.confidence()
            assertTrue("do tin cay phai tang don dieu, truoc=$previous sau=$now", now > previous)
            previous = now
        }
    }

    @Test
    fun `update voi normalizer bang 0 khong lam hong posterior`() {
        val b = BeliefState("p1")
        val before = b.posterior.toMap()
        b.update(LatentPlayerType.values().associateWith { 0.0 })
        // Không có bằng chứng hợp lệ nào -> giữ nguyên, không chia cho 0 rồi ra NaN.
        assertEquals(before, b.posterior.toMap())
        assertTrue(b.posterior.values.none { it.isNaN() })
    }

    @Test
    fun `serialize roi deserialize cho ra posterior tuong duong`() {
        val original = BeliefState("p1")
        original.update(
            mapOf(
                LatentPlayerType.HIGH_SKILL_LOW_TOLERANCE to 0.7,
                LatentPlayerType.HIGH_SKILL_HIGH_TOLERANCE to 0.2,
                LatentPlayerType.LOW_SKILL_HIGH_CHURN_RISK to 0.05,
                LatentPlayerType.CASUAL_STEADY to 0.05
            )
        )
        val restored = BeliefState.deserialize("p1", original.serialize())
        LatentPlayerType.values().forEach {
            assertEquals(original.posterior[it]!!, restored.posterior[it]!!, 1e-6)
        }
        assertEquals(original.mostLikelyType(), restored.mostLikelyType())
    }

    @Test
    fun `deserialize du lieu hong tra ve prior thay vi nem exception`() {
        val corruptInputs = listOf(
            null, "", "   ", "rác", "A:B:C", "HIGH_SKILL_LOW_TOLERANCE:abc",
            "HIGH_SKILL_LOW_TOLERANCE:0.5", // thiếu 3 type còn lại
            "HIGH_SKILL_LOW_TOLERANCE:0|HIGH_SKILL_HIGH_TOLERANCE:0|" +
                "LOW_SKILL_HIGH_CHURN_RISK:0|CASUAL_STEADY:0" // tổng = 0
        )
        for (raw in corruptInputs) {
            val b = BeliefState.deserialize("p1", raw)
            assertTrue("input '$raw' phai cho ra prior", b.isStillPrior())
            assertEquals(1.0, b.posterior.values.sum(), 1e-9)
        }
    }

    @Test
    fun `deserialize tu chuan hoa khi tong khac 1`() {
        // Ghi tay chuỗi có tổng = 2.0; deserialize phải chia lại cho tổng.
        val raw = "HIGH_SKILL_LOW_TOLERANCE:1.0|HIGH_SKILL_HIGH_TOLERANCE:0.5|" +
            "LOW_SKILL_HIGH_CHURN_RISK:0.3|CASUAL_STEADY:0.2"
        val b = BeliefState.deserialize("p1", raw)
        assertEquals(1.0, b.posterior.values.sum(), 1e-9)
        assertEquals(0.5, b.posterior[LatentPlayerType.HIGH_SKILL_LOW_TOLERANCE]!!, 1e-9)
        assertNotEquals(LatentPlayerType.CASUAL_STEADY, b.mostLikelyType())
    }
}
