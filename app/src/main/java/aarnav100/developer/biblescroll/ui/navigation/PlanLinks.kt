package aarnav100.developer.biblescroll.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** `biblescroll://plan/<plan id>`: sent only as an explicit intent (plan reminder), opens H9. */
object PlanLinks {
    private const val PREFIX = "biblescroll://plan/"
    private val PATTERN = Regex("""^biblescroll://plan/([a-z0-9]+(?:-[a-z0-9]+)*)/?$""")

    fun build(planId: String): String = "$PREFIX$planId"

    fun parse(link: String?): String? = PATTERN.matchEntire(link ?: return null)?.groupValues?.get(1)
}

/** A plan link waiting to be opened; the app UI navigates to the plan and consumes it. */
@Singleton
class PendingPlanLink @Inject constructor() {
    private val _planId = MutableStateFlow<String?>(null)
    val planId: StateFlow<String?> = _planId.asStateFlow()

    fun post(planId: String) {
        _planId.value = planId
    }

    fun consume(planId: String) {
        _planId.compareAndSet(planId, null)
    }
}
