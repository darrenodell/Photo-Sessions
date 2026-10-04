package com.example.photosessions

const val SESSION_TIMEOUT = 15 * 60 * 1000L

data class Photo(val key: String, val uri: String, val time: Long, val sessionId: Long = 0, val available: Boolean = true, val copyUri: String? = null, val copyState: String = "pending",
    val driveState: String = "none", val driveFileId: String? = null, val driveAccount: String? = null)
data class Session(val id: Long, val name: String?, val start: Long, val end: Long, val complete: Boolean, val prompt: String = "pending", val manual: Boolean = false, val stoppedAt: Long? = null, val sessionDate: String = SessionLabels.date(start))
data class Period(val sessionId: Long, val start: Long, val stop: Long? = null)
data class Catalog(val sessions: List<Session> = emptyList(), val photos: List<Photo> = emptyList(), val periods: List<Period> = emptyList())

/** Pure, deterministic reconciliation. Existing IDs and memberships never change. */
object SessionRules {
    fun reconcile(old: Catalog, discovered: List<Photo>, now: Long, nextIdFloor: Long = 1): Catalog {
        val sessions = old.sessions.associateBy { it.id }.toMutableMap()
        val photos = old.photos.associateBy { it.key }.toMutableMap()
        var nextId = maxOf((sessions.keys.maxOrNull() ?: 0) + 1, nextIdFloor)
        for (photo in discovered.distinctBy { it.key }.sortedWith(compareBy<Photo> { it.time }.thenBy { it.key })) {
            if (photo.key in photos) continue
            // Late indexing may place a photo before or inside an existing session. In a bridge
            // between two committed sessions, prefer the nearest (earlier on a tie); never merge IDs.
            val period = old.periods.lastOrNull { period ->
                val last = photos.values.filter { it.sessionId == period.sessionId && it.time >= period.start && (period.stop == null || it.time < period.stop) }.maxOfOrNull { it.time } ?: period.start
                photo.time >= period.start && photo.time < (period.stop ?: (last + SESSION_TIMEOUT))
            }
            val match = period?.let { sessions[it.sessionId] } ?: sessions.values.filter {
                !it.manual && (it.stoppedAt == null || photo.time < it.stoppedAt) &&
                photo.time > it.start - SESSION_TIMEOUT && photo.time < it.end + SESSION_TIMEOUT
            }.minWithOrNull(compareBy<Session> {
                when { photo.time < it.start -> it.start - photo.time; photo.time > it.end -> photo.time - it.end; else -> 0L }
            }.thenBy { it.start }.thenBy { it.id })
            val session = if (match == null) Session(nextId++, null, photo.time, photo.time, false)
                else match.copy(start = minOf(match.start, photo.time), end = maxOf(match.end, photo.time))
            sessions[session.id] = session
            photos[photo.key] = photo.copy(sessionId = session.id)
        }
        val accessible = discovered.mapTo(hashSetOf()) { it.key }
        return Catalog(
            sessions.values.map { session ->
                val open = old.periods.lastOrNull { it.sessionId == session.id && it.stop == null }
                val last = maxOf(session.end, open?.start ?: session.end)
                session.copy(complete = if(session.manual) open == null || now - last >= SESSION_TIMEOUT else session.stoppedAt != null || now - session.end >= SESSION_TIMEOUT)
            }.sortedByDescending { it.start },
            photos.values.map { it.copy(available = it.key in accessible) }.sortedBy { it.time },
            old.periods.map { period ->
                val last = maxOf(period.start, photos.values.filter { it.sessionId == period.sessionId && it.time >= period.start }.maxOfOrNull { it.time } ?: period.start)
                if(period.stop == null && now - last >= SESSION_TIMEOUT) period.copy(stop = last + SESSION_TIMEOUT) else period
            }
        )
    }

    /** Missing, pre-epoch or future capture dates fall back to MediaStore's insertion date. */
    fun effectiveTime(taken: Long, addedSeconds: Long, now: Long): Long? =
        taken.takeIf { it in 1..now } ?: (addedSeconds.takeIf { it > 0 && it <= now / 1000 }?.times(1000))

    fun cameraPath(path: String): Boolean = path.trim('/').lowercase(java.util.Locale.ROOT).let {
        it == "dcim/camera" || it.startsWith("dcim/camera/")
    }
}

/** The label date is persisted separately from capture intervals and the descriptive name. */
object SessionLabels {
    fun date(time: Long): String = java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    fun validDate(value: String): Boolean = runCatching { java.time.LocalDate.parse(value).toString() == value && value.length == 10 }.getOrDefault(false)
    fun title(session: Session): String = "${session.sessionDate} — ${session.name ?: "Unnamed session"}"
    fun localPath(session: Session): String = "Pictures/Photo Sessions/" + title(session)
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(100) + " (${session.id})/"
    fun unexported(photos: List<Photo>): Int = photos.count {
        !(it.copyState == "copied" && it.copyUri != null) && !(it.driveState == "uploaded" && it.driveFileId != null)
    }
}
