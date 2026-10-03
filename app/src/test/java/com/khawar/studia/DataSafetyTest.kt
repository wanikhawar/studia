package com.khawar.studia

import com.khawar.studia.data.AppData
import com.khawar.studia.data.Calc
import com.khawar.studia.data.DataFile
import com.khawar.studia.data.Remote
import com.khawar.studia.data.Session
import com.khawar.studia.data.Status
import com.khawar.studia.data.Store
import com.khawar.studia.data.Subject
import com.khawar.studia.data.Topic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import java.nio.file.Files

/** Reproductions of the reported data bugs, kept as regression tests. */
class DataSafetyTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 3)
    private val json = DataFile.json
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun real() = AppData(
        subjects = listOf(Subject("s", "Physics", topics = listOf(Topic("t", "Waves")))),
        sessions = listOf(Session("x", "s", "t", at(today, 9), 50)),
        savedAt = 100,
    )
    private fun encode(d: AppData) = json.encodeToString(AppData.serializer(), d)
    private fun dir(): File = Files.createTempDirectory("studia").toFile().apply { deleteOnExit() }
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---------- opening files ----------

    @Test fun unrelatedJsonIsNotAcceptedAsAnEmptyDatabase() {
        val r = DataFile.read("""{"items":["milk","eggs"],"store":"Tesco"}""")
        assertTrue(r is DataFile.Result.Bad)
    }

    @Test fun invalidExamDateIsRejected() {
        val bad = real().copy(subjects = listOf(Subject("s", "Physics", exam = "2026-13-45", topics = listOf(Topic("t", "Waves")))))
        val r = DataFile.read(encode(bad))
        assertTrue(r is DataFile.Result.Bad && r.reason.contains("exam date"))
    }

    @Test fun impossibleSessionsAndBrokenLinksAreRejected() {
        assertTrue(DataFile.read(encode(real().copy(sessions = listOf(Session("x", "s", null, at(today, 9), 0))))) is DataFile.Result.Bad)
        assertTrue(DataFile.read(encode(real().copy(sessions = listOf(Session("x", "nope", null, at(today, 9), 30))))) is DataFile.Result.Bad)
        assertTrue(DataFile.read(encode(real().copy(sessions = listOf(Session("x", "s", "gone", at(today, 9), 30))))) is DataFile.Result.Bad)
    }

    @Test fun realDataAndOlderUntaggedFilesAreAccepted() {
        assertTrue(DataFile.read(encode(real())) is DataFile.Result.Ok)
        val untagged = encode(real()).replace("\"app\":\"studia\",", "")
        assertFalse(untagged.contains("\"app\""))
        assertTrue(DataFile.read(untagged) is DataFile.Result.Ok)
    }

    @Test fun openingAnUnrelatedFileLeavesDataAlone() = runBlocking {
        val d = dir(); File(d, "studia.json").writeText(encode(real()))
        val store = Store(File(d, "studia.json"), scope()) { AppData() }
        val err = store.connect(FakeRemote("""{"items":["milk"]}"""), useRemoteData = true)
        assertNotNull(err)
        assertEquals("Physics", store.value.subjects.single().name)
        assertNull(store.backup.value)
    }

    // ---------- damaged local save ----------

    @Test fun damagedSaveIsKeptAndNotReplacedWithSampleData() {
        val d = dir(); val f = File(d, "studia.json"); f.writeText("{ this is not json")
        val store = Store(f, scope()) { error("must not seed sample data over a damaged file") }
        val problem = store.problem.value
        assertNotNull(problem)
        assertEquals("{ this is not json", problem!!.damagedFile!!.readText())
        assertTrue(store.value.subjects.isEmpty())
    }

    @Test fun damagedSaveRecoversFromThePreviousGoodCopy() {
        val d = dir(); val f = File(d, "studia.json")
        File(d, "studia.previous.json").writeText(encode(real()))
        f.writeText("garbage")
        val store = Store(f, scope()) { AppData() }
        assertEquals("Physics", store.value.subjects.single().name)
        assertNotNull(store.problem.value)
    }

    @Test fun clearingThenDamageStillRecoversRealData() = runBlocking {
        val d = dir(); val f = File(d, "studia.json")
        f.writeText(encode(real()))
        val store = Store(f, scope()) { AppData() }
        store.update { AppData() }            // cleared...
        store.flush()
        store.update { real() }               // ...then restored
        store.flush()
        store.update { AppData() }            // ...and cleared again
        store.flush()
        f.writeText("{ broken")                // then the file gets damaged
        val reopened = Store(f, scope()) { AppData() }
        assertEquals("Physics", reopened.value.subjects.single().name)
    }

    // ---------- backups ----------

    @Test fun backupRestoresDataReplacedBySampleOrClear() {
        val d = dir(); File(d, "studia.json").writeText(encode(real()))
        val store = Store(File(d, "studia.json"), scope()) { AppData() }
        store.saveBackup("loading sample data")
        store.update { AppData(sample = true) }
        assertTrue(store.value.subjects.isEmpty())
        assertTrue(store.restoreBackup())
        assertEquals("Physics", store.value.subjects.single().name)
        assertNull(store.backup.value)
    }

    // ---------- connecting ----------

    @Test fun connectFailsWhenTheFirstSaveFails() = runBlocking {
        val d = dir(); File(d, "studia.json").writeText(encode(real()))
        val store = Store(File(d, "studia.json"), scope()) { AppData() }
        val err = store.connect(FakeRemote(null, failWrites = true), useRemoteData = false)
        assertNotNull(err)
        assertFalse(store.sync.value.connected)
    }

    // ---------- history ----------

    @Test fun stoppingAfterSecondsLogsNothing() {
        assertEquals(0, Calc.loggedMinutes(8_000, 50))
        assertEquals(1, Calc.loggedMinutes(119_000, 50))
        assertEquals(50, Calc.loggedMinutes(3 * 3_600_000L, 50))
    }

    @Test fun sessionsAcrossMidnightAreSplitBetweenDays() {
        val s = Session("x", "s", null, at(today.minusDays(1), 23, 50), 50)
        val by = Calc.byDay(listOf(s), zone)
        assertEquals(10, by[today.minusDays(1)])
        assertEquals(40, by[today])
        assertEquals(40, Calc.minutesIn(s, today, today.plusDays(1), zone))
    }

    @Test fun pastExamIsNeverOnTrack() {
        // Created today with an exam date that has already passed (before the subject's start).
        val s = Subject("s", "History", exam = today.minusDays(5).toString(), created = at(today, 8), topics = listOf(Topic("t", "A")))
        val row = Calc.gantt(AppData(subjects = listOf(s)), today, zone)!!.rows.single()
        assertEquals("Exam passed", row.label)
        assertTrue(row.behind)
    }

    @Test fun finishedSubjectStillShowsDoneAfterItsExam() {
        val s = Subject("s", "History", exam = today.minusDays(5).toString(), created = at(today, 8), topics = listOf(Topic("t", "A", Status.DONE)))
        assertEquals("Done", Calc.gantt(AppData(subjects = listOf(s)), today, zone)!!.rows.single().label)
    }

    private class FakeRemote(var text: String?, val failWrites: Boolean = false) : Remote {
        override val label = "your save file"
        override val fileName = "studia.json"
        override val location = "Documents/studia.json"
        override fun read() = text
        override fun write(text: String) { if (failWrites) throw IOException("read-only"); this.text = text }
    }
}
