package com.dusrel.notes.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dusrel.notes.domain.doodle.DoodleCodec
import com.dusrel.notes.domain.doodle.DoodlePoint
import com.dusrel.notes.domain.doodle.DoodleStroke
import com.dusrel.notes.presentation.components.SymbolIcon
import com.dusrel.notes.presentation.components.Symbols

enum class DoodleTool(val label: String, val icon: String) {
    PEN("Pen", Symbols.EDIT),
    MARKER("Marker", Symbols.BRUSH),
    HIGHLIGHTER("Highlighter", Symbols.ARTICLE),
    ERASER("Eraser", Symbols.CLOSE),
}

private data class StrokeSizePreset(val label: String, val widthPx: Float, val displayDp: Int)

/**
 * Professional Doodle Studio Canvas.
 * Supports:
 * - 4 Drawing tools: Pen (solid), Marker (bold), Highlighter (translucent alpha 0.45), Eraser (point proximity)
 * - 5 calibrated stroke widths: 2dp (4f), 4dp (8f), 8dp (16f), 14dp (28f), 24dp (48f)
 * - 8 Color presets + Undo / Redo / Clear
 */
@Composable
fun DoodleCanvas(
    initialData: String,
    onStrokesChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    var strokes by remember(initialData) { mutableStateOf(DoodleCodec.decode(initialData)) }
    var redoStack by remember { mutableStateOf<List<List<DoodleStroke>>>(emptyList()) }
    var activeTool by remember { mutableStateOf(DoodleTool.PEN) }
    var currentColor by remember { mutableStateOf(if (isDark) Color(0xFFE2E2E2) else Color(0xFF1A1C21)) }

    val strokePresets = remember {
        listOf(
            StrokeSizePreset("2dp", 4f, 2),
            StrokeSizePreset("4dp", 8f, 4),
            StrokeSizePreset("8dp", 16f, 8),
            StrokeSizePreset("14dp", 28f, 14),
            StrokeSizePreset("24dp", 48f, 24),
        )
    }
    var currentWidthPreset by remember { mutableStateOf(strokePresets[1]) } // Default 4dp / 8f

    val palette = listOf(
        if (isDark) Color(0xFFE2E2E2) else Color(0xFF1A1C21),
        Color(0xFFE53935), // Crimson Red
        Color(0xFF1E88E5), // Vivid Blue
        Color(0xFF43A047), // Fresh Green
        Color(0xFFFB8C00), // Vibrant Amber
        Color(0xFF8E24AA), // Deep Purple
        Color(0xFF00897B), // Teal
        Color(0xFFE91E63), // Pink
    )

    fun isNear(p: DoodlePoint, touch: Offset, radius: Float): Boolean {
        val dx = p.x - touch.x
        val dy = p.y - touch.y
        return (dx * dx + dy * dy) <= (radius * radius)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Tool & Action Bar (Pen, Marker, Highlighter, Eraser + Undo, Redo, Clear)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DoodleTool.values().forEach { tool ->
                    val isSelected = activeTool == tool
                    if (isSelected) {
                        Button(
                            onClick = { activeTool = tool },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            SymbolIcon(tool.icon, size = 16.sp)
                            Text(" ${tool.label}", fontSize = 12.sp, modifier = Modifier.padding(start = 2.dp))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { activeTool = tool },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            SymbolIcon(tool.icon, size = 16.sp)
                            Text(" ${tool.label}", fontSize = 12.sp, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (strokes.isNotEmpty()) {
                            redoStack = redoStack + listOf(strokes)
                            strokes = strokes.dropLast(1)
                            onStrokesChanged(DoodleCodec.encode(strokes))
                        }
                    },
                    enabled = strokes.isNotEmpty()
                ) {
                    SymbolIcon(
                        Symbols.UNDO,
                        size = 20.sp,
                        color = if (strokes.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                IconButton(
                    onClick = {
                        if (redoStack.isNotEmpty()) {
                            val next = redoStack.last()
                            redoStack = redoStack.dropLast(1)
                            strokes = next
                            onStrokesChanged(DoodleCodec.encode(strokes))
                        }
                    },
                    enabled = redoStack.isNotEmpty()
                ) {
                    SymbolIcon(
                        Symbols.HISTORY,
                        size = 20.sp,
                        color = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                IconButton(
                    onClick = {
                        if (strokes.isNotEmpty()) {
                            redoStack = redoStack + listOf(strokes)
                            strokes = emptyList()
                            onStrokesChanged("")
                        }
                    },
                    enabled = strokes.isNotEmpty()
                ) {
                    SymbolIcon(
                        Symbols.DELETE,
                        size = 20.sp,
                        color = if (strokes.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }

        // Color Swatches Bar (Hidden when Eraser is active)
        if (activeTool != DoodleTool.ERASER) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                palette.forEach { c ->
                    val isSelected = c == currentColor
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .clickable { currentColor = c },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            SymbolIcon(
                                Symbols.CHECK,
                                size = 14.sp,
                                color = if (c == Color(0xFFE2E2E2) || c == Color(0xFFFB8C00)) Color.Black else Color.White
                            )
                        }
                    }
                }
            }

            // Stroke Width Selector (5 presets)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text("Stroke:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                strokePresets.forEach { preset ->
                    val isSelected = preset == currentWidthPreset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { currentWidthPreset = preset }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size((preset.displayDp.coerceIn(3, 14)).dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                preset.label,
                                fontSize = 11.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Canvas Surface
        val canvasBg = if (isDark) Color(0xFF1B1D22) else Color(0xFFFFFFFF)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .background(canvasBg)
                .pointerInput(activeTool, currentColor, currentWidthPreset) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (activeTool == DoodleTool.ERASER) {
                                val radius = 32f
                                val filtered = strokes.filterNot { s -> s.points.any { isNear(it, offset, radius) } }
                                if (filtered.size != strokes.size) {
                                    strokes = filtered
                                    onStrokesChanged(DoodleCodec.encode(strokes))
                                }
                            } else {
                                val strokeColor = when (activeTool) {
                                    DoodleTool.HIGHLIGHTER -> currentColor.copy(alpha = 0.45f).toArgb()
                                    else -> currentColor.toArgb()
                                }
                                val strokeWidth = when (activeTool) {
                                    DoodleTool.HIGHLIGHTER -> currentWidthPreset.widthPx * 2.2f
                                    DoodleTool.MARKER -> currentWidthPreset.widthPx * 1.5f
                                    else -> currentWidthPreset.widthPx
                                }
                                strokes = strokes + DoodleStroke(
                                    color = strokeColor,
                                    points = listOf(DoodlePoint(offset.x, offset.y)),
                                    width = strokeWidth
                                )
                            }
                        },
                        onDrag = { change, _ ->
                            if (activeTool == DoodleTool.ERASER) {
                                val radius = 32f
                                val filtered = strokes.filterNot { s -> s.points.any { isNear(it, change.position, radius) } }
                                if (filtered.size != strokes.size) {
                                    strokes = filtered
                                    onStrokesChanged(DoodleCodec.encode(strokes))
                                }
                            } else {
                                val last = strokes.lastOrNull() ?: return@detectDragGestures
                                val updated = last.copy(
                                    points = last.points + DoodlePoint(change.position.x, change.position.y)
                                )
                                strokes = strokes.dropLast(1) + updated
                                onStrokesChanged(DoodleCodec.encode(strokes))
                            }
                        },
                        onDragEnd = {
                            onStrokesChanged(DoodleCodec.encode(strokes))
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                strokes.forEach { stroke ->
                    if (stroke.points.size < 2) {
                        stroke.points.firstOrNull()?.let { p ->
                            drawCircle(Color(stroke.color), radius = stroke.width / 2, center = Offset(p.x, p.y))
                        }
                    } else {
                        val path = Path()
                        path.moveTo(stroke.points[0].x, stroke.points[0].y)
                        stroke.points.drop(1).forEach { path.lineTo(it.x, it.y) }
                        drawPath(
                            path,
                            color = Color(stroke.color),
                            style = Stroke(width = stroke.width, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }

        Text(
            if (activeTool == DoodleTool.ERASER) "Touch or drag across strokes to erase them."
            else "Draw with finger or stylus. Switch between Pen, Marker, Highlighter, and 5 stroke widths.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
