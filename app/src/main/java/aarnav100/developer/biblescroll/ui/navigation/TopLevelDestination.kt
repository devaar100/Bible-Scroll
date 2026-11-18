package aarnav100.developer.biblescroll.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector
import aarnav100.developer.biblescroll.R
import kotlinx.serialization.Serializable

@Serializable data object CommunityRoute
@Serializable data object DiscoverRoute
@Serializable data object HomeRoute
@Serializable data object BibleRoute

enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    COMMUNITY(CommunityRoute, R.string.tab_community, Icons.Filled.Groups, Icons.Outlined.Groups),
    DISCOVER(DiscoverRoute, R.string.tab_discover, Icons.Filled.Explore, Icons.Outlined.Explore),
    HOME(HomeRoute, R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    BIBLE(BibleRoute, R.string.tab_bible, Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
}
