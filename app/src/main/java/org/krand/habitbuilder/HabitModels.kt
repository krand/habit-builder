package org.krand.habitbuilder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

enum class MarkType(val label: String) {
    YES_NO("Yes / no"),
    COUNTER("Counter"),
    SINGLE_CHOICE("Single choice"),
    MULTIPLE_CHOICE("Multiple choice"),
}

enum class DayColor(val label: String) {
    NONE("None"),
    MINT("Mint"),
    AMBER("Amber"),
    ROSE("Rose"),
    BLUE("Blue"),
}

data class Habit(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: MarkType,
    val choices: List<String> = emptyList(),
    val threshold: Int = 1,
)

data class MarkValue(
    val yes: Boolean = false,
    val count: Int = 0,
    val single: String? = null,
    val multiple: Set<String> = emptySet(),
)

data class DayEntry(
    val mark: MarkValue = MarkValue(),
    val color: DayColor = DayColor.NONE,
    val comment: String = "",
)

data class TrackerData(
    val habits: List<Habit> = emptyList(),
    val entries: Map<String, DayEntry> = emptyMap(),
) {
    fun entry(habitId: String, date: LocalDate): DayEntry =
        entries[entryKey(habitId, date)] ?: DayEntry()

    fun withEntry(habitId: String, date: LocalDate, entry: DayEntry): TrackerData {
        val key = entryKey(habitId, date)
        val updated = entries.toMutableMap()
        if (entry == DayEntry()) updated.remove(key) else updated[key] = entry
        return copy(entries = updated)
    }

    fun withUpdatedHabit(habit: Habit): TrackerData {
        val previous = habits.firstOrNull { it.id == habit.id } ?: return this
        val updatedEntries = entries.mapValues { (key, entry) ->
            if (!key.startsWith("${habit.id}|")) entry
            else when {
                previous.type != habit.type -> entry.copy(mark = MarkValue())
                habit.type == MarkType.SINGLE_CHOICE -> entry.copy(
                    mark = entry.mark.copy(single = entry.mark.single?.takeIf { it in habit.choices }),
                )
                habit.type == MarkType.MULTIPLE_CHOICE -> entry.copy(
                    mark = entry.mark.copy(multiple = entry.mark.multiple.intersect(habit.choices.toSet())),
                )
                else -> entry
            }
        }.filterValues { it != DayEntry() }
        return copy(
            habits = habits.map { if (it.id == habit.id) habit else it },
            entries = updatedEntries,
        )
    }

    fun withoutHabit(habitId: String): TrackerData = copy(
        habits = habits.filterNot { it.id == habitId },
        entries = entries.filterKeys { !it.startsWith("$habitId|") },
    )

    fun withHabitOrder(habitIds: List<String>): TrackerData {
        if (habitIds == habits.map { it.id }) return this
        val byId = habits.associateBy { it.id }
        if (habitIds.size != habits.size || habitIds.toSet() != byId.keys) return this
        return copy(habits = habitIds.map { byId.getValue(it) })
    }

    companion object {
        fun entryKey(habitId: String, date: LocalDate): String = "$habitId|$date"

        fun initial() = TrackerData()
    }
}

/** A small local-first store. Entries and habit definitions survive process and app restarts. */
object HabitStore {
    private const val PREFERENCES = "habit_builder_store"
    private const val STATE_KEY = "tracker_state_v1"

    fun load(context: Context): TrackerData {
        val raw = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(STATE_KEY, null) ?: return TrackerData.initial()
        return runCatching {
            val root = JSONObject(raw)
            val habits = root.getJSONArray("habits").toHabits()
            val entries = mutableMapOf<String, DayEntry>()
            root.optJSONArray("entries")?.let { values ->
                for (i in 0 until values.length()) {
                    val item = values.getJSONObject(i)
                    val habitId = item.getString("habitId")
                    val date = LocalDate.parse(item.getString("date"))
                    entries[TrackerData.entryKey(habitId, date)] = item.toEntry()
                }
            }
            TrackerData(habits = habits, entries = entries)
        }.getOrElse { TrackerData.initial() }
    }

    fun save(context: Context, data: TrackerData) {
        val root = JSONObject()
        root.put("habits", JSONArray().apply {
            data.habits.forEach { habit ->
                put(JSONObject().apply {
                    put("id", habit.id)
                    put("name", habit.name)
                    put("type", habit.type.name)
                    put("choices", JSONArray(habit.choices))
                    put("threshold", habit.threshold)
                })
            }
        })
        root.put("entries", JSONArray().apply {
            data.entries.forEach { (key, entry) ->
                val separator = key.lastIndexOf('|')
                if (separator > 0) {
                    put(JSONObject().apply {
                        put("habitId", key.substring(0, separator))
                        put("date", key.substring(separator + 1))
                        put("yes", entry.mark.yes)
                        put("count", entry.mark.count)
                        put("single", entry.mark.single ?: JSONObject.NULL)
                        put("multiple", JSONArray(entry.mark.multiple))
                        put("color", entry.color.name)
                        put("comment", entry.comment)
                    })
                }
            }
        })
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(STATE_KEY, root.toString()).apply()
    }

    private fun JSONArray.toHabits(): List<Habit> = buildList {
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val type = runCatching { MarkType.valueOf(item.optString("type")) }.getOrNull() ?: continue
            val choicesArray = item.optJSONArray("choices") ?: JSONArray()
            val choices = buildList {
                for (choiceIndex in 0 until choicesArray.length()) {
                    choicesArray.optString(choiceIndex).takeIf(String::isNotBlank)?.let(::add)
                }
            }
            val name = item.optString("name").trim()
            if (name.isBlank()) continue
            add(
                Habit(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    type = type,
                    choices = choices,
                    threshold = item.optInt("threshold", 1).coerceAtLeast(1),
                ),
            )
        }
    }

    private fun JSONObject.toEntry(): DayEntry {
        val selected = optJSONArray("multiple") ?: JSONArray()
        val multiple = buildSet {
            for (i in 0 until selected.length()) selected.optString(i).takeIf(String::isNotBlank)?.let(::add)
        }
        return DayEntry(
            mark = MarkValue(
                yes = optBoolean("yes"),
                count = optInt("count").coerceIn(0, 99),
                single = if (isNull("single")) null else optString("single").ifBlank { null },
                multiple = multiple,
            ),
            color = runCatching { DayColor.valueOf(optString("color", "NONE")) }.getOrDefault(DayColor.NONE),
            comment = optString("comment"),
        )
    }
}
