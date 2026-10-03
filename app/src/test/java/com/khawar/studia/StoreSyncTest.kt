package com.khawar.studia

import com.khawar.studia.data.AppData
import com.khawar.studia.data.Remote
import com.khawar.studia.data.Store
import com.khawar.studia.data.Subject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StoreSyncTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private class FakeRemote(var text: String?) : Remote {
        override val label = "your save file"
        override val fileName = "studia.json"
        override val location = "Documents/studia.json"
        var writes = 0
        override fun read() = text
        override fun write(text: String) { this.text = text; writes++ }
    }

    private fun store(local: AppData): Store {
        val f = File.createTempFile("studia", ".json").apply { deleteOnExit() }
        f.writeText(json.encodeToString(AppData.serializer(), local))
        return Store(f, CoroutineScope(SupervisorJob() + Dispatchers.IO)) { AppData() }
    }

    private fun data(name: String, savedAt: Long) = AppData(subjects = listOf(Subject(name, name)), savedAt = savedAt)
    private fun encode(d: AppData) = json.encodeToString(AppData.serializer(), d)

    @Test fun newerRemoteCopyReplacesLocal() = runBlocking {
        val s = store(data("local", 100))
        s.connect(FakeRemote(encode(data("remote", 200))), useRemoteData = false, push = false)
        s.pull()
        assertEquals("remote", s.value.subjects.single().name)
        assertNull(s.sync.value.error)
    }

    @Test fun olderRemoteCopyIsOverwritten() = runBlocking {
        val s = store(data("local", 300))
        val r = FakeRemote(encode(data("remote", 200)))
        s.connect(r, useRemoteData = false, push = false)
        s.pull()
        assertEquals("local", s.value.subjects.single().name)
        assertTrue(r.text!!.contains("\"local\""))
    }

    @Test fun usingAnotherPhonesFileTakesItsData() = runBlocking {
        val s = store(data("local", 300))
        val err = s.connect(FakeRemote(encode(data("other phone", 100))), useRemoteData = true)
        assertNull(err)
        assertEquals("other phone", s.value.subjects.single().name)
    }

    @Test fun rejectsFilesThatAreNotStudiaData() = runBlocking {
        val s = store(data("local", 300))
        val err = s.connect(FakeRemote("hello, I am a shopping list"), useRemoteData = true)
        assertNotNull(err)
        assertEquals("local", s.value.subjects.single().name)
        assertTrue(!s.sync.value.connected)
    }

    @Test fun unreachableRemoteReportsErrorAndKeepsData() = runBlocking {
        val s = store(data("local", 300))
        s.connect(FakeRemote(null), useRemoteData = false, push = false)
        s.pull()
        assertNotNull(s.sync.value.error)
        assertEquals("local", s.value.subjects.single().name)
    }
}
