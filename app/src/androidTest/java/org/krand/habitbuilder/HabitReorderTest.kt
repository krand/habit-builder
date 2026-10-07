package org.krand.habitbuilder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class HabitReorderTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private lateinit var originalData: TrackerData
    private val habits = listOf(
        Habit(id = "alpha", name = "Alpha", type = MarkType.YES_NO),
        Habit(id = "beta", name = "Beta", type = MarkType.COUNTER),
        Habit(id = "gamma", name = "Gamma", type = MarkType.SINGLE_CHOICE, choices = listOf("One", "Two")),
    )

    @Before
    fun seedHabits() {
        originalData = HabitStore.load(compose.activity)
        HabitStore.save(compose.activity, TrackerData(habits = habits).withEntry(
            "alpha", LocalDate.now(), DayEntry(color = DayColor.MINT, comment = "Alpha's comment"),
        ))
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    @After
    fun restoreData() {
        HabitStore.save(compose.activity, originalData)
    }

    @Test
    fun dragOnEachTabSharesAndPersistsOrder() {
        drag("alpha", "beta")
        assertOrder("beta", "alpha", "gamma")
        compose.onNodeWithText("Week").performClick()
        assertPanelOrder("beta", "alpha")
        drag("alpha", "beta")
        assertOrder("alpha", "beta", "gamma")
        compose.onNodeWithText("Habits").performClick()
        assertPanelOrder("alpha", "beta")
        drag("alpha", "gamma")
        assertOrder("beta", "gamma", "alpha")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertPanelOrder("beta", "gamma")
        assertOrder("beta", "gamma", "alpha")
        val entry = HabitStore.load(compose.activity).entry("alpha", LocalDate.now())
        assertEquals(DayColor.MINT, entry.color)
        assertEquals("Alpha's comment", entry.comment)
    }

    @Test
    fun cancelledDragDoesNotSavePreviewOrder() {
        drag("alpha", "beta", cancel = true)
        assertOrder("alpha", "beta", "gamma")
        assertPanelOrder("alpha", "beta")
    }

    @Test
    fun normalTapsAndWeekSelectionStillWorkAfterReorder() {
        drag("alpha", "beta")
        compose.onNode(isToggleable()).performClick()
        assertTrue(HabitStore.load(compose.activity).entry("alpha", LocalDate.now()).mark.yes)
        compose.onNodeWithContentDescription("Edit comment for Alpha").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Week").performClick()
        val todayLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
        compose.onNodeWithContentDescription("Alpha, $todayLabel,", substring = true).performClick()
        drag("alpha", "beta")
        compose.onNode(isToggleable()).performClick()
        assertTrue(!HabitStore.load(compose.activity).entry("alpha", LocalDate.now()).mark.yes)
        compose.onNodeWithText("Habits").performClick()
        compose.onNodeWithContentDescription("Edit Alpha").performClick()
        compose.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun accessibilityCanMoveHabitsInBothDirections() {
        val downActions = compose.onNodeWithTag("habit-panel-alpha").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle {
            assertTrue(downActions.first { it.label == "Move Alpha down" }.action())
        }
        assertOrder("beta", "alpha", "gamma")
        val upActions = compose.onNodeWithTag("habit-panel-alpha").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle {
            assertTrue(upActions.first { it.label == "Move Alpha up" }.action())
        }
        assertOrder("alpha", "beta", "gamma")
    }

    @Test
    fun edgeScrollingMovesBeyondInitiallyVisiblePanelsInBothDirections() {
        val longHabits = habits + (3..19).map { Habit(id = "habit-$it", name = "Habit $it", type = MarkType.YES_NO) }
        HabitStore.save(compose.activity, TrackerData(habits = longHabits))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Habits").performClick()
        holdAtEdge("alpha", bottom = true)
        val afterDown = HabitStore.load(compose.activity).habits
        assertTrue("Should move beyond the initial viewport", afterDown.indexOfFirst { it.id == "alpha" } > 8)
        assertEquals(longHabits.toSet(), afterDown.toSet())
        compose.onNodeWithTag("habit-list").performScrollToIndex(afterDown.indexOfFirst { it.id == "alpha" } + 1)
        holdAtEdge("alpha", bottom = false)
        val afterUp = HabitStore.load(compose.activity).habits
        assertTrue("Should move back towards the start", afterUp.indexOfFirst { it.id == "alpha" } < 5)
        assertEquals(longHabits.toSet(), afterUp.toSet())
    }

    @Test
    fun ordinaryScrollingDoesNotReorderHabits() {
        val longHabits = habits + (3..12).map { Habit(id = "habit-$it", name = "Habit $it", type = MarkType.YES_NO) }
        HabitStore.save(compose.activity, TrackerData(habits = longHabits))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("habit-list").performTouchInput { swipeUp() }
        assertOrder(*longHabits.map { it.id }.toTypedArray())
    }

    private fun holdAtEdge(id: String, bottom: Boolean) {
        val source = compose.onNodeWithTag("habit-panel-$id")
        val sourceBounds = source.fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("habit-list").fetchSemanticsNode().boundsInRoot
        val edgeY = if (bottom) viewport.bottom - 12f else viewport.top + 12f
        val start = Offset(24f, 24f)
        val distance = edgeY - sourceBounds.top - start.y
        compose.mainClock.autoAdvance = false
        try {
            source.performTouchInput {
                down(start)
                advanceEventTime(650)
                for (step in 1..30) moveTo(start + Offset(0f, distance * step / 30), delayMillis = 20)
            }
            compose.mainClock.advanceTimeBy(2_500)
            val draggedBounds = source.fetchSemanticsNode().boundsInRoot
            assertTrue("The dragged panel should stay under the finger while scrolling: bottom=$bottom, edge=$edgeY, panel=$draggedBounds", edgeY in draggedBounds.top..draggedBounds.bottom)
            source.performTouchInput {
                advanceEventTime(2_500)
                up()
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
    }

    private fun drag(from: String, to: String, cancel: Boolean = false) {
        val source = compose.onNodeWithTag("habit-panel-$from")
        val sourceBounds = source.fetchSemanticsNode().boundsInRoot
        val destinationBounds = compose.onNodeWithTag("habit-panel-$to").fetchSemanticsNode().boundsInRoot
        val distance = destinationBounds.center.y - sourceBounds.center.y
        source.performTouchInput {
            val start = Offset(24f, 24f)
            down(start)
            advanceEventTime(650)
            for (step in 1..20) moveTo(start + Offset(0f, distance * step / 20), delayMillis = 20)
            if (cancel) cancel() else up()
        }
        compose.waitForIdle()
    }

    private fun assertOrder(vararg ids: String) {
        compose.runOnIdle { assertEquals(ids.toList(), HabitStore.load(compose.activity).habits.map { it.id }) }
    }

    private fun assertPanelOrder(first: String, second: String) {
        val firstY = compose.onNodeWithTag("habit-panel-$first").fetchSemanticsNode().boundsInRoot.top
        val secondY = compose.onNodeWithTag("habit-panel-$second").fetchSemanticsNode().boundsInRoot.top
        assertTrue("$first should precede $second", firstY < secondY)
    }
}
