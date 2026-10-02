package com.khaled.handover.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF102C48)
val Teal = Color(0xFF008E9A)
val TealLight = Color(0xFFDDF4F5)
val Amber = Color(0xFFD18C32)
private val Light = lightColorScheme(primary = Teal, onPrimary = Color.White, secondary = Navy,
    background = Color(0xFFF7FAFC), surface = Color.White, onSurface = Navy,
    surfaceVariant = Color(0xFFF0F4F7), outline = Color(0xFFBCCAD2), error = Color(0xFFB42318))
private val Dark = darkColorScheme(primary = Color(0xFF59D4D5), onPrimary = Color(0xFF00383D), secondary = Color(0xFFC2D8E8),
    background = Color(0xFF0C1926), surface = Color(0xFF152B3A), onSurface = Color(0xFFE8F3F6),
    surfaceVariant = Color(0xFF243C4C), outline = Color(0xFF8099A5), error = Color(0xFFFFB4AB))
@Composable fun HandoverTheme(darkMode: Boolean? = null, content: @Composable ()->Unit) {
    MaterialTheme(colorScheme = if (darkMode ?: isSystemInDarkTheme()) Dark else Light,
        shapes = Shapes(small = androidx.compose.foundation.shape.RoundedCornerShape(12), medium = androidx.compose.foundation.shape.RoundedCornerShape(18), large = androidx.compose.foundation.shape.RoundedCornerShape(26)),
        content = content)
}
