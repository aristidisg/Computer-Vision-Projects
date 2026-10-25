package io.github.aristidisg.ensemble.ui.wardrobe

import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.github.aristidisg.ensemble.R

/** sRGB hex of an HSV color (hue 0..360, saturation and value 0..1). */
fun hsvHex(h: Float, s: Float, v: Float): String = "#%06X".format(0xFFFFFF and Color.hsv(h.coerceIn(0f, 359.99f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)).toArgb())

/**
 * "Match by eye": the whole screen becomes the color, at full brightness, so the phone can be
 * held next to the piece. Hue column on the right, lightness on the left, saturation along the
 * bottom (all respond under the finger, no lag); dragging on the color itself fine-tunes it
 * (sideways = hue, up and down = lightness) in small steps for the last bit of matching.
 */
@Composable
fun ColorMatchDialog(initialHex: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val start = remember(initialHex) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(runCatching { android.graphics.Color.parseColor(initialHex) }.getOrDefault(0xFF808080.toInt()), it) }
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val color = Color.hsv(hue.coerceIn(0f, 359.99f), sat, value)
    val ink = if (color.luminance() > 0.45f) Color.Black else Color.White

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Full brightness and screen on while matching; both end with the dialog's window.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.let { w ->
                w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
                w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose { }
        }
        Box(Modifier.fillMaxSize().background(color)) {
            // Fine-tune on the color field: tiny steps per pixel of drag.
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var last = down.position
                    drag(down.id) { change ->
                        val d = change.position - last
                        last = change.position
                        hue = ((hue + d.x * FINE_HUE_PER_PX) % 360f + 360f) % 360f
                        value = (value - d.y * FINE_VALUE_PER_PX).coerceIn(0f, 1f)
                        change.consume()
                    }
                }
            })
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 76.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(hsvHex(hue, sat, value), color = ink, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = ink) }
                    Button(onClick = { onDone(hsvHex(hue, sat, value)) }, colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = color)) {
                        Text(stringResource(R.string.done))
                    }
                }
                Text(stringResource(R.string.match_hint), color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 76.dp))
            }
            // Lightness column (left).
            Strip(
                Modifier.align(Alignment.CenterStart).fillMaxHeight().width(STRIP).padding(vertical = 24.dp),
                Brush.verticalGradient(listOf(Color.hsv(hue.coerceIn(0f, 359.99f), sat, 1f), Color.Black)),
                1f - value, vertical = true,
            ) { value = 1f - it }
            // Hue column (right): the color wheel unrolled, red to red.
            Strip(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(STRIP).padding(vertical = 24.dp),
                hueBrush, hue / 360f, vertical = true,
            ) { hue = it * 359.99f }
            // Saturation strip (bottom).
            Strip(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(STRIP).padding(horizontal = 76.dp).padding(bottom = 24.dp),
                Brush.horizontalGradient(listOf(Color.hsv(hue.coerceIn(0f, 359.99f), 0f, value), Color.hsv(hue.coerceIn(0f, 359.99f), 1f, value))),
                sat, vertical = false,
            ) { sat = it }
        }
    }
}

private val STRIP = 56.dp
private const val FINE_HUE_PER_PX = 0.04f
private const val FINE_VALUE_PER_PX = 0.0006f

/** 37 stops, every 10°, so the hue gradient is perfectly smooth. */
private val hueBrush = Brush.verticalGradient((0..36).map { Color.hsv((it * 10f).coerceAtMost(359.99f), 1f, 1f) })

/** A gradient strip that follows the finger from the first touch; [fraction] is the marker position. */
@Composable
private fun Strip(modifier: Modifier, brush: Brush, fraction: Float, vertical: Boolean, onChange: (Float) -> Unit) {
    Canvas(modifier.pointerInput(vertical) {
        fun at(p: Offset) = (if (vertical) p.y / size.height else p.x / size.width).coerceIn(0f, 1f)
        awaitEachGesture {
            val down = awaitFirstDown()
            onChange(at(down.position))
            down.consume()
            drag(down.id) { change -> onChange(at(change.position)); change.consume() }
        }
    }) {
        val r = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
        drawRoundRect(brush, cornerRadius = r)
        drawRoundRect(Color.White.copy(alpha = 0.6f), cornerRadius = r, style = Stroke(1.5.dp.toPx()))
        val pos = fraction.coerceIn(0f, 1f)
        val line = 4.dp.toPx()
        if (vertical) {
            val y = pos * size.height
            drawRect(Color.White, topLeft = Offset(0f, y - line), size = androidx.compose.ui.geometry.Size(size.width, line * 2))
            drawRect(Color.Black, topLeft = Offset(0f, y - line / 3), size = androidx.compose.ui.geometry.Size(size.width, line / 1.5f))
        } else {
            val x = pos * size.width
            drawRect(Color.White, topLeft = Offset(x - line, 0f), size = androidx.compose.ui.geometry.Size(line * 2, size.height))
            drawRect(Color.Black, topLeft = Offset(x - line / 3, 0f), size = androidx.compose.ui.geometry.Size(line / 1.5f, size.height))
        }
    }
}
