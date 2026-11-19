package aarnav100.developer.biblescroll.widget

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.MainActivity
import aarnav100.developer.biblescroll.ui.navigation.VerseLinks
import aarnav100.developer.biblescroll.ui.theme.DarkColors
import aarnav100.developer.biblescroll.ui.theme.LightColors
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetVerses(): WidgetVerseRepository
}

private val WidgetColors = ColorProviders(light = LightColors, dark = DarkColors)

/** Home-screen verse widget: a fixed verse or the Verse of the Day. Follows light/dark via GlanceTheme; tap opens the verse in the feed. */
class VerseWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).widgetVerses()
        // Verse of the Day mode: pick up a date (or zone) change whenever the widget is drawn.
        repo.refreshDate()
        provideContent {
            val display by repo.display.collectAsState(initial = null)
            GlanceTheme(colors = WidgetColors) {
                WidgetContent(context, display)
            }
        }
    }

    companion object {
        val SMALL = DpSize(140.dp, 100.dp)
        val MEDIUM = DpSize(260.dp, 140.dp)
    }
}

@Composable
private fun WidgetContent(context: Context, display: WidgetDisplay?) {
    val medium = LocalSize.current.width >= VerseWidget.MEDIUM.width &&
        LocalSize.current.height >= VerseWidget.MEDIUM.height
    val open = Intent(context, MainActivity::class.java).apply {
        display?.let { data = Uri.parse(VerseLinks.build(it.ref)) }
        // Reuse the running activity (onNewIntent) instead of stacking a new one.
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.surface)
            .padding(if (medium) 18.dp else 12.dp)
            .clickable(actionStartActivity(open)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (display != null) {
            if (display.isVerseOfTheDay && medium) {
                Text(
                    text = LocalContext.current.getString(R.string.votd_title),
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                    ),
                )
                Spacer(GlanceModifier.height(6.dp))
            }
            // Glance can't load bundled fonts, so Scripture uses the system serif here.
            Text(
                text = display.text,
                maxLines = if (medium) 6 else 3,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontFamily = FontFamily.Serif,
                    fontSize = if (medium) 17.sp else 14.sp,
                ),
            )
            Spacer(GlanceModifier.height(if (medium) 10.dp else 6.dp))
            Text(
                text = if (medium) LocalContext.current.getString(R.string.two_parts, display.reference, display.translationId) else display.reference,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = if (medium) 13.sp else 12.sp,
                ),
            )
        }
    }
}

class VerseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VerseWidget()

    /** First widget placed: arm the Verse of the Day midnight redraw if that mode is on. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        syncMidnightUpdate(context)
    }

    /** Last widget removed: drop the midnight alarm. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        syncMidnightUpdate(context)
    }

    private fun syncMidnightUpdate(context: Context) {
        val repo = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java).widgetVerses()
        // Not goAsync: GlanceAppWidgetReceiver may already hold this broadcast. The work is one DataStore read.
        widgetScope.launch {
            try {
                repo.syncMidnightUpdate()
            } catch (e: Exception) {
                Log.e("VerseWidget", "Couldn't sync the Verse of the Day redraw", e)
            }
        }
    }

    private companion object {
        val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
