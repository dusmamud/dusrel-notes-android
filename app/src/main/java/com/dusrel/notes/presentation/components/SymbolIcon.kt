package com.dusrel.notes.presentation.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.dusrel.notes.R

private val SymbolsRegular = FontFamily(Font(R.font.material_symbols_rounded))
private val SymbolsFilled = FontFamily(Font(R.font.material_symbols_rounded_filled))

/**
 * Material Symbols Rounded icon rendered from the bundled subset fonts.
 * [codePoint] is a hex codepoint string such as "e5c4". [filled] selects
 * the FILL=1 font; regular (FILL=0) is the default.
 */
@Composable
fun SymbolIcon(
    codePoint: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: TextUnit = 24.sp,
    color: Color = Color.Unspecified,
    contentDescription: String? = null,
) {
    val char = codePoint.toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: "?"
    BasicText(
        text = char,
        modifier = modifier,
        style = TextStyle(
            fontFamily = if (filled) SymbolsFilled else SymbolsRegular,
            fontSize = size,
            color = color,
        ),
    )
}

// Verified codepoints (present in both bundled subset fonts).
object Symbols {
    const val ADD = "e145"
    const val ALARM = "e855"
    const val ALARM_ADD = "e856"
    const val ARCHIVE = "e149"
    const val ARROW_BACK = "e5c4"
    const val ATTACH_FILE = "e226"
    const val BOOKMARK = "e8e7"
    const val BRUSH = "e3ae"
    const val CALENDAR_TODAY = "e935"
    const val CHECK = "e668"
    const val CHECK_CIRCLE = "f0be"
    const val CHECKLIST = "e6b1"
    const val CHEVRON_RIGHT = "e5cc"
    const val CLOSE = "e5cd"
    const val CONTENT_COPY = "e14d"
    const val CREATE_NEW_FOLDER = "e2cc"
    const val DARK_MODE = "e51c"
    const val DELETE = "e92e"
    const val DESCRIPTION = "e873"
    const val DOWNLOAD = "f090"
    const val DRAW = "e746"
    const val EDIT = "f097"
    const val EVENT = "e878"
    const val EXPAND_LESS = "e5ce"
    const val EXPAND_MORE = "e5cf"
    const val FOLDER = "e2c7"
    const val FOLDER_OPEN = "e2c8"
    const val FORMAT_BOLD = "e238"
    const val FORMAT_ITALIC = "e23f"
    const val FORMAT_LIST_BULLETED = "e241"
    const val FORMAT_LIST_NUMBERED = "e242"
    const val FORMAT_UNDERLINED = "e249"
    const val GRID_VIEW = "e9b0"
    const val HISTORY = "e8b3"
    const val HOME = "e9b2"
    const val IMAGE = "e3f4"
    const val INFO = "e88e"
    const val LABEL = "e893"
    const val LIGHT_MODE = "e518"
    const val LOCK = "e899"
    const val LOCK_OPEN = "e898"
    const val MENU = "e5d2"
    const val MIC = "e31d"
    const val MORE_VERT = "e5d4"
    const val NOTE_ALT = "f040"
    const val NOTIFICATIONS = "e7f5"
    const val PALETTE = "e40a"
    const val PUSH_PIN = "f10d"
    const val RESTORE_FROM_TRASH = "e938"
    const val SCHEDULE = "efd6"
    const val SEARCH = "ef7a"
    const val SETTINGS = "e8b8"
    const val SHARE = "e80d"
    const val SORT = "e164"
    const val STAR = "f09a"
    const val TASK_ALT = "e2e6"
    const val TITLE = "e264"
    const val UNARCHIVE = "e169"
    const val UNDO = "e166"
    const val UPLOAD = "f09b"
    const val VIEW_LIST = "e8ef"
    const val VISIBILITY = "e8f4"
    const val VISIBILITY_OFF = "e8f5"
    const val WIDGETS = "e1bd"
    const val ARTICLE = "ef42"
    const val LIST_ALT = "e0ee"
    const val DONE_ALL = "e877"
}
