package com.lunacattus.nav3

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

private val NavEasing = CubicBezierEasing(0.42f, 0.00f, 0.58f, 1.00f)
private const val DURATION = 400

val slideInFromRight = slideIn(
    animationSpec = tween(DURATION, easing = NavEasing),
    initialOffset = { IntOffset(it.width, 0) },
)
val slideOutFromRight = slideOut(
    animationSpec = tween(DURATION, easing = NavEasing),
    targetOffset = { IntOffset(it.width, 0) },
)
val slideInFromLeft = slideIn(
    animationSpec = tween(DURATION, easing = NavEasing),
    initialOffset = { IntOffset(-it.width, 0) },
)
val slideOutFromLeft = slideOut(
    animationSpec = tween(DURATION, easing = NavEasing),
    targetOffset = { IntOffset(-it.width, 0) },
)
val slideInFromBottom = slideIn(
    animationSpec = tween(DURATION, easing = NavEasing),
    initialOffset = { IntOffset(0, it.height) },
)
val slideOutFromBottom = slideOut(
    animationSpec = tween(DURATION, easing = NavEasing),
    targetOffset = { IntOffset(0, it.height) },
)
val slideInFromTop = slideIn(
    animationSpec = tween(DURATION, easing = NavEasing),
    initialOffset = { IntOffset(0, -it.height) },
)
val slideOutFromTop = slideOut(
    animationSpec = tween(DURATION, easing = NavEasing),
    targetOffset = { IntOffset(0, -it.height) },
)

/** Horizontal push: incoming from right, outgoing to left. */
val forwardHorizontal: ContentTransform
    get() = slideInFromRight togetherWith slideOutFromLeft

/** Horizontal pop: incoming from left, outgoing to right. */
val backwardHorizontal: ContentTransform
    get() = slideInFromLeft togetherWith slideOutFromRight

/** Vertical push: incoming from bottom, outgoing to top. */
val forwardVertical: ContentTransform
    get() = slideInFromBottom togetherWith slideOutFromTop

/** Vertical pop: incoming from top, outgoing to bottom. */
val backwardVertical: ContentTransform
    get() = slideInFromTop togetherWith slideOutFromBottom

/** Fade through, useful for overlay / root-stack pages. */
val fadeThrough: ContentTransform
    get() = fadeIn(tween(DURATION)) togetherWith fadeOut(tween(DURATION))

private val stayStillIn = slideIn(
    animationSpec = tween(700, easing = LinearEasing),
    initialOffset = { IntOffset.Zero },
)
private val stayStillOut = slideOut(
    animationSpec = tween(700, easing = LinearEasing),
    targetOffset = { IntOffset.Zero },
)

/** Scale + fade enter, page stays still (used for root overlay). */
val scaleEnter: ContentTransform
    get() = scaleIn(initialScale = 0.8f, animationSpec = tween(700)) +
        fadeIn(tween(700)) togetherWith stayStillOut

/** Scale + fade exit, page stays still (used for root overlay pop). */
val scaleExit: ContentTransform
    get() = stayStillIn togetherWith
        scaleOut(targetScale = 0.8f, animationSpec = tween(700)) +
        fadeOut(tween(700))
