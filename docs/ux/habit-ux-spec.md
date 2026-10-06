# Habit Builder — UX requirements

Status: collected requirements and current UX decisions.  
Updated: 6 October 2026.  
Reference: [Charcoal interactive mockup](habit-mockup.html).

## 1. Purpose and scope

Build an Android app for tracking habits and other recurring occurrences, replacing the user's current spreadsheet workflow. A mark records that something happened; habits do not all use a simple completion checkbox.

The agreed interface has three main tabs: **Today**, **Week**, and **Habits**. **History** is a detail screen for one habit, accessed from the Habits tab.

This document distinguishes confirmed requirements from defaults introduced in the mockup. Prototype limits and unresolved decisions are listed separately so they do not become accidental product requirements.

## 2. Habit mark types

A habit has a name and one configured mark type.

| Type | Daily value and control | Example |
| --- | --- | --- |
| Yes / no | Checkbox or equivalent toggle | Sport occurred |
| Counter | Integer count, with decrease and increase controls | Number of reading sessions |
| Single choice | One selected option from the habit's configured choices | S or F |
| Multiple choice | Independently selected options from the habit's configured choices | English, Writing, or both |

Counter habits also have a configurable **daily threshold** used for statistics. Single-choice and multiple-choice habits have a predefined set of options configured for that habit.

The example labels in the spreadsheet, including `s/f` and `:1`, are occurrence markers. They do not define universal success/failure semantics or fixed behavior for every habit.

## 3. Shared daily data

Each habit and calendar date has one shared daily entry. Its fields are:

| Field | Meaning |
| --- | --- |
| Mark value | Boolean, count, one choice, or a set of choices, according to the habit type |
| Day color | Optional manually assigned color for this habit on this date |
| Comment | Optional text for this habit on this date |

The day color belongs to a **specific habit and date**, rather than to the date across all habits. Changing Sport's Tuesday color must not change Video / book's Tuesday color.

Today and Week must display the same underlying entries. The current design also uses those entries in History. Saved changes must be reflected wherever the entry is displayed.

Color and comment edits are independent of the mark. Annotating an unmarked day does not mark the habit as done or contribute to statistics.

## 4. Today tab

- Show the current date and a list of habit cards.
- Each card shows the habit name, its mark type, and the appropriate mark control.
- Display the habit's assigned day color on its card.
- Display an existing comment as read-only text below the mark controls.
- Place compact annotation actions in the **top-right corner** of each card.
- Use **🖍️** for the color action and an emoji icon for the comment action; the mockup uses **💬**.
- Open dialogs to assign color or edit the comment. Do not display an inline color palette or inline comment editor on the card.

## 5. Week tab — “Your week”

### 5.1 Layout

- Show seven dated day cells for each habit.
- Enclose each habit's week in its own light/white rounded panel in the Charcoal light appearance.
- Keep the habit heading, day labels, day cells, and selected-day configuration inside that panel.
- Place the selected-day configuration **after the day cells**.
- For a single selected day, show its date, mark controls, annotation actions, and existing comment text below the cells.
- Place the color and comment icons at the top-right of the selected-day configuration area.
- Show the assigned color on each day cell and visibly distinguish selected cells.

### 5.2 Selection

- Do not show a **“Select cells”** button or require a separate selection mode.
- Allow users to select multiple days directly.
- Allow future days to be selected too.
- Allow selection in **only one habit at a time** on Your week.
- Selecting a day in another habit clears the previous habit's selected cells.

The current interaction uses a tap to select a cell and another tap on that cell to deselect it.

### 5.3 Configuration by selection size

| Selection | Available configuration |
| --- | --- |
| No days | No selected-day configuration |
| One day | Day mark controls, color action, comment action, and existing comment text |
| Multiple days in one habit | Selected-day count and color action; comment action disabled; mark controls and comment text hidden |

When multiple days are selected, **only the color action is active**. The user can assign one color to all selected cells through the color dialog. Returning to one selected day restores its individual configuration and comment.

## 6. Annotation dialogs

### 6.1 Day color

- Assign color in a dialog opened by **🖍️**.
- Present a predefined set of tappable color choices inside the dialog.
- Do not use a dropdown list or show all color choices permanently in Today or Week.
- Make the selected choice visibly identifiable.
- For one day, identify the habit and date being edited.
- For multiple days, identify the number of selected days being edited.
- Apply the chosen color only after the user confirms.
- Cancelling or closing the dialog discards the pending choice.
- Bulk application changes only the selected days' colors; their marks and comments stay intact.

The current palette is **None**, **Mint**, **Amber**, **Rose**, and **Blue**. None clears an assigned color. These are the mockup's palette defaults; the exact final palette is not separately specified by the user.

### 6.2 Comment

- Create and edit comments in a dialog opened by the comment icon.
- Identify the habit and date and populate the editor with any existing comment.
- Save only when the user confirms; cancelling or closing discards pending edits.
- After saving, display the comment in Today and under the selected day's cells in Week.
- Hide displayed comment text during multiple-day selection and disable comment editing in that state.

The current design allows an empty saved comment to remove it and uses a small dot on calendar cells to indicate an existing comment.

## 7. Habits tab and habit configuration

- List the user's habits with their names and mark types.
- Provide a **History** action for each habit, replacing the earlier **Log** action.
- History opens historical data for the selected habit, rather than a new logging flow.
- The **⋯** action on each habit row opens an **Edit habit** dialog.
- Edit habit supports changing the name, mark type, choice options where applicable, and the counter's daily threshold.
- Changing the mark type clears existing marks for that habit; removing a choice clears marks that used it. Confirm these changes before saving. Colors and comments remain attached to their dates.
- Edit habit includes a **Delete habit** action with confirmation. Deleting removes the habit and all of its dated marks, colors, and comments. Deleting the last habit leaves the list empty after an app restart.
- The Edit habit action row runs left to right: **Cancel**, dark **Save habit**, then a compact red, icon-only delete action. The delete action matches Save habit's height and rounded shape. The separate deletion dialog has a red confirmation button.
- Provide **Export to Excel** below Add habit. It opens Android's save dialog with a suggested `.xlsx` filename.
- The exported workbook has a Monday–Sunday **Week view** for weeks with saved entries, a **Daily entries** sheet with all saved dates and full comments, and a **Habits** sheet with current configuration. Colored days use colored cell fills; a dot in Week view signals a comment. Exporting does not modify app data.

The current mockup also provides **Add habit**, with a name, mark type, choices when applicable, and a threshold for counter habits. The threshold defaults to 1. Both creation and editing support cancellation.

## 8. Single-habit History

- Show a month calendar for the selected habit.
- Show daily marks, assigned colors, and comment indicators using the same data as Today and Week.
- Support reviewing previous months.
- Allow selection of a date to display its details.
- Provide a route back to the Habits list.
- Display statistics for both the **month** and the **year**.
- Identify the periods associated with the displayed totals.
- For multiple-choice habits, show separate statistics for every configured option.

The current layout places the statistics above the calendar, with Month and Year columns. It uses the displayed calendar month and that month's calendar year. Counter History also provides an **Edit threshold** shortcut to the Edit habit dialog.

## 9. Statistics rules

Counts are derived from the recorded daily values, rather than maintained as independent totals that could diverge after an edit.

| Habit type | Required contribution from one day |
| --- | --- |
| Yes / no | Add 1 when the value is Yes; otherwise add 0 |
| Counter | Add 1 when the daily count reaches or exceeds the configured threshold; otherwise add 0 |
| Multiple choice | Add 1 to each selected option's own statistic |

A counter contributes **at most 1 per day**, regardless of how far it exceeds its threshold. For a threshold of 3, counts of 0 or 2 contribute 0; counts of 3, 4, or 10 each contribute 1.

For a multiple-choice habit with English and Writing, selecting both on one day contributes 1 to English and 1 to Writing. The option totals can therefore sum to more than the number of days in the period.

Changing or clearing a mark must recalculate the affected month and year counts. Colors and comments must not affect the counts.

Current defaults awaiting separate product confirmation:

- Year means the calendar year containing the displayed month, rather than a rolling 12-month period.
- Only dates up to today contribute; future-day annotations do not count as occurrences.
- Changing a counter threshold recalculates all historical statistics using the new threshold; previous thresholds are not retained by period.
- Single-choice statistics count the days on which each option was selected. The user explicitly requested per-option statistics for multiple choice; single-choice statistics use this consistent default in the mockup.

## 10. Chosen visual design

- Use the **Charcoal** color scheme.
- Keep one HTML mockup as the design reference, with no alternative scheme picker or older HTML mockup variants.
- Use neutral app surfaces and controls so manually assigned day colors remain visually prominent.
- Keep annotation buttons compact and icon-based, with accessible action names.
- Preserve the agreed card and week-panel structure.
- Use the selected **Marked day** launcher icon: an ivory day tile with a charcoal check and a blue annotation dot on a charcoal background.

The current light-appearance reference tokens are:

| Role | Color |
| --- | --- |
| App background | `#F6F6F7` |
| Cards and dialogs | `#FFFFFF` |
| Main text | `#25262A` |
| Secondary text | `#60646C` |
| Soft control background | `#E9EAED` |
| Primary action | `#3C414B` |

The mockup also adapts these colors for a dark host appearance. The Android app's theme-setting behavior has not yet been specified.

## 11. Other current defaults and prototype limits

These describe the reference implementation and are not confirmed production constraints:

- The week starts on Monday, matching the spreadsheet.
- Unmarked values are false for yes/no, 0 for counters, no selected option for single choice, and an empty set for multiple choice.
- Future days support colors and comments; their occurrence-mark controls are disabled.
- Calendar selections can be toggled in History; multiple selected dates use the color-only configuration.
- The prototype caps habits at 8, counters and thresholds at 99, choices at 8, habit names at 50 characters, choice labels at 30 characters, and comments at 200 characters. It requires 2–8 choices for choice-based habits. Final limits remain to be decided.
- The displayed habits, entries, and dates are sample data. The fixed demo date is 6 October 2026; it must not be treated as a production rule.
- A fresh Android installation starts with no habits or daily entries. The sample habits belong only to the HTML mockup.
- Remembered preview state supports UX evaluation. It does not specify Android storage, synchronization, or backup behavior.

## 12. Acceptance scenarios

1. **Shared entry:** Change a habit's current-day mark, color, or comment in Today. Open Week and History; the same habit/date reflects the saved changes.
2. **Per-habit color:** Color Sport's Tuesday. Another habit's Tuesday is unaffected.
3. **Dialog cancellation:** Choose a different color or edit a comment, then cancel. The stored entry is unchanged.
4. **Single selection:** Select one week cell. Its configuration appears below the cells inside the same habit panel, including its comment.
5. **Multiple selection:** Select two days in one habit. Only color is active; mark controls and comment text disappear and comment editing is disabled.
6. **Bulk color:** Apply a color to selected days. All selected cells receive that color without changes to marks or comments.
7. **Habit switch:** With Sport days selected, select a Video / book day. Sport's selection clears; selections never span habits in Your week.
8. **Future selection:** Select a future day and assign a color. The selection and color assignment work.
9. **History entry point:** Tap History on a habit row. A month calendar and month/year statistics open for that habit.
10. **Yes count:** A month with five Yes values shows 5, independent of colors and comments.
11. **Threshold boundary:** With a counter threshold of 3, values 2, 3, and 7 on three separate days produce a statistic of 2, rather than 12 or multiple increments for the day with 7.
12. **Threshold settings:** Save a new counter threshold and reopen configuration. The value is retained and the statistics follow the documented recalculation rule. Cancel an unsaved change and the previous threshold remains.
13. **Per-option statistics:** Select two options on one date. Each option's month and year total increases by 1; deselecting one reduces only that option's totals.
14. **Month navigation:** Navigate to a previous month. Month statistics change to that month, and the year column identifies the calendar year used.
15. **Final reference:** The UX directory contains one HTML mockup using Charcoal, with no app color-scheme picker.
16. **Edit habit:** Tap ⋯ on a habit, change its name or threshold, save, and see the new value across Today, Week, Habits, and History.
17. **Delete habit:** Tap ⋯, choose Delete habit, and confirm. The habit and all its dated data disappear; deleting the last habit remains effective after restarting the app.
18. **Empty first run:** A fresh installation shows no predefined habits and offers the Add habit action.
19. **Excel export:** Save an `.xlsx` file from Habits. Verify the current habits, their thresholds and choices, saved marks, day colors, and complete comments are present; colored future dates are included. Canceling Android's save dialog creates no export.

## 13. Decisions still open

- Distinguishing an explicitly recorded No from an unrecorded checkbox day.
- Final counter range, threshold default, and text/choice limits.
- Confirming the defaults for future-day marks, calendar-year totals, threshold changes, and single-choice statistics.
- Habit ordering and archiving.
- Production date/timezone handling and Android light/dark appearance settings.
- Storage, backup, account/synchronization behavior, reminders, and spreadsheet import. No requirements for these were established during this UX discussion.
