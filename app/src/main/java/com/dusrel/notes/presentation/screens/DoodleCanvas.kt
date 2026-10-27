package com.dusrel.notes.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dusrel.notes.domain.doodle.DoodleCodec
import com.dusrel.notes.domain.doodle.DoodlePoint
import com.dusrel.notes.domain.doodle.DoodleStroke
import com.dusrel.notes.presentation.components.SymbolIcon
import com.dusrel.notes.presentation.components.Symbols

/**
 * Original pointer-drag drawing canvas. Strokes are kept in memory here and
 * serialised by the caller with [DoodleCodec] into the note's doodleData.
 */
@Composable
fun DoodleCanvas(
    initialData: String,
    onStrokesChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var strokes by remember(initialData) { mutableStateOf(DoodleCodec.decode(initialData)) }
    var currentColor by remember { mutableStateOf(Color(0xFF1A1C21)) }
    val palette = listOf(
        Color(0xFF1A1C21), Color(0xFFD32F2F), Color(0xFF2F6FED),
        Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFF7B1FA2),
    )
    Column(modifier = modifier) {
        Row(modifier = Modifier.padding(bottom = 8.dp)) {
            palette.forEach { c ->
                IconButton(onClick = { currentColor = c }, modifier = Modifier.size(36.dp)) {
                    Box(
                        modifier = Modifier
                            .size(if (c == currentColor) 28.dp else 22.dp)
                            .clip(CircleShape)
                            .background(c)
                    )
                }
            }
            Button(onClick = {
                strokes = emptyList()
                onStrokesChanged("")
            }, modifier = Modifier.padding(start = 8.dp)) {
                SymbolIcon(Symbols.DELETE, size = 18.sp)
                Text("Clear", modifier = Modifier.padding(start = 4.dp))
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(Color.White)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            strokes = strokes + DoodleStroke(
                                currentColor.toArgb(),
                                listOf(DoodlePoint(offset.x, offset.y))
                            )
                        },
                        onDrag = { change, _ ->
                            val last = strokes.lastOrNull() ?: return@detectDragGestures
                            val updated = last.copy(
                                points = last.points + DoodlePoint(change.position.x, change.position.y)
                            )
                            strokes = strokes.dropLast(1) + updated
                            onStrokesChanged(
                                DoodleCodec.encode(
                                    strokes.map { s ->
                                        DoodleStroke(s.color, s.points)
                                    }
                                )
                            )
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
                            drawCircle(Color(stroke.color), radius = 4f, center = Offset(p.x, p.y))
                        }
                    } else {
                        val path = Path()
                        path.moveTo(stroke.points[0].x, stroke.points[0].y)
                        stroke.points.drop(1).forEach { path.lineTo(it.x, it.y) }
                        drawPath(
                            path, Color(stroke.color),
                            style = Stroke(width = 6f, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
        Text(
            "Draw with your finger or stylus. Strokes are saved inside this note.",
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
