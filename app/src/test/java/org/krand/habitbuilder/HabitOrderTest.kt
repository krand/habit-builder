package org.krand.habitbuilder

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HabitOrderTest {
    private val first = Habit(id = "first", name = "Read", type = MarkType.COUNTER, threshold = 3)
    private val second = Habit(id = "second", name = "Study", type = MarkType.MULTIPLE_CHOICE, choices = listOf("English", "Writing"))
    private val third = Habit(id = "third", name = "Walk", type = MarkType.YES_NO)
    private val data = TrackerData(habits = listOf(first, second, third)).withEntry(
        first.id, LocalDate.of(2026, 10, 7),
        DayEntry(mark = MarkValue(count = 4), color = DayColor.MINT, comment = "Keep this entry"),
    )

    @Test
    fun reorderingKeepsDefinitionsAndEntries() {
        val reordered = data.withHabitOrder(listOf(third.id, first.id, second.id))
        assertEquals(listOf(third, first, second), reordered.habits)
        assertSame(data.entries, reordered.entries)
        assertEquals(listOf(first, second, third), data.habits)
    }

    @Test
    fun invalidOrdersCannotRemoveDuplicateOrReplaceHabits() {
        listOf(
            listOf(first.id, second.id),
            listOf(first.id, first.id, third.id),
            listOf(first.id, second.id, "unknown"),
            listOf(first.id, second.id, third.id, third.id),
        ).forEach { assertSame(data, data.withHabitOrder(it)) }
    }

    @Test
    fun unchangedAndEmptyOrdersAreNoOps() {
        assertSame(data, data.withHabitOrder(data.habits.map { it.id }))
        val empty = TrackerData()
        assertSame(empty, empty.withHabitOrder(emptyList()))
    }
}
