package com.shivam.glasslauncher.widgets

/**
 * Smart Stack time-of-day rotation: which widget a stack should surface at a given hour.
 * Morning favours the calendar (then weather), the middle of the day weather, the evening music,
 * and late night the batteries. Pure, so it is unit-tested.
 */
object StackRotation {

    enum class Period { MORNING, DAY, EVENING, NIGHT }

    fun period(hour: Int): Period = when (((hour % 24) + 24) % 24) {
        in 5..10 -> Period.MORNING
        in 11..16 -> Period.DAY
        in 17..22 -> Period.EVENING
        else -> Period.NIGHT
    }

    fun preferences(period: Period): List<WidgetKind> = when (period) {
        Period.MORNING -> listOf(WidgetKind.CALENDAR, WidgetKind.WEATHER, WidgetKind.CLOCK)
        Period.DAY -> listOf(WidgetKind.WEATHER, WidgetKind.CALENDAR, WidgetKind.PHOTOS)
        Period.EVENING -> listOf(WidgetKind.MUSIC, WidgetKind.PHOTOS, WidgetKind.WEATHER)
        Period.NIGHT -> listOf(WidgetKind.BATTERY, WidgetKind.CLOCK, WidgetKind.MUSIC)
    }

    /** Index in [kinds] to show at [hour], or null when none of the period's favourites is in the stack. */
    fun pick(kinds: List<WidgetKind>, hour: Int): Int? {
        for (k in preferences(period(hour))) {
            val i = kinds.indexOf(k)
            if (i >= 0) return i
        }
        return null
    }
}
