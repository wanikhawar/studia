package com.khawar.studia.data

import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** Example subjects and two months of sessions, so a fresh install isn't empty. */
object Sample {
    fun create(now: Long, zone: ZoneId): AppData {
        val r = Random(11)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun topics(vararg t: Triple<String, Status, Int>) = t.map { Topic(newId(), it.first, it.second, it.third) }
        val d = Status.DONE; val p = Status.DOING; val o = Status.TODO

        val physics = Subject(newId(), "Physics", today.plusDays(24).toString(), 40, now, topics(
            Triple("Kinematics", d, 4), Triple("Newton’s laws", d, 5), Triple("Work and energy", d, 4),
            Triple("Momentum", p, 4), Triple("Circular motion", o, 3), Triple("Gravitation", o, 4),
            Triple("Oscillations", o, 5), Triple("Waves", o, 5),
        ))
        val calculus = Subject(newId(), "Calculus", today.plusDays(31).toString(), 35, now, topics(
            Triple("Limits", d, 3), Triple("Derivatives", d, 4), Triple("Chain rule", d, 3),
            Triple("Implicit differentiation", p, 3), Triple("Integration by parts", o, 4),
            Triple("Series and convergence", o, 6),
        ))
        val chemistry = Subject(newId(), "Organic Chemistry", today.plusDays(38).toString(), 30, now, topics(
            Triple("Nomenclature", d, 3), Triple("Isomerism", p, 4), Triple("Alkanes", o, 3),
            Triple("Alkenes", o, 4), Triple("SN1 and SN2 reactions", o, 5), Triple("Aromatic compounds", o, 5),
        ))
        val english = Subject(newId(), "English Literature", null, 15, now, topics(
            Triple("Macbeth", d, 5), Triple("Poetry anthology", p, 6), Triple("Essay structure", o, 2),
        ))
        val subjects = listOf(physics, calculus, chemistry, english)

        val weights = doubleArrayOf(.35, .3, .2, .15)
        val lengths = intArrayOf(25, 30, 45, 50, 60, 90)
        fun pickSubject(): Subject {
            var x = r.nextDouble(); var i = 0
            while (i < 3) { x -= weights[i]; if (x <= 0) break; i++ }
            return subjects[i]
        }
        val sessions = mutableListOf<Session>()
        for (i in 62 downTo 1) {
            if (i > 22 && r.nextDouble() < .3) continue
            val day = today.minusDays(i.toLong())
            repeat(1 + r.nextInt(3)) {
                val s = pickSubject()
                val pool = s.topics.filter { it.status != Status.TODO }
                val t = pool[r.nextInt(pool.size)]
                val start = day.atTime(8 + r.nextInt(13), 0).atZone(zone).toInstant().toEpochMilli()
                sessions += Session(newId(), s.id, t.id, start, lengths[r.nextInt(lengths.size)])
            }
        }
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        sessions += Session(newId(), physics.id, physics.topics[3].id, maxOf(todayStart + 60_000, now - 90 * 60_000), 50)

        return AppData(
            subjects = subjects,
            sessions = sessions,
            sample = true,
            focusSubjectId = physics.id,
            focusTopicId = physics.topics[3].id,
        )
    }
}
