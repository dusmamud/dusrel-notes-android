package com.dusrel.notes.richtext

import com.dusrel.notes.domain.doodle.DoodleCodec
import com.dusrel.notes.domain.doodle.DoodlePoint
import com.dusrel.notes.domain.doodle.DoodleStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoodleCodecTest {
    @Test fun emptyEncodesToEmptyAndDecodesToEmpty() {
        assertEquals("", DoodleCodec.encode(emptyList()))
        assertTrue(DoodleCodec.decode("").isEmpty())
        assertTrue(DoodleCodec.decode("   ").isEmpty())
    }

    @Test fun roundTripPreservesStrokesAndPoints() {
        val strokes = listOf(
            DoodleStroke(-16777216, listOf(DoodlePoint(1.5f, 2.5f), DoodlePoint(10f, 20f))),
            DoodleStroke(-65536, listOf(DoodlePoint(0f, 0f))),
        )
        assertEquals(strokes, DoodleCodec.decode(DoodleCodec.encode(strokes)))
    }

    @Test fun singlePointStrokeSurvives() {
        val strokes = listOf(DoodleStroke(123, listOf(DoodlePoint(3f, 4f))))
        val decoded = DoodleCodec.decode(DoodleCodec.encode(strokes))
        assertEquals(1, decoded.size)
        assertEquals(3f, decoded[0].points[0].x, 0.001f)
    }
}
