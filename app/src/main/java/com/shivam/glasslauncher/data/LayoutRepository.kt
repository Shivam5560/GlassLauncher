package com.shivam.glasslauncher.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Persists [HomeLayout] as JSON in `files/layout.json`. */
class LayoutRepository(context: Context, private val scope: CoroutineScope) {

    private val file = AtomicFile(File(context.filesDir, "layout.json"))
    private val mutex = Mutex()

    private val _layout = MutableStateFlow<HomeLayout?>(null)
    /** null until loaded; a layout with no `known` keys means it has never been populated. */
    val layout: StateFlow<HomeLayout?> = _layout.asStateFlow()

    fun load() {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                _layout.value = try {
                    if (file.baseFile.exists()) HomeLayout.fromJson(String(file.readFully())) else HomeLayout.EMPTY
                } catch (e: Exception) {
                    Log.w("GlassLayout", "Corrupt layout, starting fresh", e)
                    HomeLayout.EMPTY
                }
            }
        }
    }

    /** Applies [transform] to the current layout and saves the result. */
    fun update(transform: (HomeLayout) -> HomeLayout) {
        scope.launch(Dispatchers.Default) {
            mutex.withLock {
                val current = _layout.value ?: return@withLock
                val next = transform(current)
                if (next == current) return@withLock
                _layout.value = next
                save(next)
            }
        }
    }

    private suspend fun save(layout: HomeLayout) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val out = file.startWrite()
            try {
                out.write(layout.toJson().toByteArray())
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                Log.e("GlassLayout", "Failed to save layout", e)
            }
        }
    }
}
