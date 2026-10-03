package com.khawar.studia.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/**
 * Reading save files safely. Anything that could replace the user's data goes
 * through [read], which only accepts files that really are Studia data and whose
 * contents make sense, so a stray JSON file can't silently become an empty database.
 */
object DataFile {
    const val APP_ID = "studia"

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    sealed interface Result {
        data class Ok(val data: AppData) : Result
        data class Bad(val reason: String) : Result
    }

    fun read(text: String): Result {
        val obj = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: return Result.Bad("That file isn’t a Studia save file.")
        // New files carry "app": "studia". Files saved before that tag existed are
        // recognised by having all three top-level sections.
        val tagged = runCatching { obj["app"]?.jsonPrimitive?.contentOrNull }.getOrNull() == APP_ID
        val legacy = "subjects" in obj && "sessions" in obj && "settings" in obj
        if (!tagged && !legacy) return Result.Bad("That file isn’t a Studia save file.")
        val data = runCatching { json.decodeFromJsonElement(AppData.serializer(), obj) }.getOrNull()
            ?: return Result.Bad("That Studia file is damaged and can’t be read.")
        validate(data)?.let { return Result.Bad(it) }
        return Result.Ok(data)
    }

    /** A user-facing reason the data can't be used, or null when it's fine. */
    fun validate(d: AppData): String? {
        val subjectIds = d.subjects.map { it.id }
        val topicIds = d.subjects.flatMap { s -> s.topics.map { it.id } }
        if (subjectIds.size != subjectIds.toSet().size || topicIds.size != topicIds.toSet().size)
            return "That file has duplicate subjects or topics."
        for (s in d.subjects) {
            if (s.name.isBlank()) return "That file has a subject without a name."
            if (s.exam != null && runCatching { LocalDate.parse(s.exam) }.isFailure)
                return "That file has an invalid exam date (“${s.exam}”)."
            if (s.targetHours < 0) return "That file has a negative target for ${s.name}."
        }
        val subjects = subjectIds.toSet()
        val topicsBySubject = d.subjects.associate { s -> s.id to s.topics.map { it.id }.toSet() }
        for (x in d.sessions) {
            if (x.minutes !in 1..MAX_SESSION_MINUTES || x.start <= 0)
                return "That file has a study session with an impossible length or time."
            if (x.subjectId !in subjects) return "That file has study sessions for a subject that doesn’t exist."
            if (x.topicId != null && x.topicId !in topicsBySubject.getValue(x.subjectId))
                return "That file has study sessions for a topic that doesn’t exist."
        }
        d.run?.let { r ->
            if (r.subjectId !in subjects || r.minutes !in 1..MAX_SESSION_MINUTES || r.extra < 0)
                return "That file has a broken running session."
        }
        return null
    }

    /** Longest believable single session: the dial goes to 4 h, plus extensions, with headroom. */
    const val MAX_SESSION_MINUTES = 24 * 60
}
