package com.vansh.quicknotes.ui.design

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spring specs per spec §9: damping ratio ~0.8, medium stiffness.
 * Quick interactions ~200 ms feel, larger transitions ~350-450 ms feel.
 * Reduce-motion mode swaps every spring for a plain fade.
 */
@Immutable
data class QNMotion(
    val damping: Float = 0.8f,
    val reduced: Boolean = false,
    val fadeMillis: Int = 180,
) {
    fun <T> quick(): FiniteAnimationSpec<T> =
        if (reduced) tween(fadeMillis) else spring(damping, Spring.StiffnessHigh)

    fun <T> medium(): FiniteAnimationSpec<T> =
        if (reduced) tween(fadeMillis) else spring(damping, Spring.StiffnessMedium)

    fun <T> slow(): FiniteAnimationSpec<T> =
        if (reduced) tween(fadeMillis + 80) else spring(damping, Spring.StiffnessLow)

    fun dpSpring(): FiniteAnimationSpec<Dp> =
        if (reduced) tween(fadeMillis) else spring(damping, Spring.StiffnessMediumLow, 0.01.dp)

    fun floatSpring(): FiniteAnimationSpec<Float> =
        if (reduced) tween(fadeMillis) else spring(damping, Spring.StiffnessMediumLow)
}

val LocalQNMotion = staticCompositionLocalOf { QNMotion() }
