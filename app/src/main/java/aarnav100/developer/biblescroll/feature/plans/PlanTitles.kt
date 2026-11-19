package aarnav100.developer.biblescroll.feature.plans

import aarnav100.developer.biblescroll.ui.text.formatList
import aarnav100.developer.biblescroll.R
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Default names for custom plans ("John in 21 days"). An interface so ViewModel tests needn't load resources. */
interface PlanTitles {
    /** [names] in canonical order; [contiguous] = the books are consecutive in the Bible. */
    fun title(names: List<String>, contiguous: Boolean, days: Int): String

    /** "John" / "Genesis and Exodus" for the plan's short description, in the language's list style. */
    fun books(names: List<String>): String
}

class ResourcePlanTitles @Inject constructor(@ApplicationContext private val context: Context) : PlanTitles {
    override fun books(names: List<String>): String = formatList(names, context.resources.configuration.locales[0])

    override fun title(names: List<String>, contiguous: Boolean, days: Int): String {
        val res = context.resources
        val length = res.getQuantityString(R.plurals.plans_days, days, days)
        return when {
            names.size == 1 -> res.getString(R.string.plans_custom_title_one, names[0], length)
            names.size == 2 -> res.getString(R.string.plans_custom_title_two, names[0], names[1], length)
            contiguous -> res.getString(R.string.plans_custom_title_range, names.first(), names.last(), length)
            else -> res.getQuantityString(R.plurals.plans_custom_title_more, names.size - 1, names[0], names.size - 1, length)
        }
    }
}
