package com.example.photosessions

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction

/** SQLiteOpenHelper is the sole durable store. No media bytes are stored here. */
class CatalogDb(context: Context, name: String = "sessions.db") : SQLiteOpenHelper(context, name, null, 3) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY, name TEXT, start INTEGER NOT NULL, end INTEGER NOT NULL, complete INTEGER NOT NULL, prompt TEXT NOT NULL, manual INTEGER NOT NULL DEFAULT 0, stopped_at INTEGER)")
        db.execSQL("CREATE TABLE photos(media_key TEXT PRIMARY KEY, uri TEXT NOT NULL, time INTEGER NOT NULL, session_id INTEGER NOT NULL REFERENCES sessions(id), available INTEGER NOT NULL, copy_uri TEXT, copy_state TEXT NOT NULL DEFAULT 'pending', drive_state TEXT NOT NULL DEFAULT 'none', drive_file_id TEXT, drive_account TEXT)")
        db.execSQL("CREATE TABLE periods(session_id INTEGER NOT NULL REFERENCES sessions(id), start INTEGER NOT NULL PRIMARY KEY, stop INTEGER)")
        db.execSQL("CREATE INDEX photos_session ON photos(session_id)")
        db.execSQL("CREATE TABLE state(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if(oldVersion < 2) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN manual INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE sessions ADD COLUMN stopped_at INTEGER")
            db.execSQL("ALTER TABLE photos ADD COLUMN copy_uri TEXT")
            db.execSQL("ALTER TABLE photos ADD COLUMN copy_state TEXT NOT NULL DEFAULT 'pending'")
            db.execSQL("CREATE TABLE periods(session_id INTEGER NOT NULL REFERENCES sessions(id), start INTEGER NOT NULL PRIMARY KEY, stop INTEGER)")
        }
        if(oldVersion < 3) {
            db.execSQL("ALTER TABLE photos ADD COLUMN drive_state TEXT NOT NULL DEFAULT 'none'")
            db.execSQL("ALTER TABLE photos ADD COLUMN drive_file_id TEXT")
            db.execSQL("ALTER TABLE photos ADD COLUMN drive_account TEXT")
        }
    }

    @Synchronized fun state(key: String): String? = readableDatabase.rawQuery("SELECT value FROM state WHERE key=?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }
    @Synchronized fun put(key: String, value: String) {
        writableDatabase.insertWithOnConflict("state", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    @Synchronized fun start(now: Long) {
        writableDatabase.insertWithOnConflict("state", null, ContentValues().apply { put("key", "baseline"); put("value", now.toString()) }, SQLiteDatabase.CONFLICT_IGNORE)
    }
    @Synchronized fun load(): Catalog {
        val sessions = readableDatabase.rawQuery("SELECT id,name,start,end,complete,prompt,manual,stopped_at FROM sessions ORDER BY start DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(Session(c.getLong(0), if(c.isNull(1)) null else c.getString(1), c.getLong(2), c.getLong(3), c.getInt(4) != 0, c.getString(5), c.getInt(6) != 0, if(c.isNull(7)) null else c.getLong(7))) }
        }
        val photos = readableDatabase.rawQuery("SELECT media_key,uri,time,session_id,available,copy_uri,copy_state,drive_state,drive_file_id,drive_account FROM photos ORDER BY time", null).use { c ->
            buildList { while(c.moveToNext()) add(Photo(c.getString(0), c.getString(1), c.getLong(2), c.getLong(3), c.getInt(4) != 0, if(c.isNull(5)) null else c.getString(5), c.getString(6), c.getString(7), if(c.isNull(8)) null else c.getString(8), if(c.isNull(9)) null else c.getString(9))) }
        }
        val periods = readableDatabase.rawQuery("SELECT session_id,start,stop FROM periods ORDER BY start", null).use { c ->
            buildList { while(c.moveToNext()) add(Period(c.getLong(0), c.getLong(1), if(c.isNull(2)) null else c.getLong(2))) }
        }
        return Catalog(sessions, photos, periods)
    }
    @Synchronized fun reconcile(discovered: List<Photo>, now: Long): Catalog {
        val db = writableDatabase
        return db.transaction {
            val result = SessionRules.reconcile(load(), discovered.filter { state("deleted_" + it.key) == null }, now, state("next_id")?.toLongOrNull() ?: 1)
            put("next_id", ((result.sessions.maxOfOrNull { it.id } ?: 0) + 1).coerceAtLeast(state("next_id")?.toLongOrNull() ?: 1).toString())
            for (s in result.sessions) {
                val values = ContentValues().apply {
                    put("id", s.id); put("name", s.name); put("start", s.start); put("end", s.end)
                    put("complete", if(s.complete) 1 else 0); put("prompt", s.prompt); put("manual", if(s.manual) 1 else 0); put("stopped_at", s.stoppedAt)
                }
                // UPDATE, not REPLACE: keep foreign-key membership intact.
                if (db.update("sessions", values, "id=?", arrayOf(s.id.toString())) == 0) db.insertOrThrow("sessions", null, values)
            }
            for (p in result.photos) {
                val values = ContentValues().apply {
                    put("media_key", p.key); put("uri", p.uri); put("time", p.time)
                    put("session_id", p.sessionId); put("available", if(p.available) 1 else 0)
                }
                if(db.update("photos", values, "media_key=?", arrayOf(p.key)) == 0) db.insertOrThrow("photos", null, values)
            }
            for(period in result.periods) db.update("periods", ContentValues().apply { put("stop", period.stop) }, "start=?", arrayOf(period.start.toString()))
            put("last_scan", now.toString())
            result
        }
    }
    @Synchronized fun name(id: Long, name: String?) {
        require(name == null || name.trim().isNotEmpty())
        writableDatabase.update("sessions", ContentValues().apply { put("name", name?.trim()); put("prompt", "handled") }, "id=?", arrayOf(id.toString()))
    }
    @Synchronized fun begin(name: String, now: Long, resumeId: Long? = null): Long = writableDatabase.transaction {
        require(name.isNotBlank())
        start(now)
        val old = load()
        old.sessions.filter { !it.complete }.forEach { stop(it.id, now) }
        val id = resumeId ?: maxOf((old.sessions.maxOfOrNull { it.id } ?: 0) + 1, now, state("next_id")?.toLongOrNull() ?: 1)
        if(resumeId == null) writableDatabase.insertOrThrow("sessions", null, ContentValues().apply {
            put("id", id); put("name", name.trim()); put("start", now); put("end", now)
            put("complete", 0); put("prompt", "handled"); put("manual", 1)
        }) else {
            val previous = old.sessions.single { it.id == id }
            if(!previous.manual) writableDatabase.insertOrThrow("periods", null, ContentValues().apply {
                put("session_id", id); put("start", previous.start)
                put("stop", minOf(now, previous.stoppedAt ?: (previous.end + SESSION_TIMEOUT)))
            })
            writableDatabase.update("sessions", ContentValues().apply { put("manual", 1); put("complete", 0); putNull("stopped_at") }, "id=?", arrayOf(id.toString()))
        }
        writableDatabase.insertOrThrow("periods", null, ContentValues().apply { put("session_id", id); put("start", now) })
        put("next_id", (id + 1).coerceAtLeast(state("next_id")?.toLongOrNull() ?: 1).toString())
        id
    }
    @Synchronized fun stop(id: Long, now: Long) {
        writableDatabase.transaction {
            writableDatabase.update("periods", ContentValues().apply { put("stop", now) }, "session_id=? AND stop IS NULL", arrayOf(id.toString()))
            writableDatabase.update("sessions", ContentValues().apply { put("complete", 1); put("stopped_at", now) }, "id=?", arrayOf(id.toString()))
        }
    }
    @Synchronized fun copyStatus(key: String, uri: String?, state: String) {
        writableDatabase.update("photos", ContentValues().apply { put("copy_uri", uri); put("copy_state", state) }, "media_key=?", arrayOf(key))
    }
    /** Records one photo's Drive upload state for the given Google account. */
    @Synchronized fun driveStatus(key: String, account: String, state: String, fileId: String?) {
        writableDatabase.update("photos", ContentValues().apply { put("drive_state", state); put("drive_file_id", fileId); put("drive_account", account) }, "media_key=?", arrayOf(key))
    }
    @Synchronized fun remove(key: String) { writableDatabase.delete("state", "key=?", arrayOf(key)) }
    @Synchronized fun deleteSession(id: Long) {
        writableDatabase.transaction {
            put("next_id", maxOf(id + 1, state("next_id")?.toLongOrNull() ?: 1).toString())
            // Retain a discovery tombstone so the same photos cannot recreate a deleted session.
            load().photos.filter { it.sessionId == id }.forEach { put("deleted_" + it.key, "1") }
            writableDatabase.delete("periods", "session_id=?", arrayOf(id.toString()))
            writableDatabase.delete("photos", "session_id=?", arrayOf(id.toString()))
            writableDatabase.delete("sessions", "id=?", arrayOf(id.toString()))
        }
    }
    // At-most-once reservation survives crashes. A crash after reservation may lose an alert,
    // but never causes a second prompt; the unnamed session remains visible in the app.
    @Synchronized fun claim(id: Long): Boolean = writableDatabase.update("sessions", ContentValues().apply { put("prompt", "claimed") },
        "id=? AND complete=1 AND prompt='pending' AND name IS NULL", arrayOf(id.toString())) == 1
}
