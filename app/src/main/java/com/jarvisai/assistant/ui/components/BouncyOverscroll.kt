package com.jarvisai.assistant.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.overscroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/**
 * Factory contract creating [BouncyOverscrollEffect] instances across scrollable lists.
 */
@Stable
abstract class OverscrollFactory {
    abstract fun createOverscrollEffect(): BouncyOverscrollEffect
}

/**
 * Concrete factory producing iOS / OneUI-style rubber-band spring overscroll effects.
 */
@Stable
class BouncyOverscrollFactory(
    private val scope: CoroutineScope,
    private val maxDragDistancePx: Float = 420f
) : OverscrollFactory() {
    override fun createOverscrollEffect(): BouncyOverscrollEffect {
        return BouncyOverscrollEffect(
            scope = scope,
            maxDragDistancePx = maxDragDistancePx
        )
    }
}

val LocalOverscrollFactory = compositionLocalOf<OverscrollFactory?> { null }

/**
 * Rubber-band spring [OverscrollEffect] that replaces Android's default stretch distortion
 * with progressive drag resistance `delta * (1.0 - min(1.0, abs(currentOffset) / maxDragDistance))`
 * and a smooth spring snap-back (`Spring.DampingRatioMediumBouncy`, `Spring.StiffnessLow`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class BouncyOverscrollEffect(
    private val scope: CoroutineScope,
    private val maxDragDistancePx: Float = 420f
) : OverscrollEffect {

    private var overscrollX by mutableFloatStateOf(0f)
    private var overscrollY by mutableFloatStateOf(0f)
    private var springJob: Job? = null

    override val isInProgress: Boolean
        get() = abs(overscrollX) > 0.5f || abs(overscrollY) > 0.5f

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset
    ): Offset {
        if (source == NestedScrollSource.UserInput) {
            springJob?.cancel()
        }

        // 1. If already overscrolled and user drags back toward resting 0, consume from overscroll first
        var remainingDeltaX = delta.x
        var remainingDeltaY = delta.y
        var preConsumedX = 0f
        var preConsumedY = 0f

        if (overscrollX != 0f && sign(delta.x) != sign(overscrollX)) {
            val unwindX = if (abs(delta.x) >= abs(overscrollX)) -overscrollX else delta.x
            overscrollX += unwindX
            preConsumedX = unwindX
            remainingDeltaX -= unwindX
        }

        if (overscrollY != 0f && sign(delta.y) != sign(overscrollY)) {
            val unwindY = if (abs(delta.y) >= abs(overscrollY)) -overscrollY else delta.y
            overscrollY += unwindY
            preConsumedY = unwindY
            remainingDeltaY -= unwindY
        }

        // 2. Dispatch remaining scroll delta to underlying scrollable container
        val scrollConsumed = performScroll(Offset(remainingDeltaX, remainingDeltaY))
        val unconsumedX = remainingDeltaX - scrollConsumed.x
        val unconsumedY = remainingDeltaY - scrollConsumed.y

        // 3. Apply progressive rubber-band resistance on unconsumed edge drag
        if (source == NestedScrollSource.UserInput) {
            if (abs(unconsumedX) > 0.01f) {
                val resistanceX = 1.0f - min(1.0f, abs(overscrollX) / maxDragDistancePx)
                overscrollX = (overscrollX + unconsumedX * resistanceX * 0.55f)
                    .coerceIn(-maxDragDistancePx, maxDragDistancePx)
            }
            if (abs(unconsumedY) > 0.01f) {
                val resistanceY = 1.0f - min(1.0f, abs(overscrollY) / maxDragDistancePx)
                overscrollY = (overscrollY + unconsumedY * resistanceY * 0.55f)
                    .coerceIn(-maxDragDistancePx, maxDragDistancePx)
            }
        }

        return Offset(
            x = preConsumedX + scrollConsumed.x + unconsumedX,
            y = preConsumedY + scrollConsumed.y + unconsumedY
        )
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity
    ) {
        val consumedVelocity = performFling(velocity)
        val leftoverX = velocity.x - consumedVelocity.x
        val leftoverY = velocity.y - consumedVelocity.y

        springJob?.cancel()
        springJob = scope.launch {
            launch {
                val animX = Animatable(overscrollX)
                animX.animateTo(
                    targetValue = 0f,
                    initialVelocity = leftoverX * 0.15f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) {
                    overscrollX = value
                }
                overscrollX = 0f
            }
            launch {
                val animY = Animatable(overscrollY)
                animY.animateTo(
                    targetValue = 0f,
                    initialVelocity = leftoverY * 0.15f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) {
                    overscrollY = value
                }
                overscrollY = 0f
            }
        }
    }

    override val effectModifier: Modifier = Modifier.graphicsLayer {
        translationX = overscrollX
        translationY = overscrollY
    }
}

/**
 * Remembers a [BouncyOverscrollEffect] tied to the current composition scope or [LocalOverscrollFactory].
 */
@Composable
fun rememberBouncyOverscrollEffect(maxDragDistancePx: Float = 420f): BouncyOverscrollEffect {
    val factory = LocalOverscrollFactory.current
    val scope = rememberCoroutineScope()
    return remember(factory, scope, maxDragDistancePx) {
        factory?.createOverscrollEffect() ?: BouncyOverscrollEffect(scope, maxDragDistancePx)
    }
}

/**
 * Applies [BouncyOverscrollEffect] to a scrollable container.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.bouncyOverscroll(
    effect: BouncyOverscrollEffect = rememberBouncyOverscrollEffect()
): Modifier = this.overscroll(effect)

/**
 * Wraps the Compose hierarchy to disable Android's harsh stretch overscroll and provide
 * [LocalOverscrollFactory] with [BouncyOverscrollFactory].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProvideBouncyOverscroll(
    maxDragDistancePx: Float = 420f,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val factory = remember(scope, maxDragDistancePx) {
        BouncyOverscrollFactory(scope = scope, maxDragDistancePx = maxDragDistancePx)
    }
    CompositionLocalProvider(
        LocalOverscrollConfiguration provides null,
        LocalOverscrollFactory provides factory,
        content = content
    )
}
