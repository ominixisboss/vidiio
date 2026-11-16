package com.ominix.vidiio.ui.screens

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ominix.vidiio.data.repository.HomeStyle

enum class HeroKind {
    /** Auto-rotating pager over several titles. */
    CAROUSEL,
    /** Single full-bleed backdrop with a strong bottom fade. */
    BILLBOARD,
    /** Shorter full-width backdrop, text in the lower third. */
    BANNER,
    /** Inset rounded card. */
    SPOTLIGHT,
    /** Short image strip with a single Play button. */
    COMPACT
}

enum class CardKind { PORTRAIT, LANDSCAPE }

data class HomeStyleSpec(
    val style: HomeStyle,
    val label: String,
    val accent: Color,
    /** null → use the app theme background. */
    val background: Color?,
    val heroKind: HeroKind,
    val heroHeight: Dp,
    val heroCenterText: Boolean,
    val card: CardKind,
    val cardWidth: Dp,
    val cardCorner: Dp,
    val cardShowTitle: Boolean,
    val ratingBadge: Boolean,
    val rowGap: Dp,
    val rowHeaderSize: TextUnit,
    val rowHeaderWeight: FontWeight,
    val uppercaseHeaders: Boolean,
    val showTop10: Boolean,
    // --- motion ---
    /** Slow zoom/pan on the hero image (Netflix / Disney+ feel). */
    val kenBurns: Boolean = false,
    /** Scale a card shrinks to while pressed. 1f = no reaction. */
    val cardPressScale: Float = 0.95f,
    /** Overshooting spring on card press + row entrance (Disney+ feel). */
    val bouncyCards: Boolean = false,
    /** Fade/slide row cards in as they first appear. */
    val staggerIn: Boolean = false
) {
    val cardHeight: Dp
        get() = if (card == CardKind.PORTRAIT) cardWidth * 3f / 2f else cardWidth * 9f / 16f
}

/** Navigation transition for switching between the top-level tabs. */
fun HomeStyle.topLevelTransition(): ContentTransform = when (this) {
    HomeStyle.FLIX ->
        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
    HomeStyle.VIDIIO ->
        fadeIn(tween(400)) + scaleIn(initialScale = 0.92f, animationSpec = tween(400)) togetherWith
            fadeOut(tween(400)) + scaleOut(targetScale = 0.92f, animationSpec = tween(400))
}

/** Navigation transition for opening a Details screen. */
fun HomeStyle.detailTransition(): ContentTransform = when (this) {
    HomeStyle.FLIX, HomeStyle.VIDIIO ->
        slideInHorizontally(tween(350)) { it } + fadeIn(tween(350)) togetherWith
            slideOutHorizontally(tween(350)) { -it } + fadeOut(tween(350))
}

/**
 * @param defaultAccent the current theme accent, used by the VIDIIO preset which
 *   respects the user's separate Color Theme choice.
 */
fun HomeStyle.spec(defaultAccent: Color): HomeStyleSpec = when (this) {
    // Streaming-guide layout (tab strip, dense rows of rated posters) in the Vidiio theme colours.
    HomeStyle.FLIX -> HomeStyleSpec(
        style = this,
        label = "Flix",
        accent = defaultAccent,
        background = null,
        heroKind = HeroKind.CAROUSEL,
        heroHeight = 460.dp,
        heroCenterText = true,
        card = CardKind.PORTRAIT,
        cardWidth = 118.dp,
        cardCorner = 6.dp,
        cardShowTitle = false,
        ratingBadge = true,
        rowGap = 14.dp,
        rowHeaderSize = 20.sp,
        rowHeaderWeight = FontWeight.Bold,
        uppercaseHeaders = false,
        showTop10 = false,
        cardPressScale = 0.96f
    )
    HomeStyle.VIDIIO -> HomeStyleSpec(
        style = this,
        label = "Vidiio",
        accent = defaultAccent,
        background = null,
        heroKind = HeroKind.CAROUSEL,
        heroHeight = 500.dp,
        heroCenterText = true,
        card = CardKind.PORTRAIT,
        cardWidth = 140.dp,
        cardCorner = 20.dp,
        cardShowTitle = false,
        ratingBadge = true,
        rowGap = 16.dp,
        rowHeaderSize = 22.sp,
        rowHeaderWeight = FontWeight.Bold,
        uppercaseHeaders = false,
        showTop10 = true,
        cardPressScale = 0.95f
    )
}
