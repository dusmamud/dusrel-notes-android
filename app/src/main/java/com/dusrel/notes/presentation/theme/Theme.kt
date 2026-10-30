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

// Authentic Reference Design Tokens
val ThemeMainGold = Color(0xFFFFB21D)
val ThemeMainGoldLight = Color(0xFFFFBB00)
val HomepageBgLight = Color(0xFFF7F7F7)
val HomepageBgDark = Color(0xFF000000)
val CardBgLight = Color(0xFFFFFFFF)
val CardBgDark = Color(0xFF242424)
val TodoCheckboxOutline = Color(0xFFB8BAC0)

val NotesBlue = ThemeMainGold
val NotesBlueDark = ThemeMainGold
val NotesInk = Color(0xFF1F1F1F)
val NotesPaper = HomepageBgLight
val NotesCardDark = CardBgDark
val NotesBackgroundDark = HomepageBgDark

data class NoteCardThemeColors(
    val id: Int,
    val name: String,
    val containerColor: Color,
    val onContainerColor: Color,
    val secondaryTextColor: Color,
    val borderColor: Color,
)

/**
 * 5 Authentic Note Card Color Presets extracted directly from decompiled design tokens:
 * 0: Warm Cream / Yellow Paper (#FFFBF0)
 * 1: Sky Blue (#EBF6FC)
 * 2: Pure White (#FFFFFF)
 * 3: Mint Green (#F0FDF0)
 * 4: Sakura Pink (#FFF0F3)
 */
fun getNoteCardTheme(themeId: Int, isDark: Boolean): NoteCardThemeColors {
    val normalizedId = (themeId % 5).let { if (it < 0) it + 5 else it }
    return if (isDark) {
        when (normalizedId) {
            0 -> NoteCardThemeColors(0, "Yellow", Color(0xFF2A2720), Color(0xFFF0E8D9), Color(0xFFA89F8F), Color(0xFF3D382E))
            1 -> NoteCardThemeColors(1, "Blue",   Color(0xFF1A2433), Color(0xFFD8E6FC), Color(0xFF8FA7CC), Color(0xFF26354D))
            2 -> NoteCardThemeColors(2, "White",  Color(0xFF242424), Color(0xFFE2E2E2), Color(0xFFA0A3AB), Color(0xFF363636))
            3 -> NoteCardThemeColors(3, "Green",  Color(0xFF1D2A22), Color(0xFFD4F2DE), Color(0xFF8FA896), Color(0xFF2B3D32))
            4 -> NoteCardThemeColors(4, "Pink",   Color(0xFF301F24), Color(0xFFFCD8E1), Color(0xFFB58E97), Color(0xFF452D34))
            else -> NoteCardThemeColors(0, "Yellow", Color(0xFF2A2720), Color(0xFFF0E8D9), Color(0xFFA89F8F), Color(0xFF3D382E))
        }
    } else {
        when (normalizedId) {
            0 -> NoteCardThemeColors(0, "Yellow", Color(0xFFFFFBF0), Color(0xFF2D261A), Color(0xFF786C57), Color(0xFFF0E5D0))
            1 -> NoteCardThemeColors(1, "Blue",   Color(0xFFEBF6FC), Color(0xFF3275C2), Color(0xFF5B718F), Color(0x335B99D3))
            2 -> NoteCardThemeColors(2, "White",  Color(0xFFFFFFFF), Color(0xFF1F1F1F), Color(0xFF6B6E75), Color(0xFFEAEAEA))
            3 -> NoteCardThemeColors(3, "Green",  Color(0xFFF0FDF0), Color(0xFF4D8A5D), Color(0xFF5A7E65), Color(0x334D8A5D))
            4 -> NoteCardThemeColors(4, "Pink",   Color(0xFFFFF0F3), Color(0xFFBF5866), Color(0xFF8F5A67), Color(0xFFFAD7DF))
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
    primary = ThemeMainGold,
    onPrimary = Color(0xFF2D261A),
    primaryContainer = Color(0xFFFFF0D2),
    onPrimaryContainer = Color(0xFF5C3C00),
    surface = CardBgLight,
    onSurface = Color(0xFF1F1F1F),
    background = HomepageBgLight,
    onBackground = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFEAEAEA),
    onSurfaceVariant = Color(0xFF757575),
)

private val DarkScheme = darkColorScheme(
    primary = ThemeMainGold,
    onPrimary = Color(0xFF2D261A),
    primaryContainer = Color(0xFF4A3400),
    onPrimaryContainer = Color(0xFFFFE0A0),
    surface = CardBgDark,
    onSurface = Color(0xFFE2E2E2),
    background = HomepageBgDark,
    onBackground = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFF9E9E9E),
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
