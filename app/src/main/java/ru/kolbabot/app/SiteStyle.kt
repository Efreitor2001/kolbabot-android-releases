package ru.kolbabot.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

internal val LocalSiteDark = staticCompositionLocalOf { false }
internal val LocalToggleTheme = staticCompositionLocalOf<() -> Unit> { {} }

internal object SiteColors {
    val Brand @Composable get() = theme(0xFF365EEA, 0xFF92ADFF)
    val Action = Color(0xFF365EEA)
    val DangerAction = Color(0xFFCE3345)
    val Accent @Composable get() = theme(0xFF0A7465, 0xFF67D9BF)
    val StoreGreen @Composable get() = theme(0xFF146B53, 0xFF67D9BF)
    val AddToCart @Composable get() = theme(0xFF087F7A, 0xFF65D6CF)
    val CartInk @Composable get() = theme(0xFFFFFFFF, 0xFF103B3C)
    val CartSurface @Composable get() = theme(0xFFEAF8F4, 0xFF152D2E)
    val CartItem @Composable get() = theme(0xFFF6FDFB, 0xFF1D3B3B)
    val CartMuted @Composable get() = theme(0xFF486E66, 0xFFADCCC7)
    val CartIcon @Composable get() = theme(0xFF086E68, 0xFF83E1D7)
    val CartIconBackground @Composable get() = theme(0xFFD6EFEA, 0xFF214746)
    val Rating @Composable get() = theme(0xFF8D6400, 0xFFF0BE4E)
    val CartBorder @Composable get() = theme(0xFFB9DED5, 0xFF386462)
    val Ink @Composable get() = theme(0xFF1C2941, 0xFFE7EDF7)
    val Muted @Composable get() = theme(0xFF5D6A80, 0xFFA3B1C6)
    val BackgroundTop @Composable get() = theme(0xFFF4F6FA, 0xFF101722)
    val Surface @Composable get() = theme(0xFFFFFFFF, 0xFF192231)
    val Soft @Composable get() = theme(0xFFF6F8FC, 0xFF202C3E)
    val Hover @Composable get() = theme(0xFFEDF2FA, 0xFF28364B)
    val AccentSoft @Composable get() = theme(0xFFEDF2FF, 0xFF263552)
    val SuccessSoft @Composable get() = theme(0xFFE9F6F1, 0xFF173B36)
    val Border @Composable get() = theme(0xFFE1E7F0, 0xFF344155)
    val CustomerBorder @Composable get() = Border
    val InputBorder @Composable get() = Border
    val InputBackground @Composable get() = Soft
    val Error @Composable get() = theme(0xFFCE3345, 0xFFF57987)
    val ErrorBackground @Composable get() = theme(0xFFFFF0F1, 0xFF452732)

    @Composable private fun theme(light: Long, dark: Long) = Color(if (LocalSiteDark.current) dark else light)
}

/** Admin CSS uses white inputs; customer forms use the softer #f8fafc surface. */
internal val LocalSiteAdmin = staticCompositionLocalOf { false }
internal val LocalSiteAuth = staticCompositionLocalOf { false }
internal val LocalSiteCart = staticCompositionLocalOf { false }

// The same five Manrope weights loaded by index.html/admin.html, bundled for offline use.
internal val SiteFontFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)
