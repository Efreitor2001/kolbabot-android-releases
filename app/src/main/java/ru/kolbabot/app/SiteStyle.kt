package ru.kolbabot.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Values copied from the existing website's static/styles.css. */
internal object SiteColors {
    val Brand = Color(0xFF3C61D3)
    val Brand2 = Color(0xFF7245D8)
    val Accent = Color(0xFF1B937C)
    val StoreGreen = Color(0xFF147A66)
    val AddToCart = Color(0xFF04C160)
    val Ink = Color(0xFF2C2D30)
    val Muted = Color(0xFF6B7280)
    val BackgroundTop = Color(0xFFFBFBF9)
    val BackgroundBottom = Color(0xFFF4F3EE)
    val Border = Color.Black.copy(alpha = 0.08f)
    val CustomerBorder = Color(0xFF202B48).copy(alpha = 0.09f)
    val InputBorder = Color(0xFFD1D5DB)
    val InputBackground = Color(0xFFF8FAFC)
    val Error = Color(0xFFB42318)
    val ErrorBackground = Color(0xFFFEF2F2)
}

/** Admin CSS uses white inputs; customer forms use the softer #f8fafc surface. */
internal val LocalSiteAdmin = staticCompositionLocalOf { false }
internal val LocalSiteAuth = staticCompositionLocalOf { false }

// The same five Manrope weights loaded by index.html/admin.html, bundled for offline use.
internal val SiteFontFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)
