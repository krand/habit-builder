package org.krand.habitbuilder

import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.isActive

/** One shared gesture implementation for all three habit lists. Only a completed drag is saved. */
@Composable
internal fun ReorderableHabitList(
    habits: List<Habit>,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    footer: @Composable () -> Unit = {},
    panel: @Composable (Habit) -> Unit,
) {
    val listState = rememberLazyListState()
    val state = remember(habits, listState) { HabitReorderState(habits, listState) }
    val currentOnReorder by rememberUpdatedState(onReorder)
    val haptics = LocalHapticFeedback.current
    val edgeSize = with(LocalDensity.current) { 56.dp.toPx() }
    val maxScrollSpeed = with(LocalDensity.current) { 900.dp.toPx() }

    LaunchedEffect(state, state.draggedId) {
        if (state.draggedId == null) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (isActive && state.draggedId != null) {
            val frame = withFrameNanos { it }
            val elapsedSeconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.05f)
            previousFrame = frame
            val layout = listState.layoutInfo
            val edgeFraction = when {
                state.pointerY < layout.viewportStartOffset + edgeSize ->
                    -((layout.viewportStartOffset + edgeSize - state.pointerY) / edgeSize).coerceIn(0f, 1f)
                state.pointerY > layout.viewportEndOffset - edgeSize ->
                    ((state.pointerY - layout.viewportEndOffset + edgeSize) / edgeSize).coerceIn(0f, 1f)
                else -> 0f
            }
            if (edgeFraction != 0f) {
                val consumed = listState.scrollBy(edgeFraction * maxScrollSpeed * elapsedSeconds)
                state.onScroll(consumed)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().testTag("habit-list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header", contentType = "header") {
            Column {
                header()
                if (habits.size > 1) {
                    Text(
                        "Hold and drag a panel to reorder",
                        modifier = Modifier.padding(bottom = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        itemsIndexed(state.habits, key = { _, habit -> habit.id }, contentType = { _, _ -> "habit" }) { index, habit ->
            val dragging = state.draggedId == habit.id
            Box(
                Modifier.fillMaxWidth()
                    .zIndex(if (dragging) 1f else 0f)
                    .animateItem(placementSpec = if (dragging) null else spring())
                    .graphicsLayer { translationY = if (dragging) state.draggedTranslation() else 0f }
                    .shadow(if (dragging) 8.dp else 0.dp, RoundedCornerShape(18.dp))
                    .testTag("habit-panel-${habit.id}")
                    .semantics {
                        customActions = buildList {
                            if (index > 0) add(CustomAccessibilityAction("Move ${habit.name} up") {
                                currentOnReorder(state.movedOrder(index, index - 1))
                                true
                            })
                            if (index < state.habits.lastIndex) add(CustomAccessibilityAction("Move ${habit.name} down") {
                                currentOnReorder(state.movedOrder(index, index + 1))
                                true
                            })
                        }
                    }
                    .pointerInput(state, habit.id) {
                        if (habits.size < 2) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = { position ->
                                if (state.startDrag(habit.id, position.y)) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                state.dragBy(amount.y)
                            },
                            onDragEnd = {
                                val order = state.habits.map { it.id }
                                state.finishDrag()
                                currentOnReorder(order)
                            },
                            onDragCancel = { state.cancelDrag() },
                        )
                    },
            ) {
                panel(habit)
            }
        }
        item(key = "footer", contentType = "footer") { footer() }
    }
}

private class HabitReorderState(
    private val originalHabits: List<Habit>,
    private val listState: LazyListState,
) {
    var habits by mutableStateOf(originalHabits)
        private set
    var draggedId by mutableStateOf<String?>(null)
        private set
    var pointerY by mutableFloatStateOf(0f)
        private set
    private var draggedTop by mutableFloatStateOf(0f)
    private var draggedHeight = 0

    fun startDrag(id: String, localY: Float): Boolean {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return false
        draggedId = id
        draggedTop = item.offset.toFloat()
        draggedHeight = item.size
        pointerY = item.offset + localY
        return true
    }

    fun dragBy(distance: Float) {
        if (draggedId == null) return
        draggedTop += distance
        pointerY += distance
        moveIfNeeded(distance)
    }

    fun onScroll(distance: Float) {
        if (draggedId == null || distance == 0f) return
        // The list moves underneath the panel; its position under the finger stays fixed.
        moveIfNeeded(distance)
    }

    fun draggedTranslation(): Float {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedId } ?: return 0f
        return draggedTop - item.offset
    }

    fun movedOrder(from: Int, to: Int): List<String> = habits.map { it.id }.toMutableList().apply {
        add(to, removeAt(from))
    }

    private fun moveIfNeeded(direction: Float) {
        if (direction == 0f) return
        val from = habits.indexOfFirst { it.id == draggedId }
        if (from < 0) return
        val layout = listState.layoutInfo
        val center = (draggedTop + draggedHeight / 2f).coerceIn(
            layout.viewportStartOffset.toFloat(),
            layout.viewportEndOffset - 1f,
        )
        val candidates = layout.visibleItemsInfo.filter { item ->
            val to = habits.indexOfFirst { it.id == item.key }
            to >= 0 && if (direction > 0) to > from && center >= item.offset
            else to < from && center <= item.offset + item.size
        }
        val target = (if (direction > 0) candidates.lastOrNull() else candidates.firstOrNull()) ?: return
        val to = habits.indexOfFirst { it.id == target.key }
        // Keep the viewport anchored when a reorder involves the first visible item.
        val firstIndex = listState.firstVisibleItemIndex
        if (from + 1 == firstIndex || to + 1 == firstIndex) {
            listState.requestScrollToItem(firstIndex, listState.firstVisibleItemScrollOffset)
        }
        habits = habits.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun finishDrag() {
        draggedId = null
    }

    fun cancelDrag() {
        habits = originalHabits
        finishDrag()
    }
}
