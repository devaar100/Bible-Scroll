package aarnav100.developer.biblescroll.feature.home

import aarnav100.developer.biblescroll.R
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable

/**
 * Stock photos bundled for Home (devotional cover, plan cards). Free-licence photos from Unsplash (Unsplash License),
 * chosen 2026-11-25 by the user as an exception to "original art". The Unsplash License needs no credit, and the user
 * asked for none in the app (2026-11-25); [Photo.photographer] and [Photo.source] are kept here as the record of where
 * each photo came from. WebP in res/drawable-nodpi, about 380 KB together. No network: they ship with the app.
 */
object HomePhotos {
    @Immutable
    data class Photo(@DrawableRes val res: Int, val photographer: String, val source: String)

    /** One photo per devotional cover style, so a devotional's photo matches its drawn cover's mood. */
    private val byCoverStyle = mapOf(
        "dawn" to Photo(R.drawable.photo_sunset_field, "Kenneth Thewissen", "https://unsplash.com/photos/D76DklsG-5U"),
        "meadow" to Photo(R.drawable.photo_green_hills, "Andrew Ridley", "https://unsplash.com/photos/Kt5hRENuotI"),
        "ocean" to Photo(R.drawable.photo_calm_coast, "Paul Jarvis", "https://unsplash.com/photos/gkT4FfgHO5o"),
        "ember" to Photo(R.drawable.photo_red_rocks, "Philippe Wuyts", "https://unsplash.com/photos/_h7aBovKia4"),
        "dusk" to Photo(R.drawable.photo_mountain_peaks, "Go Wild", "https://unsplash.com/photos/V0yAek6BgGk"),
        "stone" to Photo(R.drawable.photo_forest_cliffs, "Christian Joudrey", "https://unsplash.com/photos/mWRR1xj95hg"),
        "linen" to Photo(R.drawable.photo_field_path, "Paul Jarvis", "https://unsplash.com/photos/Ven2CV8IJ5A"),
        "night" to Photo(R.drawable.photo_northern_lights, "Vashishtha Jogi", "https://unsplash.com/photos/bClr95glx6k"),
    )

    /** Plan cards: picked by plan id, so a plan keeps the same photo everywhere. */
    private val forPlans = listOf(
        Photo(R.drawable.photo_fjord, "Alexey Topolyanskiy", "https://unsplash.com/photos/-oWyJoSqBRM"),
        Photo(R.drawable.photo_waterfall, "Andrew Coelho", "https://unsplash.com/photos/VB-w_3dnyvI"),
        Photo(R.drawable.photo_misty_river, "Steve Carter", "https://unsplash.com/photos/Ixp4YhCKZkI"),
        Photo(R.drawable.photo_forest_stream, "Jerry Adney", "https://unsplash.com/photos/_WiFMBRT7Aw"),
        Photo(R.drawable.photo_wave, "Tim Marshall", "https://unsplash.com/photos/yEOCA6oiVqg"),
        Photo(R.drawable.photo_rocky_falls, "Paul Jarvis", "https://unsplash.com/photos/NYDo21ssGao"),
        Photo(R.drawable.photo_snowy_camp, "Wolfgang Lutz", "https://unsplash.com/photos/yOujaSETXlo"),
        Photo(R.drawable.photo_green_hills, "Andrew Ridley", "https://unsplash.com/photos/Kt5hRENuotI"),
    )

    /** Unknown styles (a newer pipeline) fall back to linen's photo, as the drawn cover does. */
    fun forDevotional(coverStyle: String): Photo = byCoverStyle[coverStyle] ?: byCoverStyle.getValue("linen")

    /** Stable per plan id (String.hashCode is defined by the JLS). */
    fun forPlan(planId: String): Photo = forPlans[Math.floorMod(planId.hashCode(), forPlans.size)]

}
