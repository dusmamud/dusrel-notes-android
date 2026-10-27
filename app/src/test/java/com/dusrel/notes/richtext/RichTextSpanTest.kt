package com.dusrel.notes.richtext

import com.dusrel.notes.domain.model.ParagraphType
import com.dusrel.notes.domain.model.RichDocument
import com.dusrel.notes.domain.model.SpanStyleKind
import com.dusrel.notes.domain.model.TextSpan
import com.dusrel.notes.domain.model.applySpan
import com.dusrel.notes.domain.model.hasSpan
import com.dusrel.notes.domain.model.normalized
import com.dusrel.notes.domain.model.paragraphType
import com.dusrel.notes.domain.model.removeSpan
import com.dusrel.notes.domain.model.richDocumentFromSpansJson
import com.dusrel.notes.domain.model.toSpansJson
import com.dusrel.notes.domain.model.toggleParagraph
import com.dusrel.notes.domain.model.toggleSpan
import com.dusrel.notes.domain.model.updateText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextSpanTest {

    /** Normalized document: one NORMAL paragraph per line, no spans. */
    private fun doc(text: String) = RichDocument(text).normalized()

    @Test fun applyBoldToRange() {
        val out = applySpan(doc("hello world"), 6, 11, SpanStyleKind.BOLD)
        assertEquals("hello world", out.text)
        assertEquals(listOf(TextSpan(6, 11, SpanStyleKind.BOLD)), out.spans)
        assertTrue(hasSpan(out, 6, 11, SpanStyleKind.BOLD))
        assertFalse(hasSpan(out, 0, 5, SpanStyleKind.BOLD))
    }

    @Test fun applyItalicAndUnderlineToRanges() {
        var out = applySpan(doc("hello world"), 0, 5, SpanStyleKind.ITALIC)
        out = applySpan(out, 6, 11, SpanStyleKind.UNDERLINE)
        assertEquals(
            listOf(TextSpan(0, 5, SpanStyleKind.ITALIC), TextSpan(6, 11, SpanStyleKind.UNDERLINE)),
            out.spans,
        )
    }

    @Test fun applyMergesOverlappingSameStyle() {
        var out = applySpan(doc("hello world"), 0, 5, SpanStyleKind.BOLD)
        out = applySpan(out, 3, 8, SpanStyleKind.BOLD)
        assertEquals(listOf(TextSpan(0, 8, SpanStyleKind.BOLD)), out.spans)
    }

    @Test fun applyEmptyRangeIsNoOp() {
        val out = applySpan(doc("hello"), 2, 2, SpanStyleKind.BOLD)
        assertTrue(out.spans.isEmpty())
    }

    @Test fun applyClampsOutOfRange() {
        val out = applySpan(doc("hello"), -3, 99, SpanStyleKind.BOLD)
        assertEquals(listOf(TextSpan(0, 5, SpanStyleKind.BOLD)), out.spans)
    }

    @Test fun removeSpanSplitsStraddlingSpan() {
        var out = applySpan(doc("hello world"), 0, 11, SpanStyleKind.BOLD)
        out = removeSpan(out, 3, 6, SpanStyleKind.BOLD)
        assertEquals(
            listOf(TextSpan(0, 3, SpanStyleKind.BOLD), TextSpan(6, 11, SpanStyleKind.BOLD)),
            out.spans,
        )
    }

    @Test fun removeSpanLeavesOtherStylesAlone() {
        var out = applySpan(doc("hello world"), 0, 11, SpanStyleKind.BOLD)
        out = applySpan(out, 0, 11, SpanStyleKind.ITALIC)
        out = removeSpan(out, 0, 11, SpanStyleKind.BOLD)
        assertEquals(listOf(TextSpan(0, 11, SpanStyleKind.ITALIC)), out.spans)
    }

    @Test fun toggleSpanAppliesThenRemoves() {
        val applied = toggleSpan(doc("hello"), 0, 5, SpanStyleKind.BOLD)
        assertEquals(listOf(TextSpan(0, 5, SpanStyleKind.BOLD)), applied.spans)
        val removed = toggleSpan(applied, 0, 5, SpanStyleKind.BOLD)
        assertTrue(removed.spans.isEmpty())
    }

    @Test fun overlappingBoldAndItalicOnSameRange() {
        var out = applySpan(doc("hello"), 0, 5, SpanStyleKind.BOLD)
        out = applySpan(out, 0, 5, SpanStyleKind.ITALIC)
        assertEquals(
            listOf(TextSpan(0, 5, SpanStyleKind.BOLD), TextSpan(0, 5, SpanStyleKind.ITALIC)),
            out.spans,
        )
        assertTrue(hasSpan(out, 0, 5, SpanStyleKind.BOLD))
        assertTrue(hasSpan(out, 0, 5, SpanStyleKind.ITALIC))
    }

    @Test fun headingAndBulletParagraphConversion() {
        val base = doc("Title\nitem\nplain")
        val headed = toggleParagraph(base, 0, ParagraphType.HEADING)
        assertEquals(ParagraphType.HEADING, paragraphType(headed, 0))
        assertEquals(ParagraphType.NORMAL, paragraphType(headed, 1))
        val bulleted = toggleParagraph(headed, 1, ParagraphType.BULLET)
        assertEquals(ParagraphType.BULLET, paragraphType(bulleted, 1))
        assertEquals(ParagraphType.HEADING, paragraphType(bulleted, 0))
        assertEquals(ParagraphType.NORMAL, paragraphType(bulleted, 2))
        assertEquals("Title\nitem\nplain", bulleted.text)
    }

    @Test fun toggleParagraphOffReturnsNormal() {
        val headed = toggleParagraph(doc("Title"), 0, ParagraphType.HEADING)
        val off = toggleParagraph(headed, 0, ParagraphType.HEADING)
        assertEquals(ParagraphType.NORMAL, paragraphType(off, 0))
    }

    @Test fun toggleParagraphOutOfRangeIsNoOp() {
        val base = doc("only")
        assertEquals(base, toggleParagraph(base, 5, ParagraphType.HEADING))
    }

    @Test fun spansJsonRoundTripLosesNothing() {
        var out = RichDocument(
            "He said \"hi\"\nsecond line • ünïcode\ttab",
            emptyList(),
            emptyList(),
        )
        out = applySpan(out, 0, 2, SpanStyleKind.BOLD)
        out = applySpan(out, 0, 15, SpanStyleKind.ITALIC)
        out = applySpan(out, 20, 26, SpanStyleKind.UNDERLINE)
        out = toggleParagraph(out, 0, ParagraphType.HEADING)
        out = toggleParagraph(out, 1, ParagraphType.BULLET)
        val json = out.toSpansJson()
        val back = richDocumentFromSpansJson(json)
        assertEquals(out, back)
        // And the JSON is stable: a second round-trip is identical text.
        assertEquals(json, back.toSpansJson())
    }

    @Test fun spansJsonEmptyDocumentRoundTrip() {
        val out = RichDocument("", emptyList(), emptyList())
        assertEquals(out.normalized(), richDocumentFromSpansJson(out.toSpansJson()))
    }

    @Test fun spansJsonRejectsMalformedInput() {
        try {
            richDocumentFromSpansJson("{not json")
            assertTrue("expected IllegalArgumentException", false)
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.isNotEmpty())
        }
    }

    @Test fun updateTextKeepsLineTypesByIndex() {
        val headed = toggleParagraph(doc("Title\nbody"), 0, ParagraphType.HEADING)
        val updated = updateText(headed, "New title\nnew body")
        assertEquals(ParagraphType.HEADING, paragraphType(updated, 0))
        assertEquals(ParagraphType.NORMAL, paragraphType(updated, 1))
        assertEquals("New title\nnew body", updated.text)
    }
}
