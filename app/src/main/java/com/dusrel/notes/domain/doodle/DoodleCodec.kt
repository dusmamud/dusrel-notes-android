package com.dusrel.notes.domain.doodle

/** Pure doodle serialisation: strokes -> compact JSON string and back. */
data class DoodlePoint(val x: Float, val y: Float)
data class DoodleStroke(val color: Int, val points: List<DoodlePoint>)

object DoodleCodec {
    fun encode(strokes: List<DoodleStroke>): String {
        if (strokes.isEmpty()) return ""
        val sb = StringBuilder("[")
        strokes.forEachIndexed { si, s ->
            if (si > 0) sb.append(',')
            sb.append("{\"c\":").append(s.color).append(",\"p\":[")
            s.points.forEachIndexed { pi, p ->
                if (pi > 0) sb.append(',')
                sb.append('[').append(p.x).append(',').append(p.y).append(']')
            }
            sb.append("]}")
        }
        return sb.append(']').toString()
    }

    fun decode(data: String): List<DoodleStroke> {
        val out = mutableListOf<DoodleStroke>()
        if (data.isBlank()) return out
        // Tiny hand-rolled parser for the exact format encode() produces.
        // Structure is ambiguous from numbers alone, so re-parse with a scanner:
        var i = 0
        fun skipWs() { while (i < data.length && data[i].isWhitespace()) i++ }
        fun readNumber(): Float {
            skipWs()
            val start = i
            while (i < data.length && (data[i].isDigit() || data[i] == '-' || data[i] == '.')) i++
            return data.substring(start, i).toFloat()
        }
        skipWs()
        if (i >= data.length || data[i] != '[') return out
        i++
        while (true) {
            skipWs()
            if (i >= data.length) break
            if (data[i] == ']') { i++; break }
            if (data[i] == ',') { i++; continue }
            // {"c":<color>,"p":[[x,y],...]}
            val cIdx = data.indexOf("\"c\"", i)
            if (cIdx < 0) break
            i = data.indexOf(':', cIdx) + 1
            val color = readNumber().toInt()
            val pIdx = data.indexOf("\"p\"", i)
            i = data.indexOf('[', pIdx) + 1
            val points = mutableListOf<DoodlePoint>()
            while (true) {
                skipWs()
                if (i >= data.length || data[i] == ']') { i++; break }
                if (data[i] == ',') { i++; continue }
                if (data[i] == '[') {
                    i++
                    val x = readNumber()
                    skipWs(); if (i < data.length && data[i] == ',') i++
                    val y = readNumber()
                    skipWs(); if (i < data.length && data[i] == ']') i++
                    points += DoodlePoint(x, y)
                } else break
            }
            out += DoodleStroke(color, points)
            skipWs()
            if (i < data.length && data[i] == ',') { i++; continue }
            if (i < data.length && data[i] == ']') break
        }
        return out
    }
}
