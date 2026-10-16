package com.example.photosessions

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

enum class PhotoAccess { FULL, LIMITED, NONE }
object Permissions {
    fun access(context: Context): PhotoAccess {
        fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES) -> PhotoAccess.FULL
            Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> PhotoAccess.FULL
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PhotoAccess.LIMITED
            else -> PhotoAccess.NONE
        }
    }
    fun photoPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

class MediaDiscovery(private val context: Context) {
    fun scan(baseline: Long, now: Long, screenshotsSince: Long): List<Photo> {
        check(Permissions.access(context) != PhotoAccess.NONE)
        val photos = mutableListOf<Photo>()
        // Query each real volume: IDs are only unique within a volume.
        for (volume in MediaStore.getExternalVolumeNames(context).sorted()) {
            val collection = MediaStore.Images.Media.getContentUri(volume)
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.RELATIVE_PATH)
            val selection = "(${MediaStore.Images.Media.DATE_TAKEN} >= ? OR ${MediaStore.Images.Media.DATE_ADDED} >= ?) AND ${MediaStore.Images.Media.IS_PENDING}=0" +
                if (Build.VERSION.SDK_INT >= 30) " AND ${MediaStore.Images.Media.IS_TRASHED}=0" else ""
            val cursor = context.contentResolver.query(collection, projection, selection,
                arrayOf(baseline.toString(), (baseline / 1000 - 1).toString()), null)
                ?: throw java.io.IOException("Media catalog temporarily unavailable")
            cursor.use { c ->
                while(c.moveToNext()) {
                    val id = c.getLong(0)
                    val taken = c.getLong(1)
                    val added = c.getLong(2)
                    val time = SessionRules.discoveryTime(c.getString(3) ?: "", taken, added, baseline, screenshotsSince, now) ?: continue
                    photos += Photo("$volume:$id:$added", ContentUris.withAppendedId(collection, id).toString(), time)
                }
            }
        }
        // Never advance a high-water cutoff: re-read all metadata since setup to catch late indexing.
        return photos
    }
}
