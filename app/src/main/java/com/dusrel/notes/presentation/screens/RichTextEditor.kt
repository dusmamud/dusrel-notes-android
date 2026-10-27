package com.dusrel.notes.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.dusrel.notes.domain.model.ParagraphType
import com.dusrel.notes.domain.model.RichDocument
import com.dusrel.notes.domain.model.SpanStyleKind
import com.dusrel.notes.domain.model.hasSpan
import com.dusrel.notes.domain.model.lineRanges
import com.dusrel.notes.domain.model.normalized
import com.dusrel.notes.domain.model.paragraphType
import com.dusrel.notes.domain.model.toggleParagraph
import com.dusrel.notes.domain.model.toggleSpan
import com.dusrel.notes.presentation.components.SymbolIcon
import com.dusrel.notes.presentation.components.Symbols

/**
 * Compose editor that renders [RichDocument] spans IN the text field.
 *
 * How bullet/numbered prefixes work (read this before changing the code):
 * - Prefixes ("• " for BULLET, "1. ", "2. ", … for NUMBERED, counted in
 *   order over the document's NUMBERED paragraphs) are DISPLAY-ONLY chrome.
 *   They are inserted by [toAnnotatedString] and are never part of
 *   [RichDocument.text] and never saved.
 * - On every keystroke, [annotatedStringToDocument] converts the field's
 *   [AnnotatedString] back: it strips a bullet/numbered prefix from each
 *   display line (tolerantly — a few characters typed before the prefix are
 *   tolerated, so typing at the very start of a bullet line does not bake
 *   the prefix into the saved text), re-derives paragraph types from the
 *   surviving prefix / heading-size signals, and re-derives inline spans
 *   from the field's [SpanStyle] ranges (Compose maps those ranges through
 *   typing, which is what keeps spans correct while the user types).
 * - Consequence, by design: deleting a prefix does NOT un-bullet a line —
 *   the prefix re-renders from the stored paragraph type. Paragraph type
 *   changes only via the toolbar ([toggleParagraph]). Likewise, typing a
 *   literal "• " at a line start does not create a bullet; use the toolbar.
 * - Heading is rendered as a 20.sp [SpanStyle] over the paragraph text.
 *   That size-only span is ignored when converting back (only bold /
 *   italic / underline attributes become spans); heading-ness comes from
 *   the stored paragraph type, or, after line splits/merges, from a
 *   size span covering at least half the line.
 *
 * The editor owns its field state after the first composition: it reports
 * every change through [onChange] but only resets its field when the
 * incoming [document] differs from the last document it reported, so a
 * parent echoing [onChange] back does not move the cursor.
 */

private const val BULLET_PREFIX = "• "
private const val HEADING_FONT_SIZE = 20f
private val NUMBERED_PREFIX_AT_START = Regex("^\\d+\\. ")
private val NUMBERED_PREFIX_ANYWHERE_NEAR_START = Regex("\\d+\\. ")

/**
 * Display form of [document]: plain text plus bullet/numbered prefixes,
 * bold/italic/underline [SpanStyle]s, and 20.sp size for HEADING paragraphs.
 * Use [annotatedStringToDocument] to convert a field value back; never save
 * this string as note content.
 */
fun toAnnotatedString(document: RichDocument): AnnotatedString {
    val doc = document.normalized()
    val lines = doc.lineRanges()
    var numberedCount = 0
    // Display range of each paragraph's text (prefix excluded), for span clipping.
    val paragraphDisplayRanges = ArrayList<Pair<Int, Int>>()
    return buildAnnotatedString {
        lines.forEachIndexed { index, (lineStart, lineEnd) ->
            val type = doc.paragraphs[index].type
            val prefix = when (type) {
                ParagraphType.BULLET -> BULLET_PREFIX
                ParagraphType.NUMBERED -> {
                    numberedCount++
                    "$numberedCount. "
                }
                else -> ""
            }
            append(prefix)
            val textDisplayStart = length
            if (type == ParagraphType.HEADING) pushStyle(SpanStyle(fontSize = HEADING_FONT_SIZE.sp))
            append(doc.text.substring(lineStart, lineEnd))
            if (type == ParagraphType.HEADING) pop()
            paragraphDisplayRanges += textDisplayStart to length
            if (index != lines.lastIndex) append("\n")
        }
        doc.spans.forEach { span ->
            val style = when (span.style) {
                SpanStyleKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                SpanStyleKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                SpanStyleKind.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
            }
            lines.forEachIndexed { index, (lineStart, lineEnd) ->
                val clipStart = maxOf(span.start, lineStart)
                val clipEnd = minOf(span.end, lineEnd)
                if (clipStart < clipEnd) {
                    val (displayStart, _) = paragraphDisplayRanges[index]
                    addStyle(style, displayStart + (clipStart - lineStart), displayStart + (clipEnd - lineStart))
                }
            }
        }
    }
}

private data class DisplayLine(
    val displayStart: Int,
    val displayEnd: Int,
    val plainStart: Int,
    /** Display offset where the stripped prefix starts, or -1. */
    val prefixAt: Int,
    val prefixLength: Int,
    val type: ParagraphType,
)

/**
 * Convert a field [AnnotatedString] (display form, with prefixes) back to a
 * [RichDocument]. Paragraph types come from the display signals (a prefix
 * that survived on the line, or a heading-size span fully covering it) and
 * spans come from the [AnnotatedString]'s own [SpanStyle] ranges.
 */
fun annotatedStringToDocument(annotated: AnnotatedString): RichDocument {
    val displayText = annotated.text
    val displayLines = RichDocument(displayText).lineRanges()
    val sizeRanges = annotated.spanStyles.filter { range ->
        val size = range.item.fontSize
        size != TextUnit.Unspecified && size.value >= HEADING_FONT_SIZE - 1f
    }
    // First pass: per display line, find the prefix to strip (if any).
    data class StrippedLine(
        val displayStart: Int,
        val displayEnd: Int,
        val plainText: String,
        val prefixAt: Int,
        val prefixLength: Int,
        val type: ParagraphType,
    )
    val strippedLines = displayLines.map { (ds, de) ->
        val lineText = displayText.substring(ds, de)
        var prefixAt = -1
        var prefixLength = 0
        var stripped: String? = null
        var strippedType: ParagraphType? = null
        when {
            lineText.startsWith(BULLET_PREFIX) -> {
                prefixAt = ds
                prefixLength = BULLET_PREFIX.length
                stripped = lineText.removePrefix(BULLET_PREFIX)
                strippedType = ParagraphType.BULLET
            }
            NUMBERED_PREFIX_AT_START.containsMatchIn(lineText) -> {
                val match = NUMBERED_PREFIX_AT_START.find(lineText)!!
                prefixAt = ds + match.range.first
                prefixLength = match.value.length
                stripped = lineText.removeRange(match.range)
                strippedType = ParagraphType.NUMBERED
            }
            else -> {
                // Tolerant recovery: characters typed before the prefix.
                val bulletAt = lineText.indexOf(BULLET_PREFIX)
                val numberedMatch = NUMBERED_PREFIX_ANYWHERE_NEAR_START.find(lineText)
                when {
                    bulletAt in 1..8 -> {
                        prefixAt = ds + bulletAt
                        prefixLength = BULLET_PREFIX.length
                        stripped = lineText.removeRange(bulletAt, bulletAt + BULLET_PREFIX.length)
                        strippedType = ParagraphType.BULLET
                    }
                    numberedMatch != null && numberedMatch.range.first <= 10 -> {
                        prefixAt = ds + numberedMatch.range.first
                        prefixLength = numberedMatch.value.length
                        stripped = lineText.removeRange(numberedMatch.range)
                        strippedType = ParagraphType.NUMBERED
                    }
                }
            }
        }
        // A size span covering at least half the line counts as heading, so
        // typing one character at a heading line's edge (where Compose may
        // not extend the span) does not silently drop the heading.
        val isHeading = lineText.isNotEmpty() && sizeRanges.any { range ->
            val overlap = minOf(range.end, de) - maxOf(range.start, ds)
            overlap > 0 && overlap * 2 >= (de - ds)
        }
        val type = when {
            isHeading -> ParagraphType.HEADING
            strippedType != null -> strippedType
            else -> ParagraphType.NORMAL
        }
        StrippedLine(ds, de, stripped ?: lineText, prefixAt, prefixLength, type)
    }
    // Second pass: join stripped lines into plain text, recording offsets.
    val plainTextBuilder = StringBuilder()
    val lines = ArrayList<DisplayLine>()
    strippedLines.forEachIndexed { index, strippedLine ->
        lines += DisplayLine(
            strippedLine.displayStart,
            strippedLine.displayEnd,
            plainTextBuilder.length,
            strippedLine.prefixAt,
            strippedLine.prefixLength,
            strippedLine.type,
        )
        plainTextBuilder.append(strippedLine.plainText)
        if (index != strippedLines.lastIndex) plainTextBuilder.append('\n')
    }
    val plainText = plainTextBuilder.toString()

    fun displayToPlain(offset: Int): Int {
        val line = lines.firstOrNull { offset <= it.displayEnd } ?: lines.lastOrNull()
            ?: return offset.coerceIn(0, plainText.length)
        if (line.prefixAt >= 0 && offset in line.prefixAt..(line.prefixAt + line.prefixLength)) {
            return line.plainStart
        }
        val past = if (line.prefixAt >= 0 && offset > line.prefixAt + line.prefixLength) line.prefixLength else 0
        return (line.plainStart + (offset - line.displayStart) - past).coerceIn(0, plainText.length)
    }

    val spans = ArrayList<com.dusrel.notes.domain.model.TextSpan>()
    annotated.spanStyles.forEach { range ->
        val s = displayToPlain(range.start)
        val e = displayToPlain(range.end)
        if (s >= e) return@forEach
        val style = range.item
        if (style.fontWeight == FontWeight.Bold) spans += com.dusrel.notes.domain.model.TextSpan(s, e, SpanStyleKind.BOLD)
        if (style.fontStyle == FontStyle.Italic) spans += com.dusrel.notes.domain.model.TextSpan(s, e, SpanStyleKind.ITALIC)
        if (style.textDecoration?.contains(TextDecoration.Underline) == true) {
            spans += com.dusrel.notes.domain.model.TextSpan(s, e, SpanStyleKind.UNDERLINE)
        }
    }
    // Paragraph ranges in plain coordinates, from the rebuilt plain text.
    val plainLines = RichDocument(plainText).lineRanges()
    val paragraphs = plainLines.mapIndexed { index, (ps, pe) ->
        com.dusrel.notes.domain.model.ParagraphSpan(ps, pe, lines.getOrNull(index)?.type ?: ParagraphType.NORMAL)
    }
    return RichDocument(plainText, spans, paragraphs).normalized()
}

private fun displayToPlainOffset(
    offset: Int,
    doc: RichDocument,
    displayText: String,
): Int {
    // Map a display offset (prefixes present) to a plain offset, using the
    // current document's paragraph types to know each line's prefix length.
    val docLines = doc.lineRanges()
    val displayLines = RichDocument(displayText).lineRanges()
    var numberedCount = 0
    docLines.forEachIndexed { index, (ps, pe) ->
        val (ds, de) = displayLines.getOrNull(index) ?: return offset.coerceIn(0, doc.text.length)
        val type = doc.paragraphs.getOrNull(index)?.type ?: ParagraphType.NORMAL
        val prefixLength = when (type) {
            ParagraphType.BULLET -> BULLET_PREFIX.length
            ParagraphType.NUMBERED -> {
                numberedCount++
                "$numberedCount. ".length
            }
            else -> 0
        }
        if (offset <= de) {
            if (offset <= ds + prefixLength) return ps
            return (ps + (offset - ds) - prefixLength).coerceIn(0, doc.text.length)
        }
    }
    return doc.text.length
}

private fun plainToDisplayOffset(offset: Int, doc: RichDocument): Int {
    val docLines = doc.lineRanges()
    var numberedCount = 0
    var displayOffset = 0
    docLines.forEachIndexed { index, (ps, pe) ->
        val type = doc.paragraphs.getOrNull(index)?.type ?: ParagraphType.NORMAL
        val prefixLength = when (type) {
            ParagraphType.BULLET -> BULLET_PREFIX.length
            ParagraphType.NUMBERED -> {
                numberedCount++
                "$numberedCount. ".length
            }
            else -> 0
        }
        if (offset <= pe) {
            return if (offset <= ps) displayOffset + prefixLength
            else displayOffset + prefixLength + (offset - ps)
        }
        displayOffset += prefixLength + (pe - ps) + 1 // +1 for the newline
    }
    return displayOffset
}

private fun clampedRange(selection: TextRange, length: Int): TextRange {
    val s = selection.start.coerceIn(0, length)
    val e = selection.end.coerceIn(0, length)
    return if (s <= e) TextRange(s, e) else TextRange(e, s)
}

/**
 * Rich text editor: toolbar + [BasicTextField] rendering spans in place.
 *
 * @param document current document (plain text + spans; prefixes excluded)
 * @param onChange called with the new document on every edit or toolbar action
 * @param showToolbar when false, only the text field is shown
 */
@Composable
fun RichTextEditor(
    document: RichDocument,
    onChange: (RichDocument) -> Unit,
    modifier: Modifier = Modifier,
    showToolbar: Boolean = true,
) {
    var doc by remember { mutableStateOf(document.normalized()) }
    var lastReported by remember { mutableStateOf(document.normalized()) }
    var field by remember { mutableStateOf(TextFieldValue(toAnnotatedString(document.normalized()))) }

    // External document change (e.g. another note loaded): reset the field.
    // A parent echoing our own onChange back is equal to lastReported and
    // must not reset the field, or the cursor would jump on every keystroke.
    LaunchedEffect(document) {
        val incoming = document.normalized()
        if (incoming != lastReported) {
            doc = incoming
            lastReported = incoming
            val display = toAnnotatedString(incoming)
            field = TextFieldValue(display, clampedRange(field.selection, display.text.length))
        }
    }

    fun emitAndRebuild(newDoc: RichDocument, plainSelection: Pair<Int, Int>? = null) {
        val normalizedDoc = newDoc.normalized()
        doc = normalizedDoc
        lastReported = normalizedDoc
        onChange(normalizedDoc)
        val display = toAnnotatedString(normalizedDoc)
        val selection = plainSelection?.let { (s, e) ->
            val ds = plainToDisplayOffset(s, normalizedDoc)
            val de = plainToDisplayOffset(e, normalizedDoc)
            clampedRange(TextRange(ds, de), display.text.length)
        } ?: clampedRange(field.selection, display.text.length)
        field = TextFieldValue(display, selection)
    }

    Column(modifier = modifier) {
        if (showToolbar) {
            val selection = field.selection
            val displayText = field.text
            val plainStart = displayToPlainOffset(selection.start, doc, displayText)
            val plainEnd = displayToPlainOffset(selection.end, doc, displayText)
            val selStart = minOf(plainStart, plainEnd)
            val selEnd = maxOf(plainStart, plainEnd)
            val hasSelection = selEnd > selStart
            val firstLine = doc.text.substring(0, selStart.coerceIn(0, doc.text.length)).count { it == '\n' }
            val lastLine = doc.text.substring(0, selEnd.coerceIn(0, doc.text.length)).count { it == '\n' }
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = {
                    if (hasSelection) emitAndRebuild(toggleSpan(doc, selStart, selEnd, SpanStyleKind.BOLD), selStart to selEnd)
                }) {
                    SymbolIcon(
                        Symbols.FORMAT_BOLD,
                        filled = hasSelection && hasSpan(doc, selStart, selEnd, SpanStyleKind.BOLD),
                        size = 20.sp,
                    )
                }
                IconButton(onClick = {
                    if (hasSelection) emitAndRebuild(toggleSpan(doc, selStart, selEnd, SpanStyleKind.ITALIC), selStart to selEnd)
                }) {
                    SymbolIcon(
                        Symbols.FORMAT_ITALIC,
                        filled = hasSelection && hasSpan(doc, selStart, selEnd, SpanStyleKind.ITALIC),
                        size = 20.sp,
                    )
                }
                IconButton(onClick = {
                    if (hasSelection) emitAndRebuild(toggleSpan(doc, selStart, selEnd, SpanStyleKind.UNDERLINE), selStart to selEnd)
                }) {
                    SymbolIcon(
                        Symbols.FORMAT_UNDERLINED,
                        filled = hasSelection && hasSpan(doc, selStart, selEnd, SpanStyleKind.UNDERLINE),
                        size = 20.sp,
                    )
                }
                IconButton(onClick = {
                    emitAndRebuild(toggleParagraph(doc, firstLine, ParagraphType.HEADING), selStart to selEnd)
                }) {
                    SymbolIcon(
                        Symbols.TITLE,
                        filled = (firstLine..lastLine).any { paragraphType(doc, it) == ParagraphType.HEADING },
                        size = 20.sp,
                    )
                }
                IconButton(onClick = {
                    emitAndRebuild(toggleParagraph(doc, firstLine, ParagraphType.BULLET), selStart to selEnd)
                }) {
                    SymbolIcon(
                        Symbols.FORMAT_LIST_BULLETED,
                        filled = (firstLine..lastLine).any { paragraphType(doc, it) == ParagraphType.BULLET },
                        size = 20.sp,
                    )
                }
                IconButton(onClick = {
                    emitAndRebuild(toggleParagraph(doc, firstLine, ParagraphType.NUMBERED), selStart to selEnd)
                }) {
                    SymbolIcon(
                        Symbols.FORMAT_LIST_NUMBERED,
                        filled = (firstLine..lastLine).any { paragraphType(doc, it) == ParagraphType.NUMBERED },
                        size = 20.sp,
                    )
                }
            }
        }
        BasicTextField(
            value = field,
            onValueChange = { next ->
                val newDoc = annotatedStringToDocument(next.annotatedString)
                doc = newDoc
                lastReported = newDoc
                field = next
                onChange(newDoc)
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        )
    }
}
