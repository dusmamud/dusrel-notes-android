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

    @Test fun widthRoundTripsAndOldFormatDefaultsTo6() {
        val strokes = listOf(
            DoodleStroke(1, listOf(DoodlePoint(0f, 0f), DoodlePoint(1f, 1f)), 12f),
            DoodleStroke(2, listOf(DoodlePoint(2f, 2f)), 3f),
        )
        val decoded = DoodleCodec.decode(DoodleCodec.encode(strokes))
        assertEquals(strokes, decoded)
        // Old format without a "w" field (as stored before this change).
        val old = DoodleCodec.decode("[{\"c\":5,\"p\":[[1.0,2.0],[3.0,4.0]]}]")
        assertEquals(1, old.size)
        assertEquals(6f, old[0].width, 0.001f)
    }
}
