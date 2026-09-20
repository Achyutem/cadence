package dev.achyutem.cadence.core.designsystem.component

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Long-press drag reordering for a `LazyColumn`.
 *
 * Written by hand rather than pulled in as a dependency, for the same reason as the heatmap and
 * the Markdown parser: the whole behaviour is a few dozen lines against `LazyListState`, and a
 * reorder library brings its own animation system and item-key conventions to a list that already
 * has both.
 *
 * ### How it works
 *
 * The dragged item is identified by index, and its visual offset accumulates as the finger moves.
 * On every move the item under the finger's *centre* is found, and if it is a different item the
 * two swap immediately; the list animates the swap itself through `animateItem`. Nothing is
 * written to the database until the finger lifts, so a drag across ten rows is one write rather
 * than ten.
 *
 * Auto-scroll at the edges is deliberately omitted. It is the fiddliest part of a reorder
 * implementation and the least used; a list long enough to need it is a list that wants search.
 */
class ReorderState internal constructor(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onSettle: () -> Unit,
) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    var dragOffset by mutableStateOf(0f)
        private set

    private var draggingItem: LazyListItemInfo? = null

    fun start(offsetY: Float) {
        val item = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { offsetY.toInt() in it.offset..(it.offset + it.size) }
            ?: return
        draggingItem = item
        draggingIndex = item.index
        dragOffset = 0f
    }

    fun drag(delta: Float) {
        val current = draggingIndex ?: return
        val item = draggingItem ?: return
        dragOffset += delta

        val centre = item.offset + item.size / 2 + dragOffset
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
            centre.toInt() in candidate.offset..(candidate.offset + candidate.size) &&
                candidate.index != current
        } ?: return

        onMove(current, target.index)
        draggingIndex = target.index
        // The offset is rebased onto the new slot so the item stays under the finger rather than
        // jumping by one row height every time a swap happens.
        dragOffset += (item.offset - target.offset)
        draggingItem = target
    }

    fun stop() {
        if (draggingIndex != null) onSettle()
        draggingIndex = null
        dragOffset = 0f
        draggingItem = null
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
    onSettle: () -> Unit,
): ReorderState {
    val scope = rememberCoroutineScope()
    return remember(listState) { ReorderState(listState, scope, onMove, onSettle) }
}

/**
 * Attach to the `LazyColumn` itself.
 *
 * Long press to pick up, matching every other list on Android. A plain drag would fight the
 * list's own scrolling, and a dedicated drag handle costs a permanent affordance on every row for
 * a gesture most people use rarely.
 */
fun Modifier.reorderable(state: ReorderState): Modifier = this.pointerInput(state) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> state.start(offset.y) },
        onDrag = { change, amount ->
            change.consume()
            state.drag(amount.y)
        },
        onDragEnd = { state.stop() },
        onDragCancel = { state.stop() },
    )
}

/** Attach to each row so the one being dragged follows the finger and lifts above the rest. */
fun Modifier.reorderableItem(state: ReorderState, index: Int): Modifier {
    val dragging = state.draggingIndex == index
    if (!dragging) return this
    return this
        .zIndex(1f)
        // graphicsLayer rather than offset: the translation happens at draw time, so a finger
        // moving across the screen never re-measures the list underneath it.
        .graphicsLayer { translationY = state.dragOffset }
}
