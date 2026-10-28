package com.tmksdm.ritmo

import java.util.Locale

/**
 * Порядок единого списка и выборка по тегу. Вынесено без зависимостей от Android,
 * чтобы проверять сортировку и фильтр отдельно от интерфейса.
 */
object ReminderOrdering {

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

    /** Напоминания выбранного тега; сравнение без учёта регистра. */
    fun withTag(reminders: List<Reminder>, tag: String): List<Reminder> =
        reminders.filter { TagNames.contains(it.tags, tag) }

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
