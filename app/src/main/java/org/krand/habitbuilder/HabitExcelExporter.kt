package org.krand.habitbuilder

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes a standard XLSX workbook directly to a document chosen by the user. */
object HabitExcelExporter {
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private const val MAX_ROWS = 1_048_576
    private const val HEADER_STYLE = 1
    private const val DATE_STYLE = 2
    private const val WRAPPED_STYLE = 7

    private data class SavedDay(val date: LocalDate, val habit: Habit, val entry: DayEntry)

    fun write(data: TrackerData, output: OutputStream) {
        val habitsById = data.habits.associateBy(Habit::id)
        val habitOrder = data.habits.mapIndexed { index, habit -> habit.id to index }.toMap()
        val days = data.entries.mapNotNull { (key, entry) ->
            val split = key.lastIndexOf('|')
            if (split < 0) return@mapNotNull null
            val habit = habitsById[key.substring(0, split)] ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(key.substring(split + 1)) }.getOrNull()
                ?: return@mapNotNull null
            SavedDay(date, habit, entry)
        }.sortedWith(compareBy<SavedDay> { it.date }.thenBy { habitOrder[it.habit.id] ?: Int.MAX_VALUE })
        val weeks = days.map { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.distinct()
        require(days.size < MAX_ROWS && data.habits.size < MAX_ROWS && weeks.size.toLong() * data.habits.size < MAX_ROWS) {
            "There are too many rows for one Excel workbook."
        }

        ZipOutputStream(output).use { zip ->
            zip.xml("[Content_Types].xml") { it.write(contentTypes) }
            zip.xml("_rels/.rels") { it.write(rootRelationships) }
            zip.xml("xl/workbook.xml") { it.write(workbook) }
            zip.xml("xl/_rels/workbook.xml.rels") { it.write(workbookRelationships) }
            zip.xml("xl/styles.xml") { it.write(styles) }
            zip.xml("xl/worksheets/sheet1.xml") { writer -> writeWeekSheet(writer, data, weeks) }
            zip.xml("xl/worksheets/sheet2.xml") { writer -> writeDaysSheet(writer, days) }
            zip.xml("xl/worksheets/sheet3.xml") { writer -> writeHabitsSheet(writer, data.habits) }
        }
    }

    private fun writeWeekSheet(writer: Writer, data: TrackerData, weeks: List<LocalDate>) {
        val rows = 1 + weeks.size * data.habits.size
        writer.sheetStart("J", rows, listOf(15, 26, 18, 16, 16, 16, 16, 16, 16, 16))
        writer.row(1) {
            listOf("Week starting", "Habit", "Mark type", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                .forEachIndexed { index, label -> textCell(index, 1, label, HEADER_STYLE) }
        }
        var row = 2
        weeks.forEach { week ->
            data.habits.forEach { habit ->
                writer.row(row) {
                    dateCell(0, row, week)
                    textCell(1, row, habit.name)
                    textCell(2, row, habit.type.label)
                    repeat(7) { offset ->
                        val entry = data.entry(habit.id, week.plusDays(offset.toLong()))
                        val mark = entry.mark.forHabit(habit)
                        val label = if (entry.comment.isBlank()) mark else if (mark.isBlank()) "•" else "$mark •"
                        textCell(offset + 3, row, label, entry.color.style())
                    }
                }
                row++
            }
        }
        writer.sheetEnd("J", rows)
    }

    private fun writeDaysSheet(writer: Writer, days: List<SavedDay>) {
        val rows = 1 + days.size
        writer.sheetStart("G", rows, listOf(15, 26, 18, 24, 16, 52, 38))
        writer.row(1) {
            listOf("Date", "Habit", "Mark type", "Mark", "Day color", "Comment", "Habit ID")
                .forEachIndexed { index, label -> textCell(index, 1, label, HEADER_STYLE) }
        }
        days.forEachIndexed { index, saved ->
            val row = index + 2
            writer.row(row) {
                dateCell(0, row, saved.date)
                textCell(1, row, saved.habit.name)
                textCell(2, row, saved.habit.type.label)
                if (saved.habit.type == MarkType.COUNTER && saved.entry.mark.count > 0) {
                    numberCell(3, row, saved.entry.mark.count.toLong(), saved.entry.color.style())
                } else {
                    textCell(3, row, saved.entry.mark.forHabit(saved.habit), saved.entry.color.style())
                }
                textCell(4, row, saved.entry.color.label.takeUnless { saved.entry.color == DayColor.NONE }.orEmpty(), saved.entry.color.style())
                textCell(5, row, saved.entry.comment, WRAPPED_STYLE)
                textCell(6, row, saved.habit.id)
            }
        }
        writer.sheetEnd("G", rows)
    }

    private fun writeHabitsSheet(writer: Writer, habits: List<Habit>) {
        val rows = 1 + habits.size
        writer.sheetStart("E", rows, listOf(26, 18, 40, 20, 38))
        writer.row(1) {
            listOf("Habit", "Mark type", "Choices", "Daily threshold", "Habit ID")
                .forEachIndexed { index, label -> textCell(index, 1, label, HEADER_STYLE) }
        }
        habits.forEachIndexed { index, habit ->
            val row = index + 2
            writer.row(row) {
                textCell(0, row, habit.name)
                textCell(1, row, habit.type.label)
                textCell(2, row, habit.choices.joinToString(", "))
                if (habit.type == MarkType.COUNTER) numberCell(3, row, habit.threshold.toLong())
                textCell(4, row, habit.id)
            }
        }
        writer.sheetEnd("E", rows)
    }

    private fun MarkValue.forHabit(habit: Habit): String = when (habit.type) {
        MarkType.YES_NO -> if (yes) "Yes" else ""
        MarkType.COUNTER -> if (count > 0) count.toString() else ""
        MarkType.SINGLE_CHOICE -> single.orEmpty()
        MarkType.MULTIPLE_CHOICE -> habit.choices.filter { it in multiple }.joinToString(", ")
    }

    private fun DayColor.style(): Int = when (this) {
        DayColor.NONE -> 0
        DayColor.MINT -> 3
        DayColor.AMBER -> 4
        DayColor.ROSE -> 5
        DayColor.BLUE -> 6
    }

    private inline fun ZipOutputStream.xml(path: String, write: (Writer) -> Unit) {
        putNextEntry(ZipEntry(path))
        val writer = OutputStreamWriter(this, Charsets.UTF_8)
        write(writer)
        writer.flush()
        closeEntry()
    }

    private fun Writer.sheetStart(lastColumn: String, rows: Int, widths: List<Int>) {
        write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><dimension ref="A1:$lastColumn${maxOf(rows, 1)}"/><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight="18"/><cols>""")
        widths.forEachIndexed { index, width ->
            write("""<col min="${index + 1}" max="${index + 1}" width="$width" customWidth="1"/>""")
        }
        write("</cols><sheetData>")
    }

    private fun Writer.sheetEnd(lastColumn: String, rows: Int) {
        write("""</sheetData><autoFilter ref="A1:$lastColumn${maxOf(rows, 1)}"/></worksheet>""")
    }

    private inline fun Writer.row(index: Int, cells: Writer.() -> Unit) {
        write("""<row r="$index">""")
        cells()
        write("</row>")
    }

    private fun Writer.textCell(column: Int, row: Int, value: String, style: Int = 0) {
        if (value.isEmpty() && style == 0) return
        write("""<c r="${columnName(column)}$row" s="$style" t="inlineStr"><is><t xml:space="preserve">""")
        write(escapeXml(value))
        write("</t></is></c>")
    }

    private fun Writer.numberCell(column: Int, row: Int, value: Long, style: Int = 0) {
        write("""<c r="${columnName(column)}$row" s="$style"><v>$value</v></c>""")
    }

    private fun Writer.dateCell(column: Int, row: Int, date: LocalDate) {
        numberCell(column, row, ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), date), DATE_STYLE)
    }

    private fun columnName(index: Int): String = ('A'.code + index).toChar().toString()

    private fun escapeXml(value: String): String = buildString(value.length) {
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            when (codePoint) {
                '&'.code -> append("&amp;")
                '<'.code -> append("&lt;")
                '>'.code -> append("&gt;")
                '"'.code -> append("&quot;")
                '\''.code -> append("&apos;")
                else -> if (codePoint == 9 || codePoint == 10 || codePoint == 13 || codePoint in 32..0xD7FF || codePoint in 0xE000..0xFFFD || codePoint in 0x10000..0x10FFFF) {
                    appendCodePoint(codePoint)
                }
            }
            index += Character.charCount(codePoint)
        }
    }

    private val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""

    private val rootRelationships = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Week view" sheetId="1" r:id="rId1"/><sheet name="Daily entries" sheetId="2" r:id="rId2"/><sheet name="Habits" sheetId="3" r:id="rId3"/></sheets></workbook>"""

    private val workbookRelationships = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/><Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""

    private val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="yyyy-mm-dd"/></numFmts><fonts count="2"><font><sz val="11"/><color rgb="FF25262A"/><name val="Arial"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Arial"/></font></fonts><fills count="7"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF3C414B"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE4F1DE"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFFF0C9"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFBE1E3"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE0EDFF"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="8"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="0" fontId="0" fillId="3" borderId="0" xfId="0" applyFill="1"/><xf numFmtId="0" fontId="0" fillId="4" borderId="0" xfId="0" applyFill="1"/><xf numFmtId="0" fontId="0" fillId="5" borderId="0" xfId="0" applyFill="1"/><xf numFmtId="0" fontId="0" fillId="6" borderId="0" xfId="0" applyFill="1"/><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment wrapText="1" vertical="top"/></xf></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
