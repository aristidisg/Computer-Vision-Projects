package io.github.aristidisg.ensemble.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.ui.theme.LocalEnsembleColors
import io.github.aristidisg.ensemble.ui.theme.StampStyle
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The swipe deck: right = like, left = no, up = wearing it. A card commits at 30 % of the width
 * or a fling above 1000 dp/s, rotates up to ±12°, ticks a haptic on commit and shows LIKE /
 * NOPE / WEAR stamps. Buttons and TalkBack custom actions do the same things.
 */
@Composable
fun SwipeDeck(
    cards: List<ScoredOutfit>,
    onSwipe: (ScoredOutfit, Swipe) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (ScoredOutfit) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        // Draw the next card under the top one for depth.
        cards.take(2).asReversed().forEach { card ->
            key(card.signature) {
                val isTop = card.signature == cards.first().signature
                if (isTop) SwipeCard(card, widthPx, heightPx, onSwipe) { content(card) }
                else Box(Modifier.fillMaxSize().padding(top = 10.dp).graphicsLayer { scaleX = 0.96f; scaleY = 0.96f; alpha = 0.7f }) { content(card) }
            }
        }
    }
}

@Composable
private fun SwipeCard(card: ScoredOutfit, widthPx: Float, heightPx: Float, onSwipe: (ScoredOutfit, Swipe) -> Unit, content: @Composable () -> Unit) {
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val flingPx = with(density) { 1000.dp.toPx() }
    val colors = LocalEnsembleColors.current
    val likeLabel = stringResource(R.string.action_like)
    val nopeLabel = stringResource(R.string.action_nope)
    val wearLabel = stringResource(R.string.action_wear)

    fun commit(dir: Swipe) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            val target = when (dir) {
                Swipe.LIKE -> Offset(widthPx * 1.5f, offset.value.y)
                Swipe.NOPE -> Offset(-widthPx * 1.5f, offset.value.y)
                Swipe.WEAR -> Offset(offset.value.x, -heightPx * 1.5f)
            }
            offset.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
            onSwipe(card, dir)
        }
    }

    val x = offset.value.x; val y = offset.value.y
    val progressX = (x / (widthPx * 0.3f)).coerceIn(-1f, 1f)
    val progressUp = (-y / (heightPx * 0.25f)).coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { translationX = x; translationY = y; rotationZ = 12f * progressX }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(likeLabel) { commit(Swipe.LIKE); true },
                    CustomAccessibilityAction(nopeLabel) { commit(Swipe.NOPE); true },
                    CustomAccessibilityAction(wearLabel) { commit(Swipe.WEAR); true },
                )
            }
            .pointerInput(card.signature) {
                val tracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        val v = tracker.calculateVelocity()
                        val cur = offset.value
                        when {
                            cur.x > widthPx * 0.3f || v.x > flingPx -> commit(Swipe.LIKE)
                            cur.x < -widthPx * 0.3f || v.x < -flingPx -> commit(Swipe.NOPE)
                            cur.y < -heightPx * 0.25f || (v.y < -flingPx && abs(v.y) > abs(v.x)) -> commit(Swipe.WEAR)
                            else -> scope.launch { offset.animateTo(Offset.Zero, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(Offset.Zero, spring()) } },
                ) { change, drag ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch { offset.snapTo(offset.value + drag) }
                }
            },
    ) {
        content()
        if (progressX > 0.1f) Stamp(stringResource(R.string.stamp_like), colors.like, progressX, Alignment.TopStart, -12f)
        if (progressX < -0.1f) Stamp(stringResource(R.string.stamp_nope), colors.nope, -progressX, Alignment.TopEnd, 12f)
        if (progressUp > 0.1f && abs(progressX) < 0.3f) Stamp(stringResource(R.string.stamp_wear), colors.wear, progressUp, Alignment.BottomCenter, 0f)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Stamp(text: String, color: Color, alpha: Float, align: Alignment, angle: Float) {
    Text(
        text, style = StampStyle, color = color,
        modifier = Modifier.align(align).padding(28.dp).rotate(angle).graphicsLayer { this.alpha = alpha }
            .border(3.dp, color, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
