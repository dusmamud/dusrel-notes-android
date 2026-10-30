package com.dusrel.notes.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dusrel.notes.R
import com.dusrel.notes.data.local.ThemeSetting

// Original Notes tokens
val NotesBlue = Color(0xFF2F6FED)
val NotesBlueDark = Color(0xFF7FB0FF)
val NotesInk = Color(0xFF1A1C21)
val NotesPaper = Color(0xFFF7F8FA)
val NotesCardDark = Color(0xFF23262E)
val NotesBackgroundDark = Color(0xFF14161B)

data class NoteCardThemeColors(
    val id: Int,
    val name: String,
    val containerColor: Color,
    val onContainerColor: Color,
    val secondaryTextColor: Color,
    val borderColor: Color,
)

/**
 * 5 Accessible Note Card Color Presets (Warm Yellow, Sky Blue, Pure White, Mint Green, Sakura Pink)
 * With accessible contrast-verified light and dark mode pairings.
 */
fun getNoteCardTheme(themeId: Int, isDark: Boolean): NoteCardThemeColors {
    val normalizedId = (themeId % 5).let { if (it < 0) it + 5 else it }
    return if (isDark) {
        when (normalizedId) {
            0 -> NoteCardThemeColors(0, "Yellow", Color(0xFF2A2720), Color(0xFFF0E8D9), Color(0xFFA89F8F), Color(0xFF3D382E))
            1 -> NoteCardThemeColors(1, "Blue",   Color(0xFF1A2433), Color(0xFFD8E6FC), Color(0xFF8FA7CC), Color(0xFF26354D))
            2 -> NoteCardThemeColors(2, "White",  Color(0xFF23262E), Color(0xFFE2E2E2), Color(0xFFA0A3AB), Color(0xFF323642))
            3 -> NoteCardThemeColors(3, "Green",  Color(0xFF1D2A22), Color(0xFFD4F2DE), Color(0xFF8FA896), Color(0xFF2B3D32))
            4 -> NoteCardThemeColors(4, "Pink",   Color(0xFF301F24), Color(0xFFFCD8E1), Color(0xFFB58E97), Color(0xFF452D34))
            else -> NoteCardThemeColors(0, "Yellow", Color(0xFF2A2720), Color(0xFFF0E8D9), Color(0xFFA89F8F), Color(0xFF3D382E))
        }
    } else {
        when (normalizedId) {
            0 -> NoteCardThemeColors(0, "Yellow", Color(0xFFFFFBF0), Color(0xFF2D261A), Color(0xFF786C57), Color(0xFFF0E5D0))
            1 -> NoteCardThemeColors(1, "Blue",   Color(0xFFF0F6FF), Color(0xFF152945), Color(0xFF5B718F), Color(0xFFD8E5F7))
            2 -> NoteCardThemeColors(2, "White",  Color(0xFFFFFFFF), Color(0xFF1F1F1F), Color(0xFF6B6E75), Color(0xFFE5E7EB))
            3 -> NoteCardThemeColors(3, "Green",  Color(0xFFF0FAF3), Color(0xFF183823), Color(0xFF5A7E65), Color(0xFFD4EEDB))
            4 -> NoteCardThemeColors(4, "Pink",   Color(0xFFFFF0F3), Color(0xFF451622), Color(0xFF8F5A67), Color(0xFFFAD7DF))
            else -> NoteCardThemeColors(0, "Yellow", Color(0xFFFFFBF0), Color(0xFF2D261A), Color(0xFF786C57), Color(0xFFF0E5D0))
        }
    }
}

val PoppinsFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private val NotesTypography = Typography(
    displayLarge = TextStyle(fontFamily = PoppinsFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = PoppinsFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = PoppinsFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = PoppinsFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = PoppinsFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

private val LightScheme = lightColorScheme(
    primary = NotesBlue,
    onPrimary = Color.White,
    surface = Color.White,
    onSurface = NotesInk,
    background = NotesPaper,
    onBackground = NotesInk,
    surfaceVariant = Color(0xFFEDEFF3),
)

private val DarkScheme = darkColorScheme(
    primary = NotesBlueDark,
    onPrimary = Color.Black,
    surface = NotesCardDark,
    onSurface = Color.White,
    background = NotesBackgroundDark,
    onBackground = Color.White,
    surfaceVariant = Color(0xFF2E323C),
)

@Composable
fun NotesTheme(theme: ThemeSetting, content: @Composable () -> Unit) {
    val dark = when (theme) {
        ThemeSetting.LIGHT -> false
        ThemeSetting.DARK -> true
        ThemeSetting.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = NotesTypography,
        content = content,
    )
}
