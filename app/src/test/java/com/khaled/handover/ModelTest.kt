package com.khaled.handover

import com.khaled.handover.data.*
import org.junit.Assert.*
import org.junit.Test

class ModelTest {
    @Test fun `skipped does not masquerade as captured`() {
        val counts = countStatuses(listOf(Capture.CAPTURED, Capture.CAPTURED, Capture.SKIPPED, Capture.NOT_APPLICABLE))
        assertEquals(2, counts.captured); assertEquals(1, counts.skipped); assertEquals(4, counts.total)
    }
    @Test fun `return completion differs from report generation`() {
        assertEquals(Progress.BASELINE_DONE, nextStatus(Progress.DRAFT, Phase.BASELINE))
        assertEquals(Progress.RETURN_DONE, nextStatus(Progress.AWAITING_RETURN, Phase.RETURN))
        assertEquals(Progress.DRAFT, nextStatus(Progress.DRAFT, Phase.RETURN))
        assertEquals(Progress.ARCHIVED, nextStatus(Progress.ARCHIVED, Phase.RETURN))
    }
    @Test fun `all templates have stable unique keys`() {
        for (type in listOf("VEHICLE", "DEVICE", "APARTMENT")) {
            val items = Templates.forCategory(type)
            assertTrue(items.isNotEmpty())
            assertEquals(items.size, items.map { it.key }.toSet().size)
        }
        assertTrue(Templates.apartment(listOf("Bedroom", "Kitchen")).any { it.label.startsWith("Bedroom") })
    }
    @Test fun `image sample fits within maximum decoded edge`() {
        assertEquals(1, boundedSampleSize(0, 200, 800))
        assertEquals(1, boundedSampleSize(800, 750, 800))
        assertEquals(2, boundedSampleSize(1500, 500, 800))
        assertEquals(8, boundedSampleSize(6000, 4000, 800))
        assertEquals(16, boundedSampleSize(12000, 9000, 800))
        assertEquals(1 shl 22, boundedSampleSize(Int.MAX_VALUE, Int.MAX_VALUE, 800))
    }
}
