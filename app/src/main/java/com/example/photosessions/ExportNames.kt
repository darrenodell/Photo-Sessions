package com.example.photosessions

import android.content.Context

/** Serializes export edits with uploads; each destination commits independently. */
class ExportNames(private val context: Context, private val db: CatalogDb,
    private val localRename: (Long) -> Unit = { SessionFiles(context, db).rename(it) },
    private val driveRename: (String, String, String) -> Unit = { account, folderId, target ->
        val token = DriveAuth.token(context, account) ?: error("Reconnect Google Drive for $account, then retry folder names.")
        try { DriveApi(token).renameFolder(folderId, target) }
        catch(e: DriveHttpException) {
            if(e.code == 401) { DriveAuth.clear(context, token); error("Reconnect Google Drive for $account, then retry folder names.") }
            throw e
        }
    }
) {
    companion object { val lock = Any() }
    fun retry(id: Long) = synchronized(lock) {
        val session = db.load().sessions.single { it.id == id }
        if(db.state("folder_$id") != null) {
            try { localRename(id) }
            catch(e: Exception) { db.put("rename_local_$id", e.message ?: "Local folder rename failed.") }
        }
        db.states("drive_folder_").filterKeys { it.endsWith("_$id") }.forEach { (key, folderId) ->
            val account = key.removePrefix("drive_folder_").removeSuffix("_$id")
            val nameKey = "drive_name_${account}_$id"
            val errorKey = "rename_drive_${account}_$id"
            val target = DriveQuery.folderName(SessionLabels.title(session))
            if(db.state(nameKey) != target || db.state(errorKey) != null) {
                db.put(errorKey, "Folder name needs updating.")
                try {
                    driveRename(account, folderId, target)
                    db.put(nameKey, target); db.remove(errorKey)
                } catch(e: Exception) { db.put(errorKey, e.message ?: "Drive folder rename failed. Check your connection and retry.") }
            }
        }
    }
    fun problems(id: Long): List<String> = listOfNotNull(db.state("rename_local_$id")?.let { "Device: $it" }) +
        db.states("rename_drive_").filterKeys { it.endsWith("_$id") }.map { (key, value) ->
            "Drive (${key.removePrefix("rename_drive_").removeSuffix("_$id")}): $value"
        }
}
