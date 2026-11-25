package ru.kolbabot.app

import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.PI
import kotlin.random.Random

internal val LocalSiteHero = staticCompositionLocalOf<Bitmap?> { null }
internal val LocalNewYear = staticCompositionLocalOf { false }
internal val LocalRefreshAppearance = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
internal fun SiteThemeButton(showLabel: Boolean = false) {
    val dark = LocalSiteDark.current
    val toggle = LocalToggleTheme.current
    val label = if (dark) "Светлая тема" else "Тёмная тема"
    val ink = SiteColors.Ink
    if (showLabel) {
        androidx.compose.material3.OutlinedButton(toggle, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)) {
            ThemeGlyph(dark, ink)
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp)
        }
    } else {
        IconButton(toggle, Modifier.size(38.dp, 40.dp).semantics { contentDescription = "Включить " + label.lowercase() }) {
            ThemeGlyph(dark, ink)
        }
    }
}

@Composable
private fun ThemeGlyph(dark: Boolean, ink: Color) {
    Canvas(Modifier.size(18.dp)) {
        val u = size.width / 18f
        val stroke = 1.6f * u
        if (dark) {
            drawCircle(ink, 3.5f * u, style = Stroke(stroke))
            repeat(8) { ray ->
                rotate(ray * 45f) {
                    drawLine(ink, Offset(center.x, 0.8f * u), Offset(center.x, 3f * u), stroke)
                }
            }
        } else {
            val moon = Path().apply {
                moveTo(10f * u, 2f * u)
                cubicTo(4f * u, 1.5f * u, .8f * u, 7f * u, 3f * u, 12f * u)
                cubicTo(5.2f * u, 17f * u, 12f * u, 17f * u, 15.6f * u, 11.3f * u)
                cubicTo(10f * u, 13f * u, 6.5f * u, 7.6f * u, 10f * u, 2f * u)
                close()
            }
            drawPath(moon, ink, style = Stroke(stroke))
        }
    }
}

@Composable
internal fun SiteMascot(size: Dp, modifier: Modifier = Modifier, description: String? = "Колбабот") {
    Box(modifier.size(size)) {
        val logo = LocalSiteHero.current
        val frame = Modifier.matchParentSize().clip(RoundedCornerShape(size * .29f))
        if (logo == null) Image(painterResource(R.drawable.site_robot), description, frame, contentScale = ContentScale.Crop)
        else Image(logo.asImageBitmap(), description, frame, contentScale = ContentScale.Crop)
        if (LocalNewYear.current) {
            Image(painterResource(R.drawable.new_year_hat), null,
                Modifier.offset(x = size * .36f, y = size * (.23f - .28f * 118f / 192f))
                    .size(size * .28f, size * (.28f * 118f / 192f)))
        }
    }
}

@Composable
internal fun FestiveHeader(content: @Composable () -> Unit) {
    val enabled = LocalNewYear.current
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(top = if (enabled) 17.dp else 0.dp)) { content() }
        if (enabled) FestiveGarland()
    }
}

@Composable
private fun rememberDecorationClock(): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val animate = ValueAnimator.areAnimatorsEnabled()
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
    }
    return time
}

@Composable
internal fun FestiveSnow(modifier: Modifier) {
    if (!ValueAnimator.areAnimatorsEnabled()) return
    val dark = LocalSiteDark.current
    val time = rememberDecorationClock()
    val count = if (LocalConfiguration.current.screenWidthDp <= 640) 9 else 16
    val flakes = remember(count) {
        val random = Random(92)
        List(count) { i -> (((i * 7) % 17 + random.nextFloat()) / 17f) to (i + .35f + random.nextFloat() * .3f) }
    }
    Canvas(modifier) {
        if (size.width < 58.dp.toPx()) return@Canvas
        val elapsed = time.value
        flakes.forEachIndexed { i, (x, slot) ->
            val phase = (elapsed / 22f + slot / count) % 1f
            val left = (x * size.width).coerceIn(24.dp.toPx(), size.width - 34.dp.toPx())
            val point = Offset(left + phase * (if (i % 2 == 1) 28.dp else (-22).dp).toPx(),
                -8.dp.toPx() + phase * (size.height + 16.dp.toPx()))
            val radius = (2 + i % 3).dp.toPx() / 2
            val alpha = .34f + i % 5 * .06f + if (dark) 0f else .18f
            if (dark) drawCircle(Color(0xFFDFEFFF).copy(alpha = alpha * .36f), radius + 1.5.dp.toPx(), point)
            else drawCircle(Color(0xFF1E2A3E).copy(alpha = alpha * .82f), radius + 1.dp.toPx(), point)
            drawCircle(Color.White.copy(alpha = alpha), radius, point)
        }
    }
}

@Composable
private fun FestiveGarland() {
    val dark = LocalSiteDark.current
    val time = rememberDecorationClock()
    val count = if (LocalConfiguration.current.screenWidthDp <= 640) 4 else 8
    val odd = listOf(0xFFF34461, 0xFFF4AC26, 0xFF2CBD77, 0xFF3799ED).map { Color(it) }
    val even = listOf(0xFF41A9EF, 0xFFE669B7, 0xFFF24D58, 0xFFF6BC37).map { Color(it) }
    val wire = Color(if (dark) 0xFF678575 else 0xFF52685B)
    val socket = Color(if (dark) 0xFF537265 else 0xFF384D41)
    Canvas(Modifier.fillMaxWidth().height(17.dp)) {
        val width = size.width / count
        val elapsed = time.value
        repeat(count) { i ->
            val origin = i * width
            val line = Path().apply {
                moveTo(origin, 1.dp.toPx())
                cubicTo(origin + width * .16f, 11.dp.toPx(), origin + width * .84f, 11.dp.toPx(), origin + width, 1.dp.toPx())
            }
            drawPath(line, wire, style = Stroke(1.dp.toPx()))
            val colors = if (i % 2 == 0) odd else even
            listOf(.17f, .38f, .61f, .82f).forEachIndexed { j, fraction ->
                val position = Offset(origin + width * fraction, (if (j in 1..2) 9.dp else 8.dp).toPx())
                val period = if (j == 2) 6.2f else if (j % 2 == 1) 4.7f else 5.6f
                val phase = ((elapsed + i * .53f) / period) % 1f
                val light = .47f + .53f * ((1 - cos(phase * 2 * PI)) / 2).toFloat()
                val color = colors[j]
                rotate(listOf(18f, 9f, -9f, -18f)[j], position) {
                    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = light * .6f), Color.Transparent),
                        center = position + Offset(0f, 4.dp.toPx()), radius = 7.dp.toPx()),
                        7.dp.toPx(), center = position + Offset(0f, 4.dp.toPx()))
                    drawRoundRect(color.copy(alpha = light), position - Offset(3.dp.toPx(), 0f),
                        Size(6.dp.toPx(), 8.dp.toPx()), CornerRadius(3.dp.toPx()))
                    drawCircle(Color(0xFFFFF4D4).copy(alpha = light), 1.dp.toPx(), position + Offset(-.5.dp.toPx(), 2.dp.toPx()))
                    drawRoundRect(socket, position - Offset(3.dp.toPx(), 2.dp.toPx()),
                        Size(6.dp.toPx(), 3.dp.toPx()), CornerRadius(1.dp.toPx()))
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
internal fun Activity.setFestiveTaskIcon(enabled: Boolean, hero: Bitmap?) {
    val label = applicationInfo.loadLabel(packageManager).toString()
    if (!enabled) {
        val description = if (Build.VERSION.SDK_INT >= 28) ActivityManager.TaskDescription(label, R.drawable.ic_launcher)
            else ActivityManager.TaskDescription(label, null as Bitmap?)
        setTaskDescription(description)
        return
    }
    val original = hero ?: BitmapFactory.decodeResource(resources, R.drawable.site_robot)
    val hat = BitmapFactory.decodeResource(resources, R.drawable.new_year_hat)
    val size = 64f
    val cropRatio = .74f
    val square = minOf(original.width, original.height)
    val crop = (square * cropRatio).toInt()
    val left = (original.width - crop) / 2
    val top = (original.height - square) / 2
    val icon = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(icon).apply {
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        drawBitmap(original, Rect(left, top, left + crop, top + crop), RectF(0f, 0f, size, size), paint)
        val hatWidth = size * .28f / cropRatio
        val hatHeight = hatWidth * hat.height / hat.width
        val hatLeft = size * (.36f - (1f - cropRatio) / 2f) / cropRatio
        val hatBottom = size * .23f / cropRatio
        drawBitmap(hat, null, RectF(hatLeft, hatBottom - hatHeight, hatLeft + hatWidth, hatBottom), paint)
    }
    setTaskDescription(ActivityManager.TaskDescription(label, icon))
}
