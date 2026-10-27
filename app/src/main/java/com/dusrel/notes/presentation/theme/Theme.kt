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

// Original Notes tokens (not copied from any other app).
val NotesBlue = Color(0xFF2F6FED)
val NotesBlueDark = Color(0xFF7FB0FF)
val NotesInk = Color(0xFF1A1C21)
val NotesPaper = Color(0xFFF7F8FA)
val NotesCardDark = Color(0xFF23262E)
val NotesBackgroundDark = Color(0xFF14161B)

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
