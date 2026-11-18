package aarnav100.developer.biblescroll.feature.share

import aarnav100.developer.biblescroll.ui.theme.CharcoalBackground
import aarnav100.developer.biblescroll.ui.theme.CharcoalSurfaceContainerHigh
import aarnav100.developer.biblescroll.ui.theme.Chalk
import aarnav100.developer.biblescroll.ui.theme.ChalkMuted
import aarnav100.developer.biblescroll.ui.theme.Crimson as CrimsonToken
import aarnav100.developer.biblescroll.ui.theme.CrimsonDarkContainer
import aarnav100.developer.biblescroll.ui.theme.CrimsonLight
import aarnav100.developer.biblescroll.ui.theme.Gold
import aarnav100.developer.biblescroll.ui.theme.GoldContainer
import aarnav100.developer.biblescroll.ui.theme.Ink
import aarnav100.developer.biblescroll.ui.theme.InkMuted
import aarnav100.developer.biblescroll.ui.theme.Inter
import aarnav100.developer.biblescroll.ui.theme.Literata
import aarnav100.developer.biblescroll.ui.theme.OnCrimsonDarkContainer
import aarnav100.developer.biblescroll.ui.theme.PaperBackground
import aarnav100.developer.biblescroll.ui.theme.PaperSurfaceContainerHigh
import aarnav100.developer.biblescroll.ui.theme.TaupeContainer
import androidx.annotation.StringRes
import aarnav100.developer.biblescroll.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** B10 backgrounds, all from theme tokens (no photos). */
@Immutable
enum class CardStyle(
    @StringRes val label: Int,
    val background: Brush,
    val text: Color,
    val muted: Color,
    val accent: Color,
) {
    Paper(
        R.string.share_style_paper,
        Brush.verticalGradient(listOf(PaperBackground, PaperSurfaceContainerHigh)),
        text = Ink, muted = InkMuted, accent = CrimsonToken,
    ),
    Charcoal(
        R.string.share_style_charcoal,
        Brush.verticalGradient(listOf(CharcoalSurfaceContainerHigh, CharcoalBackground)),
        text = Chalk, muted = ChalkMuted, accent = CrimsonLight,
    ),
    Crimson(
        R.string.share_style_crimson,
        Brush.linearGradient(listOf(CrimsonDarkContainer, Color(0xFF3A0510))),
        text = OnCrimsonDarkContainer, muted = CrimsonLight, accent = CrimsonLight,
    ),
    Sepia(
        R.string.share_style_sepia,
        Brush.verticalGradient(listOf(GoldContainer, TaupeContainer)),
        text = Ink, muted = InkMuted, accent = Gold,
    ),
}

@Immutable
data class CardContent(
    val verseText: String,
    /** e.g. "John 3:16" */
    val reference: String,
    val translationId: String,
    val appName: String,
)

private fun verseStyle(fontPx: Float, layout: CardLayout) = TextStyle(
    fontFamily = Literata,
    fontWeight = FontWeight.Normal,
    fontSize = fontPx.sp,
    lineHeight = layout.lineHeight.em,
)

/** Density where 1.sp == 1 card pixel, so measurements are independent of the device and font scale. */
private val CardDensity = Density(density = 1f, fontScale = 1f)

/** Font size (card px) for [text] in [format]: as large as fits, never below the layout's minimum. */
fun TextMeasurer.fitVerse(text: String, format: CardFormat): FitResult {
    val layout = CardLayout.of(format)
    return fitFontSize(layout.minFont, layout.maxFont, CardLayout.FONT_STEP) { size ->
        val result = measure(
            text = text,
            style = verseStyle(size, layout),
            constraints = Constraints(maxWidth = layout.textWidth.toInt()),
            density = CardDensity,
            layoutDirection = LayoutDirection.Ltr,
        )
        result.size.height <= layout.textMaxHeight
    }
}

@Composable
fun rememberVerseFit(text: String, format: CardFormat): FitResult {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    // Re-fit if fonts finish loading under a different resolver.
    val resolver = LocalFontFamilyResolver.current
    return remember(text, format, resolver) { measurer.fitVerse(text, format) }
}

/**
 * The card, filling its size. Everything is in card px times `unit` (= this card's width / 1080),
 * so a 360 dp preview and the 1080 px export are the same picture at different scales.
 */
@Composable
fun VerseCard(
    content: CardContent,
    format: CardFormat,
    style: CardStyle,
    fit: FitResult,
    modifier: Modifier = Modifier,
) {
    val layout = CardLayout.of(format)
    BoxWithConstraints(modifier.background(style.background)) {
        val unit: Dp = maxWidth / format.widthPx
        val density = LocalDensity.current
        // Dp -> sp without fontScale, so the user's system font size can't change the card.
        fun cardSp(px: Float) = with(density) { (unit * px).toSp() }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = unit * layout.padding, vertical = unit * layout.padding),
        ) {
            Spacer(Modifier.weight(1f))
            // Exactly the box fitVerse measured against, so preview, export and fit always agree.
            Box(
                Modifier
                    .height(unit * layout.textMaxHeight)
                    .fillMaxWidth(),
                contentAlignment = Alignment.CenterStart,
            ) {
                // BasicText with the exact style fitVerse measured: M3 Text would merge LocalTextStyle
                // (letter spacing, line-height trimming) and wrap differently from the measurement.
                BasicText(
                    text = content.verseText,
                    style = verseStyle(fit.fontSize, layout).copy(color = style.text, fontSize = cardSp(fit.fontSize)),
                    overflow = TextOverflow.Ellipsis,
                    // Only the overflow case needs a cap; ellipsis there beats clipping mid-line.
                    maxLines = if (fit.overflows) maxLinesFor(layout, fit.fontSize) else Int.MAX_VALUE,
                )
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(unit * 40))
            Box(
                Modifier
                    .width(unit * 72)
                    .height(unit * 6)
                    .background(style.accent),
            )
            Spacer(Modifier.height(unit * 24))
            // BasicText + em line heights: M3 Text's unscaled 24 sp line height would grow these
            // lines in a small preview and squeeze the verse box.
            BasicText(
                text = "${content.reference} · ${content.translationId}",
                maxLines = 1,
                style = TextStyle(
                    color = style.text,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = cardSp(layout.referenceFont),
                    lineHeight = 1.3.em,
                ),
            )
            Spacer(Modifier.height(unit * 56))
            BasicText(
                text = content.appName.uppercase(),
                maxLines = 1,
                style = TextStyle(
                    color = style.muted,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = cardSp(layout.brandFont),
                    lineHeight = 1.3.em,
                    letterSpacing = 0.18.em,
                ),
            )
        }
    }
}

internal fun maxLinesFor(layout: CardLayout, fontPx: Float): Int =
    (layout.textMaxHeight / (fontPx * layout.lineHeight)).toInt().coerceAtLeast(1)
