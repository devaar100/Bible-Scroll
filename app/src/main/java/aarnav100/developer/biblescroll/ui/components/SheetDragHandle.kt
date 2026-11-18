package aarnav100.developer.biblescroll.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The standard handle, in a 48dp-wide box. The sheet makes the handle a TalkBack/tap target
 * (expand, collapse, dismiss) and the default one is only 32dp wide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetDragHandle() {
    Box(Modifier.widthIn(min = 48.dp), contentAlignment = Alignment.Center) {
        BottomSheetDefaults.DragHandle()
    }
}
