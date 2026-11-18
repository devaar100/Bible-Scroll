package aarnav100.developer.biblescroll.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Placement and pinning of the verse widget (B11). */
interface HomeScreenWidgets {
    fun hasWidget(): Boolean
    fun canPin(): Boolean

    /** Shows the launcher's "add widget" prompt. Returns false if the launcher refused. */
    fun requestPin(): Boolean
}

@Singleton
class AndroidHomeScreenWidgets @Inject constructor(
    @ApplicationContext private val context: Context,
) : HomeScreenWidgets, WidgetRefresher {

    private val manager get() = AppWidgetManager.getInstance(context)
    private val provider get() = ComponentName(context, VerseWidgetReceiver::class.java)

    override fun hasWidget() = manager.getAppWidgetIds(provider).isNotEmpty()

    override fun canPin() = manager.isRequestPinAppWidgetSupported

    override fun requestPin() = manager.requestPinAppWidget(provider, null, null)

    override suspend fun refresh() = VerseWidget().updateAll(context)
}
