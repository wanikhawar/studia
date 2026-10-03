package com.khawar.studia.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Pure calculations behind the screens. No Android types, so they are unit-tested. */
object Calc {
    fun day(ts: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

    /**
     * Minutes studied per local day. A session that crosses midnight is split, so
     * 23:50–00:40 counts 10 minutes for the first day and 40 for the next.
     */
    fun byDay(sessions: List<Session>, zone: ZoneId): Map<LocalDate, Int> {
        val ms = HashMap<LocalDate, Long>()
        for (s in sessions) forEachDayPart(s, zone) { d, part -> ms[d] = (ms[d] ?: 0L) + part }
        return ms.mapValues { (it.value / 60_000.0).roundToInt() }.filterValues { it > 0 }
    }

    /** Minutes of [s] that fall on days in [from, to). */
    fun minutesIn(s: Session, from: LocalDate, to: LocalDate, zone: ZoneId): Int {
        var total = 0L
        forEachDayPart(s, zone) { d, part -> if (!d.isBefore(from) && d.isBefore(to)) total += part }
        return (total / 60_000.0).roundToInt()
    }

    private inline fun forEachDayPart(s: Session, zone: ZoneId, f: (LocalDate, Long) -> Unit) {
        var t = s.start
        val end = s.start + s.minutes * 60_000L
        while (t < end) {
            val d = day(t, zone)
            val next = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val e = minOf(end, next)
            f(d, e - t)
            t = e
        }
    }

    /**
     * Whole minutes actually studied, capped at the planned length. Partial minutes
     * are dropped, so stopping after a few seconds logs nothing instead of a minute.
     */
    fun loggedMinutes(elapsedMs: Long, plannedMinutes: Int): Int =
        (elapsedMs / 60_000L).toInt().coerceIn(0, plannedMinutes)

    /** Consecutive days with study, ending today (or yesterday if today has none yet). */
    fun streak(byDay: Map<LocalDate, Int>, today: LocalDate): Int {
        var d = if ((byDay[today] ?: 0) > 0) today else today.minusDays(1)
        var n = 0
        while ((byDay[d] ?: 0) > 0) { n++; d = d.minusDays(1) }
        return n
    }

    fun bestStreak(byDay: Map<LocalDate, Int>, today: LocalDate): Int {
        var d = byDay.keys.minOrNull() ?: return 0
        var best = 0; var cur = 0
        while (!d.isAfter(today)) {
            cur = if ((byDay[d] ?: 0) > 0) cur + 1 else 0
            best = max(best, cur)
            d = d.plusDays(1)
        }
        return best
    }

    data class Counts(val done: Int, val doing: Int, val todo: Int) {
        val total get() = done + doing + todo
        val left get() = doing + todo
    }

    fun counts(s: Subject) = Counts(
        s.topics.count { it.status == Status.DONE },
        s.topics.count { it.status == Status.DOING },
        s.topics.count { it.status == Status.TODO },
    )

    fun daysTo(exam: String?, today: LocalDate): Int? =
        exam?.let { ChronoUnit.DAYS.between(today, LocalDate.parse(it)).toInt() }

    fun subjectMinutes(data: AppData, id: String) = data.sessions.filter { it.subjectId == id }.sumOf { it.minutes }
    fun topicMinutes(data: AppData, id: String) = data.sessions.filter { it.topicId == id }.sumOf { it.minutes }

    /**
     * Hours per day needed to finish before the exam, at the pace so far
     * (minutes spent per finished topic; an in-progress topic counts as half).
     */
    fun pace(s: Subject, minutes: Int, today: LocalDate): Double? {
        val c = counts(s)
        val days = daysTo(s.exam, today) ?: return null
        if (days <= 0 || c.total == 0) return null
        val left = c.todo + c.doing * .5
        if (left == 0.0) return 0.0
        val doneUnits = c.done + c.doing * .5
        val perTopic = if (doneUnits > 0) minutes / doneUnits else 180.0
        return left * perTopic / days / 60
    }

    fun fmt(minutes: Number): String {
        val m = minutes.toDouble().roundToInt()
        if (m < 60) return "${m}m"
        val h = m / 60; val r = m % 60
        return if (r == 0) "${h}h" else "${h}h ${r}m"
    }

    fun hrs(minutes: Int): String =
        if (minutes >= 600) "${(minutes / 60.0).roundToInt()}" else String.format(Locale.US, "%.1f", minutes / 60.0)

    fun hhmm(minutes: Int): String = String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

    fun monthName(d: LocalDate): String = d.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    fun mon3(d: LocalDate): String = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    fun dow(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    fun short(d: LocalDate): String = "${d.dayOfMonth} ${mon3(d)}"

    // ---------- stats ranges ----------

    enum class Range { WEEK, MONTH, YEAR }

    data class Bar(val minutes: Int, val axis: String, val caption: String, val start: LocalDate)

    data class RangeInfo(
        val bars: List<Bar>,
        val from: LocalDate,
        /** Exclusive. */
        val to: LocalDate,
        val title: String,
        val todayIndex: Int?,
        /** Days of the range that have already happened, for the daily average. */
        val elapsedDays: Int,
    )

    fun rangeInfo(range: Range, anchor: LocalDate, byDay: Map<LocalDate, Int>, today: LocalDate): RangeInfo {
        return when (range) {
            Range.WEEK -> {
                val from = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val bars = (0..6).map {
                    val d = from.plusDays(it.toLong())
                    Bar(byDay[d] ?: 0, "MTWTFSS"[it].toString(), "${dow(d)} ${d.dayOfMonth}", d)
                }
                val last = from.plusDays(6)
                RangeInfo(
                    bars, from, from.plusDays(7), "${short(from)} – ${short(last)}",
                    bars.indexOfFirst { it.start == today }.takeIf { it >= 0 },
                    bars.count { !it.start.isAfter(today) },
                )
            }
            Range.MONTH -> {
                val from = anchor.withDayOfMonth(1)
                val bars = (1..from.lengthOfMonth()).map {
                    val d = from.withDayOfMonth(it)
                    Bar(byDay[d] ?: 0, if (it in setOf(1, 8, 15, 22, 29)) "$it" else "", "${dow(d)} $it", d)
                }
                RangeInfo(
                    bars, from, from.plusMonths(1), "${monthName(from)} ${from.year}",
                    bars.indexOfFirst { it.start == today }.takeIf { it >= 0 },
                    bars.count { !it.start.isAfter(today) },
                )
            }
            Range.YEAR -> {
                val from = anchor.withDayOfYear(1)
                val bars = (1..12).map { m ->
                    val start = from.withMonth(m)
                    val end = start.plusMonths(1)
                    val sum = byDay.entries.filter { !it.key.isBefore(start) && it.key.isBefore(end) }.sumOf { it.value }
                    Bar(sum, monthName(start).take(1), monthName(start), start)
                }
                val to = from.plusYears(1)
                val upTo = minOf(today.plusDays(1), to)
                RangeInfo(
                    bars, from, to, "${from.year}",
                    if (today.year == from.year) today.monthValue - 1 else null,
                    ChronoUnit.DAYS.between(from, upTo).toInt(),
                )
            }
        }
    }

    // ---------- gantt timeline ----------

    data class GanttRow(
        val subject: Subject,
        val start: LocalDate,
        val end: LocalDate,
        val estimated: Boolean,
        val progress: Float,
        val partial: Float,
        val label: String,
        val behind: Boolean,
    )

    data class Gantt(val rows: List<GanttRow>, val from: LocalDate, val to: LocalDate, val today: LocalDate) {
        val spanDays: Float get() = ChronoUnit.DAYS.between(from, to).toFloat()
        fun pos(d: LocalDate): Float = ChronoUnit.DAYS.between(from, d) / spanDays
    }

    /**
     * Each subject runs from its first session to its exam date. Without an exam,
     * the end is an estimate from the last 14 days' pace. Fill = topics completed.
     */
    fun gantt(data: AppData, today: LocalDate, zone: ZoneId): Gantt? {
        if (data.subjects.isEmpty()) return null
        val since = today.minusDays(13)
        val rows = data.subjects.map { s ->
            val c = counts(s)
            val mine = data.sessions.filter { it.subjectId == s.id }
            val first = mine.minOfOrNull { it.start }?.let { day(it, zone) }
            val created = if (s.created > 0) day(s.created, zone) else today
            val start = minOf(first ?: created, today)
            var estimated = false
            var end: LocalDate
            val exam = s.exam?.let(LocalDate::parse)
            if (exam != null) {
                end = exam
            } else {
                estimated = true
                val left = c.todo + c.doing * .5
                val doneUnits = c.done + c.doing * .5
                val perTopic = if (doneUnits > 0) mine.sumOf { it.minutes } / doneUnits else 180.0
                val rate = mine.filter { !day(it.start, zone).isBefore(since) }.sumOf { it.minutes } / 14.0
                end = when {
                    left == 0.0 -> today
                    rate > 0 -> today.plusDays(ceil(left * perTopic / rate).toLong())
                    else -> today.plusDays(60)
                }
                end = minOf(end, today.plusDays(365))
            }
            // The bar needs some length to draw, but labels below use the real exam date.
            if (!end.isAfter(start)) end = start.plusDays(1)

            val progress = if (c.total > 0) c.done.toFloat() / c.total else 0f
            val partial = if (c.total > 0) c.doing.toFloat() / c.total else 0f
            val elapsed = (ChronoUnit.DAYS.between(start, today).toFloat() /
                ChronoUnit.DAYS.between(start, end)).coerceIn(0f, 1f)
            var behind = false
            val label = when {
                c.total > 0 && c.done == c.total -> "Done"
                exam != null && today.isAfter(exam) -> { behind = true; "Exam passed" }
                estimated -> "Est. ${short(end)}"
                progress + .05f >= elapsed -> "On track"
                else -> { behind = true; "Behind ${((elapsed - progress) * 100).roundToInt()}%" }
            }
            GanttRow(s, start, end, estimated, progress, partial, label, behind)
        }
        val from = rows.minOf { it.start }.minusDays(3)
        val to = maxOf(today, rows.maxOf { it.end }).plusDays(3)
        return Gantt(rows, from, to, today)
    }
}
