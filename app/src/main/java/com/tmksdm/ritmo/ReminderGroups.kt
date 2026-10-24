package com.tmksdm.ritmo

import java.util.Locale

/** Раздел списка: именованная группа или негруппированные напоминания (name == null). */
data class ReminderSection(
    val name: String?,
    val reminders: List<Reminder>,
    val expanded: Boolean
)

/** Готовый к показу список: при отсутствии групп — один плоский раздел, как раньше. */
data class ReminderList(
    val grouped: Boolean,
    val sections: List<ReminderSection>
)

/**
 * Порядок списка и разделов. Вынесен без зависимостей от Android, чтобы проверять
 * сортировку и границы групп отдельно от интерфейса.
 */
object ReminderGroups {

    /**
     * Активные — по ближайшему срабатыванию, затем выключенные, затем завершённые;
     * при равенстве — по названию и id, чтобы порядок был стабильным.
     */
    fun displayOrder(now: Long): Comparator<Reminder> = compareBy(
        { displayGroup(it) },
        { displayTime(it, now) },
        { it.title.lowercase(Locale.getDefault()) },
        { it.id }
    )

    fun ordered(reminders: List<Reminder>, now: Long): List<Reminder> =
        reminders.sortedWith(displayOrder(now))

    /**
     * Собирает разделы. Свёрнутая группа продолжает участвовать в сортировке:
     * её место определяется напоминаниями, а не тем, что они скрыты.
     * Без групп возвращается прежний плоский список.
     */
    fun buildList(
        reminders: List<Reminder>,
        definedGroups: List<String>,
        now: Long,
        isExpanded: (String?) -> Boolean
    ): ReminderList {
        val groupNames = mutableListOf<String>()
        definedGroups.forEach { addName(groupNames, it) }
        reminders.forEach { reminder ->
            val name = reminder.group ?: return@forEach
            addName(groupNames, name)
        }
        if (groupNames.isEmpty()) {
            return ReminderList(
                grouped = false,
                sections = listOf(ReminderSection(null, ordered(reminders, now), true))
            )
        }

        val grouped = groupNames.associateWith { mutableListOf<Reminder>() }.toMutableMap()
        val ungrouped = mutableListOf<Reminder>()
        reminders.forEach { reminder ->
            val name = reminder.group
            if (name == null) {
                ungrouped.add(reminder)
            } else {
                // Сопоставление без учёта регистра: в старых копиях могли быть разные варианты.
                val canonical = groupNames.firstOrNull { it.equals(name, ignoreCase = true) } ?: name
                grouped.getOrPut(canonical) { mutableListOf() }.add(reminder)
            }
        }
        val sections = groupNames.map { name ->
            ReminderSection(name, ordered(grouped[name].orEmpty(), now), isExpanded(name))
        }.toMutableList()
        if (ungrouped.isNotEmpty()) {
            sections.add(ReminderSection(null, ordered(ungrouped, now), isExpanded(null)))
        }

        // Ближайшее активное срабатывание: группы без предстоящих — вниз.
        // Название и признак «Без группы» — стабильные tie-breakers.
        sections.sortWith(
            compareBy(
                { nearestUpcoming(it.reminders, now) ?: Long.MAX_VALUE },
                { it.name?.lowercase(Locale.getDefault()) ?: "\uFFFF" },
                { it.name == null }
            )
        )
        return ReminderList(grouped = true, sections = sections)
    }

    /** Ближайшее будущее срабатывание среди включённых напоминаний. */
    fun nearestUpcoming(reminders: List<Reminder>, now: Long): Long? =
        reminders.asSequence()
            .filter { it.enabled }
            .mapNotNull { it.nextTriggerAt(now) }
            .minOrNull()

    private fun addName(names: MutableList<String>, raw: String) {
        val name = raw.trim()
        if (name.isEmpty()) return
        if (names.any { it.equals(name, ignoreCase = true) }) return
        names.add(name)
    }

    private fun displayGroup(reminder: Reminder): Int = when {
        !reminder.enabled && reminder.repeatType == RepeatType.ONCE && reminder.lastTriggeredAt > 0 -> 2
        !reminder.enabled -> 1
        else -> 0
    }

    private fun displayTime(reminder: Reminder, now: Long): Long = when (displayGroup(reminder)) {
        0 -> reminder.nextTriggerAt(now) ?: Long.MAX_VALUE
        else -> -reminder.dateTimeMillis
    }
}
