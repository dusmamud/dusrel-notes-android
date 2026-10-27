package com.dusrel.notes.richtext

import com.dusrel.notes.domain.model.ParagraphType
import com.dusrel.notes.domain.model.RichDocument
import com.dusrel.notes.domain.model.SpanStyleKind
import com.dusrel.notes.domain.model.TextSpan
import com.dusrel.notes.domain.model.migrateMarkersToSpans
import com.dusrel.notes.domain.model.paragraphType
import com.dusrel.notes.domain.model.richDocumentFromSpansJson
import com.dusrel.notes.domain.model.toSpansJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migration tests for the old plain-text marker format. The old marker
 * helpers no longer exist: markers are a one-way input to
 * [migrateMarkersToSpans] only, never the storage format. This file keeps
 * its historical name so existing test references still resolve; the class
 * itself is the migration test.
 */
class RichTextMigrationTest {

    @Test fun headingLineMigrates() {
        val doc = migrateMarkersToSpans("# Hello")
        assertEquals("Hello", doc.text)
        assertEquals(ParagraphType.HEADING, paragraphType(doc, 0))
        assertTrue(doc.spans.isEmpty())
    }

    @Test fun bulletAndNumberedLinesMigrate() {
        val doc = migrateMarkersToSpans("- one\n1. two\n3. three")
        assertEquals("one\ntwo\nthree", doc.text)
        assertEquals(ParagraphType.BULLET, paragraphType(doc, 0))
        assertEquals(ParagraphType.NUMBERED, paragraphType(doc, 1))
        assertEquals(ParagraphType.NUMBERED, paragraphType(doc, 2))
    }

    @Test fun inlineMarkersBecomeSpansWithMarkersStripped() {
        val doc = migrateMarkersToSpans("a **b** c *d* e __f__")
        assertEquals("a b c d e f", doc.text)
        assertFalse(doc.text.contains("*"))
        assertFalse(doc.text.contains("__"))
        assertEquals(
            listOf(
                TextSpan(2, 3, SpanStyleKind.BOLD),
                TextSpan(6, 7, SpanStyleKind.ITALIC),
                TextSpan(10, 11, SpanStyleKind.UNDERLINE),
            ),
            doc.spans,
        )
    }

    @Test fun combinedMarkerNoteMigrates() {
        val doc = migrateMarkersToSpans(
            "# Shopping\n- **milk** and *eggs*\n1. call __mom__",
        )
        assertEquals("Shopping\nmilk and eggs\ncall mom", doc.text)
        assertEquals(ParagraphType.HEADING, paragraphType(doc, 0))
        assertEquals(ParagraphType.BULLET, paragraphType(doc, 1))
        assertEquals(ParagraphType.NUMBERED, paragraphType(doc, 2))
        assertTrue(doc.spans.contains(TextSpan(9, 13, SpanStyleKind.BOLD)))
        assertTrue(doc.spans.contains(TextSpan(18, 22, SpanStyleKind.ITALIC)))
        assertTrue(doc.spans.contains(TextSpan(28, 31, SpanStyleKind.UNDERLINE)))
    }

    @Test fun nestedMarkersMigrateToOverlappingSpans() {
        val doc = migrateMarkersToSpans("**bold and *italic* inside**")
        assertEquals("bold and italic inside", doc.text)
        assertEquals(TextSpan(0, 22, SpanStyleKind.BOLD), doc.spans.single { it.style == SpanStyleKind.BOLD })
        assertEquals(TextSpan(9, 15, SpanStyleKind.ITALIC), doc.spans.single { it.style == SpanStyleKind.ITALIC })
    }

    @Test fun unmatchedMarkerLeftAsLiteralText() {
        val doc = migrateMarkersToSpans("a **b and c")
        assertEquals("a **b and c", doc.text)
        assertTrue(doc.spans.isEmpty())
    }

    @Test fun emptyTextMigratesToEmptyDocument() {
        val doc = migrateMarkersToSpans("")
        assertEquals("", doc.text)
        assertEquals(ParagraphType.NORMAL, paragraphType(doc, 0))
    }

    @Test fun migratedDocumentSurvivesJsonRoundTrip() {
        val doc = migrateMarkersToSpans("# Title\n- **bold** text")
        val back: RichDocument = richDocumentFromSpansJson(doc.toSpansJson())
        assertEquals(doc, back)
        assertEquals("Title\nbold text", back.text)
    }
}
