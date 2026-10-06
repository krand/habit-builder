package org.krand.habitbuilder

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppTab(val title: String, val emoji: String) {
    TODAY("Today", "☑"),
    WEEK("Week", "▦"),
    HABITS("Habits", "≡"),
}

private data class SelectedWeekDays(
    val habitId: String,
    val dates: Set<LocalDate>,
    val activeDate: LocalDate,
)

private data class ColorTarget(val habitId: String, val dates: List<LocalDate>)

@Composable
fun HabitBuilderApp() {
    val context = LocalContext.current
    var data by remember { mutableStateOf(HabitStore.load(context)) }
    val currentData by rememberUpdatedState(data)
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(HabitExcelExporter.MIME_TYPE),
    ) { uri ->
        if (uri != null) {
            val snapshot = currentData
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: error("Could not open the selected file")
                        HabitExcelExporter.write(snapshot, output)
                    }
                }.onSuccess {
                    Toast.makeText(context, "Excel file saved", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(context, "Export failed: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    var currentTabName by rememberSaveable { mutableStateOf(AppTab.TODAY.name) }
    val currentTab = AppTab.valueOf(currentTabName)
    val today = LocalDate.now()
    var weekStartIso by rememberSaveable { mutableStateOf(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()) }
    val weekStart = LocalDate.parse(weekStartIso)
    var weekSelection by remember { mutableStateOf<SelectedWeekDays?>(null) }
    var historyHabitId by rememberSaveable { mutableStateOf<String?>(null) }
    var historyMonthIso by rememberSaveable { mutableStateOf(YearMonth.from(today).atDay(1).toString()) }
    var historyDateIso by rememberSaveable { mutableStateOf(today.toString()) }
    var colorTarget by remember { mutableStateOf<ColorTarget?>(null) }
    var commentTarget by remember { mutableStateOf<Pair<String, LocalDate>?>(null) }
    var settingsHabitId by remember { mutableStateOf<String?>(null) }
    var showAddHabit by remember { mutableStateOf(false) }

    fun setData(updated: TrackerData) {
        data = updated
        HabitStore.save(context, updated)
    }

    fun updateEntry(habitId: String, date: LocalDate, transform: (DayEntry) -> DayEntry) {
        setData(data.withEntry(habitId, date, transform(data.entry(habitId, date))))
    }

    val historyHabit = data.habits.firstOrNull { it.id == historyHabitId }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = if (historyHabitId != null) tab == AppTab.HABITS else currentTab == tab,
                        onClick = {
                            historyHabitId = null
                            currentTabName = tab.name
                            weekSelection = null
                        },
                        icon = { Text(tab.emoji, fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface) },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            when {
                historyHabit != null -> HistoryScreen(
                    habit = historyHabit,
                    data = data,
                    month = YearMonth.parse(historyMonthIso.take(7)),
                    selectedDate = LocalDate.parse(historyDateIso),
                    onBack = { historyHabitId = null },
                    onMonth = {
                        historyMonthIso = it.atDay(1).toString()
                        historyDateIso = if (today.year == it.year && today.month == it.month) today.toString() else it.atDay(1).toString()
                    },
                    onSelectDate = { historyDateIso = it.toString() },
                    onMark = { date, mark -> updateEntry(historyHabit.id, date) { it.copy(mark = mark) } },
                    onColor = { dates -> colorTarget = ColorTarget(historyHabit.id, dates) },
                    onComment = { date -> commentTarget = historyHabit.id to date },
                )

                currentTab == AppTab.TODAY -> TodayScreen(
                    habits = data.habits,
                    data = data,
                    today = today,
                    onMark = { habit, mark -> updateEntry(habit.id, today) { it.copy(mark = mark) } },
                    onColor = { habit -> colorTarget = ColorTarget(habit.id, listOf(today)) },
                    onComment = { habit -> commentTarget = habit.id to today },
                )

                currentTab == AppTab.WEEK -> WeekScreen(
                    habits = data.habits,
                    data = data,
                    weekStart = weekStart,
                    today = today,
                    selection = weekSelection,
                    onWeek = {
                        weekStartIso = it.toString()
                        weekSelection = null
                    },
                    onSelect = { habit, date ->
                        weekSelection = when {
                            weekSelection?.habitId != habit.id -> SelectedWeekDays(habit.id, setOf(date), date)
                            date in weekSelection!!.dates -> {
                                val remaining = weekSelection!!.dates - date
                                if (remaining.isEmpty()) null else SelectedWeekDays(habit.id, remaining, remaining.last())
                            }
                            else -> SelectedWeekDays(habit.id, weekSelection!!.dates + date, date)
                        }
                    },
                    onMark = { habit, date, mark -> updateEntry(habit.id, date) { it.copy(mark = mark) } },
                    onColor = { habit, dates -> colorTarget = ColorTarget(habit.id, dates) },
                    onComment = { habit, date -> commentTarget = habit.id to date },
                )

                else -> HabitsScreen(
                    habits = data.habits,
                    onHistory = { habit ->
                        historyHabitId = habit.id
                        historyMonthIso = YearMonth.from(today).atDay(1).toString()
                        historyDateIso = today.toString()
                    },
                    onSettings = { settingsHabitId = it.id },
                    onAdd = { showAddHabit = true },
                    onExport = { exportLauncher.launch("HabitBuilder-${LocalDate.now()}.xlsx") },
                )
            }
        }
    }

    colorTarget?.let { target ->
        val habit = data.habits.firstOrNull { it.id == target.habitId }
        if (habit != null) DayColorDialog(
            habit = habit,
            dates = target.dates,
            currentColor = data.entry(habit.id, target.dates.first()).color,
            onDismiss = { colorTarget = null },
            onApply = { color ->
                target.dates.forEach { date ->
                    updateEntry(habit.id, date) { it.copy(color = color) }
                }
                colorTarget = null
            },
        )
    }

    commentTarget?.let { (habitId, date) ->
        val habit = data.habits.firstOrNull { it.id == habitId }
        if (habit != null) CommentDialog(
            habit = habit,
            date = date,
            initialComment = data.entry(habit.id, date).comment,
            onDismiss = { commentTarget = null },
            onSave = { comment ->
                updateEntry(habit.id, date) { it.copy(comment = comment.trim()) }
                commentTarget = null
            },
        )
    }

    settingsHabitId?.let { id ->
        data.habits.firstOrNull { it.id == id }?.let { habit ->
            EditHabitDialog(
                habit = habit,
                onDismiss = { settingsHabitId = null },
                onSave = { updated ->
                    setData(data.withUpdatedHabit(updated))
                    settingsHabitId = null
                },
                onDelete = {
                    setData(data.withoutHabit(habit.id))
                    settingsHabitId = null
                    if (historyHabitId == habit.id) historyHabitId = null
                    if (weekSelection?.habitId == habit.id) weekSelection = null
                },
            )
        }
    }

    if (showAddHabit) AddHabitDialog(
        onDismiss = { showAddHabit = false },
        onCreate = { habit ->
            setData(data.copy(habits = data.habits + habit))
            showAddHabit = false
        },
    )
}

@Composable
private fun TodayScreen(
    habits: List<Habit>,
    data: TrackerData,
    today: LocalDate,
    onMark: (Habit, MarkValue) -> Unit,
    onColor: (Habit) -> Unit,
    onComment: (Habit) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp),
    ) {
        ScreenHeader("Today", today.format(fullDateFormatter()))
        Spacer(Modifier.height(6.dp))
        if (habits.isEmpty()) EmptyHabitsMessage()
        habits.forEach { habit ->
            val entry = data.entry(habit.id, today)
            HabitDayCard(
                habit = habit,
                entry = entry,
                date = today,
                onMark = { onMark(habit, it) },
                onColor = { onColor(habit) },
                onComment = { onComment(habit) },
            )
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HabitsScreen(
    habits: List<Habit>,
    onHistory: (Habit) -> Unit,
    onSettings: (Habit) -> Unit,
    onAdd: () -> Unit,
    onExport: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        ScreenHeader("Your habits", "Habits and their history")
        if (habits.isEmpty()) EmptyHabitsMessage()
        habits.forEach { habit ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(habit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text(habit.type.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { onHistory(habit) }) { Text("History") }
                    IconButton(
                        onClick = { onSettings(habit) },
                        modifier = Modifier.semantics { contentDescription = "Edit ${habit.name}" },
                    ) { Text("···", fontSize = 19.sp) }
                }
            }
        }
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("+ Add habit") }
        OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Export to Excel") }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyHabitsMessage() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text("Tap + Add habit on the Habits tab to start tracking.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HabitDayCard(
    habit: Habit,
    entry: DayEntry,
    date: LocalDate,
    onMark: (MarkValue) -> Unit,
    onColor: () -> Unit,
    onComment: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = HabitColors.dayTint(entry.color, isSystemInDarkTheme())),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(habit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(habit.type.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DayActionButton("🖍️", "Change day color for ${habit.name}", onClick = onColor)
                DayActionButton("💬", if (entry.comment.isBlank()) "Add comment for ${habit.name}" else "Edit comment for ${habit.name}", onClick = onComment)
            }
            Spacer(Modifier.height(8.dp))
            MarkControls(habit, entry.mark, enabled = true, onMark = onMark)
            if (entry.comment.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(entry.comment, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            if (habit.type == MarkType.COUNTER) {
                Spacer(Modifier.height(4.dp))
                Text("Daily threshold: ${habit.threshold}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DayActionButton(emoji: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp).semantics { contentDescription = description }) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 0.76f else 0.35f)), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 18.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkControls(habit: Habit, mark: MarkValue, enabled: Boolean, onMark: (MarkValue) -> Unit) {
    when (habit.type) {
        MarkType.YES_NO -> Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = mark.yes, onCheckedChange = { onMark(mark.copy(yes = it)) }, enabled = enabled)
            Text(if (enabled) if (mark.yes) "Yes" else "No" else "Future day")
        }

        MarkType.COUNTER -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onMark(mark.copy(count = (mark.count - 1).coerceAtLeast(0))) }, enabled = enabled && mark.count > 0, modifier = Modifier.defaultMinSize(minWidth = 48.dp)) { Text("−", fontSize = 22.sp) }
            Text(mark.count.toString(), modifier = Modifier.width(28.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = { onMark(mark.copy(count = (mark.count + 1).coerceAtMost(99))) }, enabled = enabled && mark.count < 99, modifier = Modifier.defaultMinSize(minWidth = 48.dp)) { Text("+", fontSize = 22.sp) }
        }

        MarkType.SINGLE_CHOICE -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            habit.choices.forEach { choice ->
                ChoiceButton(choice, selected = mark.single == choice, enabled = enabled) {
                    onMark(mark.copy(single = if (mark.single == choice) null else choice))
                }
            }
        }

        MarkType.MULTIPLE_CHOICE -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            habit.choices.forEach { choice ->
                ChoiceButton(choice, selected = choice in mark.multiple, enabled = enabled) {
                    val selected = if (choice in mark.multiple) mark.multiple - choice else mark.multiple + choice
                    onMark(mark.copy(multiple = selected))
                }
            }
        }
    }
}

@Composable
private fun ChoiceButton(choice: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled) { Text(choice) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled) { Text(choice) }
    }
}

@Composable
private fun WeekScreen(
    habits: List<Habit>,
    data: TrackerData,
    weekStart: LocalDate,
    today: LocalDate,
    selection: SelectedWeekDays?,
    onWeek: (LocalDate) -> Unit,
    onSelect: (Habit, LocalDate) -> Unit,
    onMark: (Habit, LocalDate, MarkValue) -> Unit,
    onColor: (Habit, List<LocalDate>) -> Unit,
    onComment: (Habit, LocalDate) -> Unit,
) {
    val weekDates = (0..6).map { weekStart.plusDays(it.toLong()) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        ScreenHeader("Your week", "Review and annotate each day")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { onWeek(weekStart.minusWeeks(1)) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Previous week" }) { Text("‹", fontSize = 28.sp) }
            Text("${weekStart.format(shortDateFormatter())} – ${weekDates.last().format(shortDateFormatter())}", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onWeek(weekStart.plusWeeks(1)) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Next week" }) { Text("›", fontSize = 28.sp) }
        }
        Spacer(Modifier.height(8.dp))
        if (habits.isEmpty()) EmptyHabitsMessage()
        habits.forEach { habit ->
            val selected = selection?.takeIf { it.habitId == habit.id }
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(habit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text(habit.type.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    WeekCells(
                        dates = weekDates,
                        habit = habit,
                        data = data,
                        today = today,
                        selectedDates = selected?.dates.orEmpty(),
                        onSelect = { onSelect(habit, it) },
                    )
                    if (selected == null) {
                        Text("Tap a day to configure it", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        HorizontalDivider(Modifier.padding(top = 14.dp, bottom = 10.dp), color = MaterialTheme.colorScheme.outline)
                        val activeDate = selected.activeDate
                        val entry = data.entry(habit.id, activeDate)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (selected.dates.size == 1) activeDate.format(fullDateFormatter()) else "${selected.dates.size} days selected", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                if (selected.dates.size == 1) Text(habit.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DayActionButton("🖍️", "Change color for ${selected.dates.size} selected day(s)") { onColor(habit, selected.dates.sorted()) }
                            DayActionButton(
                                "💬",
                                if (selected.dates.size == 1) "Edit comment for ${habit.name}" else "Select one day to edit a comment",
                                enabled = selected.dates.size == 1,
                            ) {
                                if (selected.dates.size == 1) onComment(habit, activeDate)
                            }
                        }
                        if (selected.dates.size == 1) {
                            Spacer(Modifier.height(6.dp))
                            MarkControls(habit, entry.mark, enabled = !activeDate.isAfter(today)) { onMark(habit, activeDate, it) }
                            if (habit.type == MarkType.COUNTER) {
                                Spacer(Modifier.height(3.dp))
                                Text("Daily threshold: ${habit.threshold}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (entry.comment.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(entry.comment, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        Text("• Has comment     · No mark     Outline = selected", Modifier.padding(bottom = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeekCells(
    dates: List<LocalDate>,
    habit: Habit,
    data: TrackerData,
    today: LocalDate,
    selectedDates: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        dates.forEach { date ->
            Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1), Modifier.weight(1f), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        dates.forEach { date ->
            val entry = data.entry(habit.id, date)
            val selected = date in selectedDates
            val future = date.isAfter(today)
            val symbol = if (future) "" else entry.mark.summary(habit)
            val desc = buildString {
                append("${habit.name}, ${date.format(fullDateFormatter())}, ")
                append(if (future) "future day" else symbol)
                append(", ${entry.color.label}")
                if (entry.comment.isNotBlank()) append(", has comment")
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(HabitColors.dayTint(entry.color, isSystemInDarkTheme()))
                        .then(
                            if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(11.dp))
                            else Modifier,
                        )
                        .clickable(onClickLabel = "Select ${date.format(fullDateFormatter())}") { onSelect(date) }
                        .semantics { contentDescription = desc },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = buildString {
                            append(symbol.ifBlank { "·" })
                            if (entry.comment.isNotBlank() && !future) append("  •")
                        },
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    habit: Habit,
    data: TrackerData,
    month: YearMonth,
    selectedDate: LocalDate,
    onBack: () -> Unit,
    onMonth: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onMark: (LocalDate, MarkValue) -> Unit,
    onColor: (List<LocalDate>) -> Unit,
    onComment: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val days = month.lengthOfMonth()
    val startPadding = month.atDay(1).dayOfWeek.value - 1
    val gridDates: List<LocalDate?> = buildList {
        repeat(startPadding) { add(null) }
        for (day in 1..days) add(month.atDay(day))
        while (size % 7 != 0) add(null)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹  All habits") }
        }
        Text(habit.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
        Text(habit.type.label, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Previous month" }) { Text("‹", fontSize = 28.sp) }
            Text(month.atDay(1).format(monthFormatter()), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onMonth(month.plusMonths(1)) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Next month" }) { Text("›", fontSize = 28.sp) }
        }
        HistoryStatistics(habit, data, month, today)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                Text(day, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))
        gridDates.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f).height(58.dp))
                    } else {
                        val entry = data.entry(habit.id, date)
                        val future = date.isAfter(today)
                        val selected = date == selectedDate
                        val mark = if (future) "" else entry.mark.summary(habit)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.fillMaxWidth().heightIn(min = 58.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(HabitColors.dayTint(entry.color, isSystemInDarkTheme()))
                                    .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(11.dp)) else Modifier)
                                    .clickable { onSelectDate(date) }
                                    .semantics {
                                        contentDescription = "${habit.name}, ${date.format(fullDateFormatter())}, ${if (future) "future" else mark}"
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(Modifier.padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
                                    Text(mark.ifBlank { "·" }, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    if (entry.comment.isNotBlank()) Text("•", fontSize = 11.sp, lineHeight = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Text("• Has comment     Tap a date to view or edit", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val entry = data.entry(habit.id, selectedDate)
        Spacer(Modifier.height(8.dp))
        Card(
            Modifier.fillMaxWidth().padding(bottom = 18.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(selectedDate.format(fullDateFormatter()), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Text(if (selectedDate.isAfter(today)) "Future day" else entry.mark.summary(habit).ifBlank { "Not marked" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DayActionButton("🖍️", "Change day color for ${habit.name}") { onColor(listOf(selectedDate)) }
                    DayActionButton("💬", if (entry.comment.isBlank()) "Add comment for ${habit.name}" else "Edit comment for ${habit.name}") { onComment(selectedDate) }
                }
                if (!selectedDate.isAfter(today)) {
                    Spacer(Modifier.height(6.dp))
                    MarkControls(habit, entry.mark, enabled = true) { onMark(selectedDate, it) }
                }
                if (entry.comment.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(entry.comment, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun HistoryStatistics(habit: Habit, data: TrackerData, month: YearMonth, today: LocalDate) {
    val monthStats = habit.statistics(data, month.atDay(1), month.atEndOfMonth(), today)
    val yearStart = month.atDay(1).withDayOfYear(1)
    val yearEnd = yearStart.withDayOfYear(yearStart.lengthOfYear())
    val yearStats = habit.statistics(data, yearStart, yearEnd, today)
    Card(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text("Statistics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (habit.type == MarkType.MULTIPLE_CHOICE || habit.type == MarkType.SINGLE_CHOICE) "Option" else "Measure", Modifier.weight(1.3f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(0.65f), horizontalAlignment = Alignment.End) {
                    Text("Month", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(month.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(0.55f), horizontalAlignment = Alignment.End) {
                    Text("Year", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(month.year.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            monthStats.forEachIndexed { index, stat ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stat.first, Modifier.weight(1.3f), style = MaterialTheme.typography.bodyMedium)
                    Text(monthStats[index].second.toString(), Modifier.weight(0.65f), textAlign = TextAlign.End, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(yearStats[index].second.toString(), Modifier.weight(0.55f), textAlign = TextAlign.End, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                }
            }
            val rule = when (habit.type) {
                MarkType.YES_NO -> "Each Yes adds 1."
                MarkType.COUNTER -> "Daily count ≥ ${habit.threshold} adds 1."
                MarkType.MULTIPLE_CHOICE -> "Each selected option adds 1 for the day."
                MarkType.SINGLE_CHOICE -> "Each chosen option adds 1 for the day."
            }
            Spacer(Modifier.height(4.dp))
            Text(rule, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Habit.statistics(
    data: TrackerData,
    start: LocalDate,
    end: LocalDate,
    today: LocalDate,
): List<Pair<String, Int>> {
    val last = minOf(end, today)
    if (last.isBefore(start)) {
        return when (type) {
            MarkType.YES_NO -> listOf("Yes" to 0)
            MarkType.COUNTER -> listOf("Threshold met" to 0)
            MarkType.SINGLE_CHOICE, MarkType.MULTIPLE_CHOICE -> choices.map { it to 0 }
        }
    }
    val marks = generateSequence(start) { previous -> previous.plusDays(1).takeIf { it <= last } }
        .map { data.entry(id, it).mark }
        .toList()
    return when (type) {
        MarkType.YES_NO -> listOf("Yes" to marks.count { it.yes })
        MarkType.COUNTER -> listOf("Threshold met" to marks.count { it.count >= threshold })
        MarkType.SINGLE_CHOICE -> choices.map { choice -> choice to marks.count { it.single == choice } }
        MarkType.MULTIPLE_CHOICE -> choices.map { choice -> choice to marks.count { choice in it.multiple } }
    }
}

private fun MarkValue.summary(habit: Habit): String = when (habit.type) {
    MarkType.YES_NO -> if (yes) "✓" else "·"
    MarkType.COUNTER -> count.takeIf { it > 0 }?.toString() ?: "·"
    MarkType.SINGLE_CHOICE -> single ?: "·"
    MarkType.MULTIPLE_CHOICE -> multiple.mapNotNull { choice -> choice.firstOrNull()?.uppercaseChar() }
        .joinToString("+").ifBlank { "·" }
}

@Composable
private fun DayColorDialog(
    habit: Habit,
    dates: List<LocalDate>,
    currentColor: DayColor,
    onDismiss: () -> Unit,
    onApply: (DayColor) -> Unit,
) {
    var selectedColor by remember(habit.id, dates) { mutableStateOf(currentColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Day color") },
        text = {
            Column {
                Text(
                    if (dates.size == 1) habit.name + " · " + dates.first().format(fullDateFormatter())
                    else habit.name + " · " + dates.size + " selected days",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayColor.entries.forEach { color ->
                        Column(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedColor = color }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(36.dp)
                                    .clip(CircleShape)
                                    .background(HabitColors.dayTint(color, isSystemInDarkTheme()))
                                    .then(
                                        if (selectedColor == color) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (selectedColor == color) "✓" else if (color == DayColor.NONE) "×" else "", fontSize = 17.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(color.label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onApply(selectedColor) }) { Text("Apply color") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CommentDialog(
    habit: Habit,
    date: LocalDate,
    initialComment: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var comment by remember(habit.id, date) { mutableStateOf(initialComment) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit comment") },
        text = {
            Column {
                Text(habit.name + " · " + date.format(fullDateFormatter()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(200) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add a note about this day…") },
                    minLines = 3,
                    maxLines = 5,
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(comment) }) { Text("Save comment") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditHabitDialog(
    habit: Habit,
    onDismiss: () -> Unit,
    onSave: (Habit) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(habit.id) { mutableStateOf(habit.name) }
    var type by remember(habit.id) { mutableStateOf(habit.type) }
    var choicesText by remember(habit.id) { mutableStateOf(habit.choices.joinToString(", ")) }
    var thresholdText by remember(habit.id) { mutableStateOf(habit.threshold.toString()) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember(habit.id) { mutableStateOf(false) }
    var confirmSave by remember(habit.id) { mutableStateOf<Habit?>(null) }
    val threshold = thresholdText.toIntOrNull()?.takeIf { it in 1..99 }
    val choices = choicesText.split(',').map(String::trim).filter(String::isNotBlank).distinct()
    val choiceType = type == MarkType.SINGLE_CHOICE || type == MarkType.MULTIPLE_CHOICE
    val valid = name.isNotBlank() &&
        (!choiceType || choices.size in 2..8 && choices.all { it.length <= 30 }) &&
        (type != MarkType.COUNTER || threshold != null)

    when {
        confirmDelete -> AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete habit?") },
            text = { Text("${habit.name} and all its marks, colors, and comments will be permanently removed.") },
            confirmButton = {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete habit") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep habit") } },
        )
        confirmSave != null -> AlertDialog(
            onDismissRequest = { confirmSave = null },
            title = { Text("Change habit setup?") },
            text = { Text("Changing the mark type or removing choices will clear affected past marks. Colors and comments will stay.") },
            confirmButton = { Button(onClick = { confirmSave?.let(onSave) }) { Text("Save changes") } },
            dismissButton = { TextButton(onClick = { confirmSave = null }) { Text("Cancel") } },
        )
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit habit") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(50) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    Box {
                        OutlinedButton(onClick = { typeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Mark type: " + type.label, Modifier.weight(1f), textAlign = TextAlign.Start)
                            Text("▾")
                        }
                        DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                            MarkType.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = { type = option; typeMenuExpanded = false },
                                )
                            }
                        }
                    }
                    if (choiceType) {
                        OutlinedTextField(
                            value = choicesText,
                            onValueChange = { choicesText = it.take(250) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Choices, separated by commas") },
                            supportingText = { Text("Use 2–8 choices, up to 30 characters each.") },
                        )
                    }
                    if (type == MarkType.COUNTER) {
                        OutlinedTextField(
                            value = thresholdText,
                            onValueChange = { thresholdText = it.filter(Char::isDigit).take(2) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Daily threshold") },
                            supportingText = { Text("Changing this recalculates month and year statistics.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = threshold == null,
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            val updated = habit.copy(
                                name = name.trim(),
                                type = type,
                                choices = if (choiceType) choices else emptyList(),
                                threshold = if (type == MarkType.COUNTER) threshold ?: 1 else 1,
                            )
                            if (habit.type != updated.type || habit.choices.any { it !in updated.choices }) {
                                confirmSave = updated
                            } else onSave(updated)
                        },
                        enabled = valid,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Text("Save habit") }
                    Box(
                        modifier = Modifier.padding(start = 4.dp)
                            .size(width = 44.dp, height = 40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                            .clickable(role = Role.Button) { confirmDelete = true }
                            .semantics { contentDescription = "Delete ${habit.name}" },
                        contentAlignment = Alignment.Center,
                    ) { Text("🗑️", fontSize = 18.sp) }
                }
            },
            dismissButton = {},
        )
    }
}

@Composable
private fun AddHabitDialog(onDismiss: () -> Unit, onCreate: (Habit) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(MarkType.YES_NO) }
    var choicesText by remember { mutableStateOf("") }
    var thresholdText by remember { mutableStateOf("1") }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    val threshold = thresholdText.toIntOrNull()?.takeIf { it in 1..99 }
    val choices = choicesText.split(',').map(String::trim).filter(String::isNotBlank).distinct()
    val choiceType = type == MarkType.SINGLE_CHOICE || type == MarkType.MULTIPLE_CHOICE
    val valid = name.isNotBlank() &&
        (!choiceType || choices.size in 2..8 && choices.all { it.length <= 30 }) &&
        (type != MarkType.COUNTER || threshold != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New habit") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(50) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Drink water") },
                    singleLine = true,
                )
                Box {
                    OutlinedButton(onClick = { typeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Mark type: " + type.label, Modifier.weight(1f), textAlign = TextAlign.Start)
                        Text("▾")
                    }
                    DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                        MarkType.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = { type = option; typeMenuExpanded = false },
                            )
                        }
                    }
                }
                if (choiceType) {
                    OutlinedTextField(
                        value = choicesText,
                        onValueChange = { choicesText = it.take(250) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Choices, separated by commas") },
                        placeholder = { Text("e.g. Reading, Video") },
                    )
                }
                if (type == MarkType.COUNTER) {
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Daily threshold") },
                        supportingText = { Text("Daily count at or above this adds 1 to statistics.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (valid) onCreate(
                        Habit(
                            name = name.trim(),
                            type = type,
                            choices = if (choiceType) choices else emptyList(),
                            threshold = if (type == MarkType.COUNTER) threshold ?: 1 else 1,
                        ),
                    )
                },
                enabled = valid,
            ) { Text("Create habit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun fullDateFormatter() = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
private fun shortDateFormatter() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private fun monthFormatter() = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
