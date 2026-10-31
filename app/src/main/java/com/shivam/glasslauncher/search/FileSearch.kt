package com.shivam.glasslauncher.search

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings

data class FileResult(
    val uri: Uri,
    val name: String,
    val mime: String?,
    /** Folder relative to the storage root, e.g. "Download/" or "Documents/Bills/". */
    val folder: String,
    val size: Long,
    /** Last modified, epoch millis. */
    val modified: Long,
)

/**
 * Spotlight file search over the system media index (MediaStore), so nothing is crawled or cached:
 * the index already covers every file on shared storage. Needs "All files access" to see documents,
 * APKs and other non-media files; without it [query] returns nothing.
 */
object FileSearch {

    fun hasAccess(): Boolean = Environment.isExternalStorageManager()

    fun accessIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Files whose name contains every word of [query], best match first. The database narrows by
     * name (newest first, capped), then [rank] orders by match quality and recency.
     */
    fun query(context: Context, query: String, limit: Int = 5, cancel: CancellationSignal? = null): List<FileResult> {
        if (!hasAccess()) return emptyList()
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(4)
        if (words.isEmpty()) return emptyList()

        val selection = buildString {
            append("${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL")
            // App-private data, trash-like caches and hidden files/folders are noise here.
            append(" AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} NOT LIKE 'Android/%'")
            append(" AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} NOT LIKE '.%'")
            append(" AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} NOT LIKE '%/.%'")
            append(" AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} NOT LIKE '.%'")
            words.forEach { _ -> append(" AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'") }
        }
        val args = words.map { "%" + it.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%" }.toTypedArray()
        val bundle = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            // ponytail: newest 80 name matches are ranked; a better-named but older file past 80 is missed.
            putInt(ContentResolver.QUERY_ARG_LIMIT, 80)
        }
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
        val found = ArrayList<FileResult>()
        try {
            context.contentResolver.query(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), projection, bundle, cancel)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    found += FileResult(
                        uri = ContentUris.withAppendedId(contentUriFor(c.getInt(6)), id),
                        name = c.getString(1) ?: continue,
                        mime = c.getString(2),
                        folder = c.getString(3).orEmpty(),
                        size = c.getLong(4),
                        modified = c.getLong(5) * 1000,
                    )
                }
            }
        } catch (e: android.os.OperationCanceledException) {
            throw kotlinx.coroutines.CancellationException("file query cancelled")
        } catch (e: Exception) {
            return emptyList()
        }
        return rank(query, found, System.currentTimeMillis(), { it.name }, { it.modified }).take(limit)
    }

    /** Best name match first (a word-start beats a mid-word hit); among equals, the newest. */
    fun <T> rank(query: String, files: List<T>, now: Long, name: (T) -> String, modified: (T) -> Long): List<T> {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return files
            .map { f ->
                // The extension stays searchable ("pdf"), and ranks as a word start like any other.
                val target = FuzzyMatcher.Target(name(f))
                val match = words.minOf { FuzzyMatcher.score(it, target) }
                val ageDays = (now - modified(f)) / 86_400_000L
                f to match + when { ageDays < 7 -> 40; ageDays < 30 -> 20; else -> 0 }
            }
            .sortedWith(compareByDescending<Pair<T, Int>> { it.second }.thenByDescending { modified(it.first) })
            .map { it.first }
    }

    /** The typed content URI lets viewers (Photos, players) open media; the generic files URI covers the rest. */
    private fun contentUriFor(mediaType: Int): Uri = when (mediaType) {
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    }

    fun openIntent(f: FileResult, anyType: Boolean = false): Intent =
        Intent(Intent.ACTION_VIEW).setDataAndType(f.uri, if (anyType) "*/*" else f.mime ?: "*/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
