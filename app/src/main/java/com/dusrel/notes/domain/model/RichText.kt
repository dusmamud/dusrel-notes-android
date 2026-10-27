package com.dusrel.notes.domain.model

/**
 * Rich text as REAL spans — the editing model.
 *
 * This replaces the old plain-text marker system (`**bold**`, `*italic*`,
 * `__underline__`, `# `, `- `, `1. `). Markers are NOT the storage format
 * any more: the visible text contains no markers, and formatting lives in
 * [RichDocument.spans] / [RichDocument.paragraphs] only.
 *
 * This file is pure Kotlin: no Android or Compose classes, so it is usable
 * from pure-JVM unit tests and from the Room layer (via [RichDocument.toSpansJson]).
 *
 * Conventions:
 * - Character offsets are UTF-16 code-unit indices into [RichDocument.text],
 *   half-open ranges [start, end). Newlines are ordinary characters; a
 *   paragraph is one line, i.e. the text between newlines.
 * - [RichDocument.paragraphs] always covers every line exactly once
 *   (see [RichDocument.normalized]); a line with no stored entry is NORMAL.
 * - [RichDocument.spans] may overlap (bold + italic on the same range) and
 *   may cross paragraph boundaries. Adjacent same-style spans are merged by
 *   [applySpan] but are otherwise preserved as stored.
 */

/** Inline character style. */
enum class SpanStyleKind { BOLD, ITALIC, UNDERLINE }

/** Paragraph-level type. Exactly one per paragraph. */
enum class ParagraphType { NORMAL, HEADING, BULLET, NUMBERED }

/** One inline style over [start, end) in the document text. */
data class TextSpan(val start: Int, val end: Int, val style: SpanStyleKind)

/** One paragraph's type over [start, end) — the line's text, newline excluded. */
data class ParagraphSpan(val start: Int, val end: Int, val type: ParagraphType)

/**
 * A note body: plain text plus its formatting.
 * [text] never contains formatting markers.
 */
data class RichDocument(
    val text: String = "",
    val spans: List<TextSpan> = emptyList(),
    val paragraphs: List<ParagraphSpan> = emptyList(),
)

/** Line ranges of this document's text: (start, end) per line, newline excluded. */
fun RichDocument.lineRanges(): List<Pair<Int, Int>> {
    val out = ArrayList<Pair<Int, Int>>()
    var start = 0
    for (i in text.indices) {
        if (text[i] == '\n') {
            out += start to i
            start = i + 1
        }
    }
    out += start to text.length
    return out
}

/**
 * Canonical form: spans clamped to the text, empty/inverted spans dropped,
 * spans in a stable (start, end, style) order, and paragraphs rebuilt to
 * cover every line exactly once (a stored paragraph is matched to its line
 * by start offset; lines without a stored entry become NORMAL).
 * Adjacent same-style spans are NOT merged here, so JSON round-trips are exact.
 */
fun RichDocument.normalized(): RichDocument {
    val len = text.length
    val cleanSpans = spans.mapNotNull { span ->
        val s = span.start.coerceIn(0, len)
        val e = span.end.coerceIn(s, len)
        if (s < e) TextSpan(s, e, span.style) else null
    }.sortedWith(compareBy({ it.start }, { it.end }, { it.style.ordinal }))
    val lines = lineRanges()
    val byStart = paragraphs.groupBy { it.start }
    val fullParagraphs = lines.map { (s, e) ->
        val type = byStart[s]?.firstOrNull()?.type ?: ParagraphType.NORMAL
        ParagraphSpan(s, e, type)
    }
    return RichDocument(text, cleanSpans, fullParagraphs)
}

/** Type of paragraph [paragraphIndex] (0-based line index), NORMAL if out of range. */
fun paragraphType(document: RichDocument, paragraphIndex: Int): ParagraphType {
    val paragraphs = document.normalized().paragraphs
    return paragraphs.getOrNull(paragraphIndex)?.type ?: ParagraphType.NORMAL
}

/** True when the whole [start, end) range is covered by [style] spans (union). */
fun hasSpan(document: RichDocument, start: Int, end: Int, style: SpanStyleKind): Boolean {
    val doc = document.normalized()
    val s = start.coerceIn(0, doc.text.length)
    val e = end.coerceIn(s, doc.text.length)
    if (s >= e) return false
    var coveredUntil = s
    for (span in doc.spans) {
        if (span.style != style) continue
        if (span.start > coveredUntil) break
        if (span.end > coveredUntil) coveredUntil = span.end
        if (coveredUntil >= e) return true
    }
    return coveredUntil >= e
}

/**
 * Add [style] over [start, end). Out-of-range ends are clamped, an empty
 * range is a no-op, and overlapping/adjacent same-style spans merge into one.
 * Other styles are untouched, so bold + italic can overlap.
 */
fun applySpan(document: RichDocument, start: Int, end: Int, style: SpanStyleKind): RichDocument {
    val doc = document.normalized()
    val s = start.coerceIn(0, doc.text.length)
    val e = end.coerceIn(s, doc.text.length)
    if (s >= e) return doc
    val (overlapping, rest) = doc.spans.partition {
        it.style == style && it.end >= s && it.start <= e
    }
    val merged = TextSpan(
        minOf(s, overlapping.minOfOrNull { it.start } ?: s),
        maxOf(e, overlapping.maxOfOrNull { it.end } ?: e),
        style,
    )
    return RichDocument(doc.text, rest + merged, doc.paragraphs).normalized()
}

/**
 * Remove [style] from [start, end). A same-style span straddling the range
 * is split into its surviving left/right parts; other styles are untouched.
 */
fun removeSpan(document: RichDocument, start: Int, end: Int, style: SpanStyleKind): RichDocument {
    val doc = document.normalized()
    val s = start.coerceIn(0, doc.text.length)
    val e = end.coerceIn(s, doc.text.length)
    if (s >= e) return doc
    val out = ArrayList<TextSpan>()
    for (span in doc.spans) {
        if (span.style != style || span.end <= s || span.start >= e) {
            out += span
        } else {
            if (span.start < s) out += TextSpan(span.start, s, style)
            if (span.end > e) out += TextSpan(e, span.end, style)
        }
    }
    return RichDocument(doc.text, out, doc.paragraphs).normalized()
}

/** [applySpan] when the range is not fully styled, [removeSpan] when it is. */
fun toggleSpan(document: RichDocument, start: Int, end: Int, style: SpanStyleKind): RichDocument =
    if (hasSpan(document, start, end, style)) removeSpan(document, start, end, style)
    else applySpan(document, start, end, style)

/**
 * Set paragraph [paragraphIndex] to [type]; if it already has [type] (or
 * [type] is NORMAL) it reverts to NORMAL. Out-of-range index: unchanged.
 */
fun toggleParagraph(document: RichDocument, paragraphIndex: Int, type: ParagraphType): RichDocument {
    val doc = document.normalized()
    if (paragraphIndex !in doc.paragraphs.indices) return doc
    val current = doc.paragraphs[paragraphIndex].type
    val next = if (type == ParagraphType.NORMAL || current == type) ParagraphType.NORMAL else type
    if (next == current) return doc
    val updated = doc.paragraphs.toMutableList()
    updated[paragraphIndex] = updated[paragraphIndex].copy(type = next)
    return doc.copy(paragraphs = updated)
}

/**
 * Best-effort text replacement (e.g. programmatic set, not typing): spans are
 * clamped to the new text, and each new line keeps the old line's type at the
 * same index, or NORMAL for added lines. Span offsets are NOT diff-tracked
 * through edits — the Compose editor path ([annotatedStringToDocument] in
 * RichTextEditor.kt) preserves spans through typing instead, and should be
 * preferred for user edits.
 */
fun updateText(document: RichDocument, newText: String): RichDocument {
    val oldTypes = document.normalized().paragraphs.map { it.type }
    val len = newText.length
    val cleanSpans = document.spans.mapNotNull { span ->
        val s = span.start.coerceIn(0, len)
        val e = span.end.coerceIn(s, len)
        if (s < e) TextSpan(s, e, span.style) else null
    }.sortedWith(compareBy({ it.start }, { it.end }, { it.style.ordinal }))
    val lines = RichDocument(newText).lineRanges()
    val paragraphs = lines.mapIndexed { index, (s, e) ->
        ParagraphSpan(s, e, oldTypes.getOrElse(index) { ParagraphType.NORMAL })
    }
    return RichDocument(newText, cleanSpans, paragraphs)
}

// ---------------------------------------------------------------------------
// JSON serialisation (hand-rolled: the main source set has no JSON dependency).
// Shape:
// {"text":"...","spans":[{"start":0,"end":5,"style":"BOLD"}],
//  "paragraphs":[{"start":0,"end":5,"type":"HEADING"}]}
// The [normalized] document is written, so a parse of the output is exactly
// equal to the normalized input: the round-trip loses nothing.
// ---------------------------------------------------------------------------

private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    for (ch in value) {
        when (ch) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> {
                if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
    }
    append('"')
}

/** Serialise this document (normalized) to its spans-JSON string. */
fun RichDocument.toSpansJson(): String {
    val doc = normalized()
    return buildString {
        append("{\"text\":")
        appendJsonString(doc.text)
        append(",\"spans\":[")
        doc.spans.forEachIndexed { i, span ->
            if (i > 0) append(',')
            append("{\"start\":${span.start},\"end\":${span.end},\"style\":\"${span.style.name}\"}")
        }
        append("],\"paragraphs\":[")
        doc.paragraphs.forEachIndexed { i, paragraph ->
            if (i > 0) append(',')
            append("{\"start\":${paragraph.start},\"end\":${paragraph.end},\"type\":\"${paragraph.type.name}\"}")
        }
        append("]}")
    }
}

private class JsonParser(private val input: String) {
    private var pos = 0

    fun parseTop(): Any? {
        val value = parseValue()
        skipWs()
        if (pos != input.length) throw IllegalArgumentException("Trailing characters in spans JSON")
        return value
    }

    private fun skipWs() {
        while (pos < input.length && input[pos].isWhitespace()) pos++
    }

    private fun peek(): Char {
        skipWs()
        if (pos >= input.length) throw IllegalArgumentException("Unexpected end of spans JSON")
        return input[pos]
    }

    private fun expect(ch: Char) {
        if (peek() != ch) throw IllegalArgumentException("Expected '$ch' in spans JSON at $pos")
        pos++
    }

    private fun parseValue(): Any? = when (peek()) {
        '{' -> parseObject()
        '[' -> parseArray()
        '"' -> parseString()
        't' -> parseLiteral("true", true)
        'f' -> parseLiteral("false", false)
        'n' -> parseLiteral("null", null)
        else -> parseNumber()
    }

    private fun parseLiteral(word: String, value: Any?): Any? {
        if (!input.startsWith(word, pos)) throw IllegalArgumentException("Invalid literal in spans JSON")
        pos += word.length
        return value
    }

    private fun parseNumber(): Long {
        val start = pos
        if (pos < input.length && input[pos] == '-') pos++
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.' || input[pos] == 'e' || input[pos] == 'E' || input[pos] == '+' || input[pos] == '-')) pos++
        val raw = input.substring(start, pos)
        return raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong()
            ?: throw IllegalArgumentException("Invalid number '$raw' in spans JSON")
    }

    private fun parseString(): String {
        expect('"')
        val out = StringBuilder()
        while (true) {
            if (pos >= input.length) throw IllegalArgumentException("Unterminated string in spans JSON")
            val ch = input[pos++]
            if (ch == '"') break
            if (ch != '\\') {
                out.append(ch)
            } else {
                if (pos >= input.length) throw IllegalArgumentException("Unterminated escape in spans JSON")
                when (val esc = input[pos++]) {
                    '"' -> out.append('"')
                    '\\' -> out.append('\\')
                    '/' -> out.append('/')
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000C')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'u' -> {
                        if (pos + 4 > input.length) throw IllegalArgumentException("Bad unicode escape in spans JSON")
                        val code = input.substring(pos, pos + 4).toIntOrNull(16)
                            ?: throw IllegalArgumentException("Bad unicode escape in spans JSON")
                        pos += 4
                        out.append(code.toChar())
                    }
                    else -> throw IllegalArgumentException("Unknown escape '\\$esc' in spans JSON")
                }
            }
        }
        return out.toString()
    }

    private fun parseObject(): Map<String, Any?> {
        expect('{')
        val out = LinkedHashMap<String, Any?>()
        if (peek() == '}') {
            pos++
            return out
        }
        while (true) {
            val key = parseString()
            expect(':')
            out[key] = parseValue()
            when (peek()) {
                ',' -> pos++
                '}' -> {
                    pos++
                    return out
                }
                else -> throw IllegalArgumentException("Expected ',' or '}' in spans JSON")
            }
        }
    }

    private fun parseArray(): List<Any?> {
        expect('[')
        val out = ArrayList<Any?>()
        if (peek() == ']') {
            pos++
            return out
        }
        while (true) {
            out += parseValue()
            when (peek()) {
                ',' -> pos++
                ']' -> {
                    pos++
                    return out
                }
                else -> throw IllegalArgumentException("Expected ',' or ']' in spans JSON")
            }
        }
    }
}

private fun Any?.asInt(): Int? = when (this) {
    is Long -> this.toInt()
    is Int -> this
    is Double -> this.toInt()
    else -> null
}

private fun Any?.asObjectList(): List<Map<String, Any?>> =
    (this as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()

/**
 * Parse a [RichDocument.toSpansJson] string. Unknown fields and unknown
 * enum names are ignored; structurally invalid JSON throws
 * IllegalArgumentException (callers loading from storage should catch it
 * and fall back to [migrateMarkersToSpans] on the plain text).
 */
fun richDocumentFromSpansJson(json: String): RichDocument {
    val top = JsonParser(json).parseTop() as? Map<String, Any?>
        ?: throw IllegalArgumentException("Spans JSON must be an object")
    val text = top["text"] as? String ?: ""
    val spans = top["spans"].asObjectList().mapNotNull { obj ->
        val s = obj["start"].asInt() ?: return@mapNotNull null
        val e = obj["end"].asInt() ?: return@mapNotNull null
        val style = (obj["style"] as? String)?.let { name ->
            runCatching { SpanStyleKind.valueOf(name) }.getOrNull()
        } ?: return@mapNotNull null
        TextSpan(s, e, style)
    }
    val paragraphs = top["paragraphs"].asObjectList().mapNotNull { obj ->
        val s = obj["start"].asInt() ?: return@mapNotNull null
        val e = obj["end"].asInt() ?: return@mapNotNull null
        val type = (obj["type"] as? String)?.let { name ->
            runCatching { ParagraphType.valueOf(name) }.getOrNull()
        } ?: return@mapNotNull null
        ParagraphSpan(s, e, type)
    }
    return RichDocument(text, spans, paragraphs).normalized()
}

// ---------------------------------------------------------------------------
// One-way migration from the old marker format. Markers are stripped from
// the visible text and converted to spans/paragraphs; the marker text itself
// is never stored afterwards. An unmatched opener (no closer) is left as
// literal text rather than silently deleted.
// ---------------------------------------------------------------------------

private val NUMBERED_PREFIX = Regex("^\\d+\\. ")

private fun parseInlineMarkers(input: String, out: StringBuilder, spans: MutableList<TextSpan>) {
    var i = 0
    while (i < input.length) {
        val marker = when {
            input.startsWith("**", i) -> "**"
            input.startsWith("__", i) -> "__"
            input.startsWith("*", i) -> "*"
            else -> null
        }
        if (marker == null) {
            out.append(input[i])
            i++
            continue
        }
        val close = input.indexOf(marker, i + marker.length)
        if (close == -1) {
            // Unmatched opener: keep it visible instead of dropping characters.
            out.append(marker)
            i += marker.length
            continue
        }
        val style = when (marker) {
            "**" -> SpanStyleKind.BOLD
            "__" -> SpanStyleKind.UNDERLINE
            else -> SpanStyleKind.ITALIC
        }
        val innerStart = out.length
        parseInlineMarkers(input.substring(i + marker.length, close), out, spans)
        if (out.length > innerStart) spans += TextSpan(innerStart, out.length, style)
        i = close + marker.length
    }
}

/**
 * Convert an old marker-formatted note body to a [RichDocument]:
 * `# ` -> HEADING, `- ` -> BULLET, `N. ` -> NUMBERED paragraphs, and
 * `**bold**`, `*italic*`, `__underline__` -> spans. All markers are
 * stripped from the resulting [RichDocument.text]. Line endings are
 * normalized to `\n`. This is one-way: there is no conversion back.
 */
fun migrateMarkersToSpans(oldMarkerText: String): RichDocument {
    val rawLines = oldMarkerText.split('\n')
    val plain = StringBuilder()
    val spans = ArrayList<TextSpan>()
    val paragraphs = ArrayList<ParagraphSpan>()
    rawLines.forEachIndexed { index, raw ->
        val line = raw.removeSuffix("\r")
        var body = line
        var type = ParagraphType.NORMAL
        when {
            body.startsWith("# ") -> {
                type = ParagraphType.HEADING
                body = body.removePrefix("# ")
            }
            body.startsWith("- ") -> {
                type = ParagraphType.BULLET
                body = body.removePrefix("- ")
            }
            NUMBERED_PREFIX.containsMatchIn(body) -> {
                type = ParagraphType.NUMBERED
                body = body.replaceFirst(NUMBERED_PREFIX, "")
            }
        }
        val lineStart = plain.length
        parseInlineMarkers(body, plain, spans)
        paragraphs += ParagraphSpan(lineStart, plain.length, type)
        if (index != rawLines.lastIndex) plain.append('\n')
    }
    return RichDocument(plain.toString(), spans, paragraphs).normalized()
}
