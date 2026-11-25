package aarnav100.developer.biblescroll.ui.theme

import aarnav100.developer.biblescroll.R
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Both fonts are bundled variable fonts (SIL Open Font License) from Google Fonts.
private val weights = listOf(
    FontWeight.Light,
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
)

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int) = FontFamily(
    weights.map { weight ->
        Font(
            resId = resId,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/** Scripture and headlines. */
val Literata = variableFamily(R.font.literata)

/** UI text. */
val Inter = variableFamily(R.font.inter)

private val base = Typography()

val Typography = Typography(
    // Headlines / display: Literata
    displayLarge = base.displayLarge.copy(fontFamily = Literata),
    displayMedium = base.displayMedium.copy(fontFamily = Literata),
    displaySmall = base.displaySmall.copy(fontFamily = Literata),
    headlineLarge = base.headlineLarge.copy(fontFamily = Literata, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Literata, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Literata, fontWeight = FontWeight.SemiBold),
    // UI: Inter
    // Titles and body: no extra tracking. Material's defaults (0.15–0.5 sp) were tuned for Roboto; Inter already has
    // open spacing, so they spread letters and made the gaps between words look wide.
    titleLarge = base.titleLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = base.titleMedium.copy(fontFamily = Inter, letterSpacing = 0.sp),
    titleSmall = base.titleSmall.copy(fontFamily = Inter, letterSpacing = 0.sp),
    bodyLarge = base.bodyLarge.copy(fontFamily = Inter, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = Inter, letterSpacing = 0.sp),
    bodySmall = base.bodySmall.copy(fontFamily = Inter, letterSpacing = 0.sp),
    labelLarge = base.labelLarge.copy(fontFamily = Inter),
    labelMedium = base.labelMedium.copy(fontFamily = Inter),
    labelSmall = base.labelSmall.copy(fontFamily = Inter),
)

/** Full-screen verse text in the Bible feed. */
val ScriptureTextStyle = TextStyle(
    fontFamily = Literata,
    fontWeight = FontWeight.Normal,
    fontSize = 26.sp,
    lineHeight = 38.sp,
)
