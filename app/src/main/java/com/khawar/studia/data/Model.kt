package com.khawar.studia.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class Status { TODO, DOING, DONE }

@Serializable
data class Topic(
    val id: String,
    val title: String,
    val status: Status = Status.TODO,
    val estHours: Int = 0,
)

@Serializable
data class Subject(
    val id: String,
    val name: String,
    /** Exam date as ISO yyyy-MM-dd, or null when there is no exam. */
    val exam: String? = null,
    val targetHours: Int = 0,
    val created: Long = 0,
    val topics: List<Topic> = emptyList(),
)

@Serializable
data class Session(
    val id: String,
    val subjectId: String,
    val topicId: String?,
    val start: Long,
    val minutes: Int,
)

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val haptics: Boolean = true,
    val sound: Boolean = false,
    val goalMinutes: Int = 180,
    val defaultLength: Int = 50,
)

/** A focus session in progress. Stored, so it survives the app being closed. */
@Serializable
data class RunState(
    val start: Long,
    val minutes: Int,
    val extra: Int = 0,
    val subjectId: String,
    val topicId: String?,
) {
    val endsAt: Long get() = start + (minutes + extra) * 60_000L
}

@Serializable
data class AppData(
    /** Identifies a Studia save file, so unrelated JSON is never mistaken for one. */
    val app: String = DataFile.APP_ID,
    val subjects: List<Subject> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val settings: Settings = Settings(),
    val run: RunState? = null,
    val sample: Boolean = false,
    val focusSubjectId: String? = null,
    val focusTopicId: String? = null,
    val focusLength: Int = 50,
    /** When this data was last changed, on any device. Newest copy wins when syncing. */
    val savedAt: Long = 0,
) {
    fun subject(id: String?): Subject? = subjects.find { it.id == id }

    fun topic(id: String?): Pair<Subject, Topic>? {
        if (id == null) return null
        for (s in subjects) s.topics.find { it.id == id }?.let { return s to it }
        return null
    }

    fun mapTopic(id: String, f: (Topic) -> Topic): AppData = copy(
        subjects = subjects.map { s ->
            if (s.topics.none { it.id == id }) s else s.copy(topics = s.topics.map { if (it.id == id) f(it) else it })
        }
    )
}

fun newId(): String = UUID.randomUUID().toString().substring(0, 8)
