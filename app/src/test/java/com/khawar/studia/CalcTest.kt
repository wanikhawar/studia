package com.khawar.studia

import com.khawar.studia.data.AppData
import com.khawar.studia.data.Calc
import com.khawar.studia.data.Sample
import com.khawar.studia.data.Session
import com.khawar.studia.data.Status
import com.khawar.studia.data.Subject
import com.khawar.studia.data.Topic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class CalcTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 3)
    private fun at(d: LocalDate, hour: Int = 10) = d.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun session(d: LocalDate, min: Int, sid: String = "s") = Session("x$d$min", sid, null, at(d), min)

    @Test fun streakCountsBackFromToday() {
        val by = Calc.byDay(listOf(session(today, 30), session(today.minusDays(1), 20), session(today.minusDays(2), 10), session(today.minusDays(4), 10)), zone)
        assertEquals(3, Calc.streak(by, today))
    }

    @Test fun streakStillCountsWhenTodayIsEmpty() {
        val by = Calc.byDay(listOf(session(today.minusDays(1), 20), session(today.minusDays(2), 10)), zone)
        assertEquals(2, Calc.streak(by, today))
    }

    @Test fun bestStreakFindsLongestRun() {
        val days = listOf(10, 9, 8, 7, 3, 2).map { session(today.minusDays(it.toLong()), 25) }
        assertEquals(4, Calc.bestStreak(Calc.byDay(days, zone), today))
    }

    @Test fun formatting() {
        assertEquals("45m", Calc.fmt(45))
        assertEquals("2h", Calc.fmt(120))
        assertEquals("1h 20m", Calc.fmt(80))
        assertEquals("00:50", Calc.hhmm(50))
        assertEquals("1.5", Calc.hrs(90))
    }

    @Test fun paceNeedsExamDate() {
        val s = Subject("s", "Physics", topics = listOf(Topic("a", "A", Status.DONE), Topic("b", "B")))
        assertNull(Calc.pace(s, 120, today))
        val withExam = s.copy(exam = today.plusDays(10).toString())
        // 1 done topic took 120 min, 1 left, 10 days -> 12 min/day = 0.2 h/day
        assertEquals(0.2, Calc.pace(withExam, 120, today)!!, 1e-9)
    }

    @Test fun monthRangeHasEveryDayAndFindsToday() {
        val info = Calc.rangeInfo(Calc.Range.MONTH, today, emptyMap(), today)
        assertEquals(31, info.bars.size)
        assertEquals(2, info.todayIndex)
        assertEquals("October 2026", info.title)
        assertEquals(3, info.elapsedDays)
    }

    @Test fun weekStartsOnMonday() {
        val info = Calc.rangeInfo(Calc.Range.WEEK, today, emptyMap(), today)
        assertEquals(LocalDate.of(2026, 9, 28), info.from)
        assertEquals(5, info.todayIndex)
    }

    @Test fun ganttFlagsSubjectsBehindSchedule() {
        // Started 20 days ago, exam in 10 days -> 2/3 of the time gone, only 1 of 4 topics done.
        val s = Subject("s", "Physics", exam = today.plusDays(10).toString(), topics = listOf(
            Topic("a", "A", Status.DONE), Topic("b", "B"), Topic("c", "C"), Topic("d", "D"),
        ))
        val data = AppData(subjects = listOf(s), sessions = listOf(session(today.minusDays(20), 60)))
        val row = Calc.gantt(data, today, zone)!!.rows.single()
        assertTrue(row.behind)
        assertTrue(row.label.startsWith("Behind"))
        assertEquals(.25f, row.progress, 1e-6f)
    }

    @Test fun ganttEstimatesEndWithoutExam() {
        val s = Subject("s", "English", topics = listOf(Topic("a", "A", Status.DONE), Topic("b", "B")))
        val data = AppData(subjects = listOf(s), sessions = listOf(session(today.minusDays(3), 140)))
        val row = Calc.gantt(data, today, zone)!!.rows.single()
        assertTrue(row.estimated)
        assertTrue(row.end.isAfter(today))
    }

    @Test fun sampleDataIsConsistent() {
        val d = Sample.create(at(today), zone)
        assertEquals(4, d.subjects.size)
        val topicIds = d.subjects.flatMap { s -> s.topics.map { it.id } }.toSet()
        assertTrue(d.sessions.all { it.topicId in topicIds })
        assertTrue(Calc.streak(Calc.byDay(d.sessions, zone), today) >= 20)
    }
}
