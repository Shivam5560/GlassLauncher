package com.shivam.glasslauncher.widgets

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.shivam.glasslauncher.GlassApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

/**
 * Persists widget instances (kind, size, settings) and the Today view order in
 * `files/widgets.json`. Placement on home pages lives in the home layout; this store only knows
 * which instances exist. Updates apply to [state] synchronously and are saved in the background.
 */
class WidgetRepository private constructor(private val context: Context, private val scope: CoroutineScope) {

    private val file = AtomicFile(File(context.filesDir, "widgets.json"))
    private val saveLock = Mutex()
    private val _state = MutableStateFlow<WidgetState?>(null)

    /** null until loaded. */
    val state: StateFlow<WidgetState?> = _state.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val loaded = try {
                if (file.baseFile.exists()) WidgetState.fromJson(String(file.readFully())) else WidgetState()
            } catch (e: Exception) {
                Log.w(TAG, "Corrupt widget store, starting fresh", e)
                WidgetState()
            }
            _state.compareAndSet(null, loaded)
        }
    }

    fun instance(id: String): WidgetInstance? = _state.value?.instances?.get(id)

    private fun mutate(transform: (WidgetState) -> WidgetState) {
        var changed = false
        _state.update { cur ->
            val base = cur ?: return@update cur
            val next = transform(base)
            changed = next != base
            next
        }
        if (changed) save()
    }

    private fun save() {
        scope.launch(Dispatchers.IO) {
            saveLock.withLock {
                val snapshot = _state.value ?: return@withLock
                val out = file.startWrite()
                try {
                    out.write(snapshot.toJson().toByteArray())
                    file.finishWrite(out)
                } catch (e: Exception) {
                    file.failWrite(out)
                    Log.e(TAG, "Failed to save widgets", e)
                }
            }
        }
    }

    /** Creates an instance (and, for a Smart Stack, its default children) and returns it. */
    fun create(kind: WidgetKind, size: WidgetSize, config: Map<String, String> = emptyMap()): WidgetInstance {
        val id = newId(kind)
        val extra = HashMap<String, WidgetInstance>()
        var cfg = config
        if (kind == WidgetKind.SMART_STACK && WidgetKeys.STACK_CHILDREN !in config) {
            val children = listOf(WidgetKind.CALENDAR, WidgetKind.WEATHER, WidgetKind.MUSIC, WidgetKind.BATTERY).map { k ->
                WidgetInstance(newId(k), k, if (size in k.sizes) size else k.sizes.first())
            }
            children.forEach { extra[it.id] = it }
            cfg = cfg + (WidgetKeys.STACK_CHILDREN to children.joinToString(",") { it.id }) + (WidgetKeys.STACK_ROTATE to "1")
        }
        val inst = WidgetInstance(id, kind, size, cfg)
        mutate { it.copy(instances = it.instances + extra + (id to inst)) }
        return inst
    }

    fun markHomeSeeded() = mutate { it.copy(homeSeeded = true) }

    fun setConfig(id: String, key: String, value: String?) = mutate { s ->
        val inst = s.instances[id] ?: return@mutate s
        val cfg = if (value == null) inst.config - key else inst.config + (key to value)
        s.copy(instances = s.instances + (id to inst.copy(config = cfg)))
    }

    fun setSize(id: String, size: WidgetSize) = mutate { s ->
        val inst = s.instances[id] ?: return@mutate s
        // A stack's children follow its size.
        val children = inst.list(WidgetKeys.STACK_CHILDREN).mapNotNull { s.instances[it] }
            .associate { c -> c.id to c.copy(size = if (size in c.kind.sizes) size else c.kind.sizes.first()) }
        s.copy(instances = s.instances + children + (id to inst.copy(size = size)))
    }

    /** Sets the kinds a Smart Stack holds, keeping existing children of those kinds (and their settings). */
    fun setStackKinds(id: String, kinds: List<WidgetKind>) = mutate { s ->
        val inst = s.instances[id] ?: return@mutate s
        val existing = inst.list(WidgetKeys.STACK_CHILDREN).mapNotNull { s.instances[it] }
        val added = HashMap<String, WidgetInstance>()
        val children = kinds.map { k ->
            existing.firstOrNull { it.kind == k } ?: WidgetInstance(newId(k), k, if (inst.size in k.sizes) inst.size else k.sizes.first()).also { added[it.id] = it }
        }
        val removed = existing.filter { it !in children }.map { it.id }.toSet()
        removed.forEach { releaseResources(s.instances[it]) }
        val cfg = inst.config + (WidgetKeys.STACK_CHILDREN to children.joinToString(",") { it.id })
        s.copy(instances = (s.instances - removed) + added + (id to inst.copy(config = cfg)))
    }

    /** Deletes an instance, its stack children, any hosted Android widget id and its photos. */
    fun delete(id: String) = mutate { s ->
        val inst = s.instances[id] ?: return@mutate s
        val ids = setOf(id) + inst.list(WidgetKeys.STACK_CHILDREN)
        ids.forEach { releaseResources(s.instances[it]) }
        s.copy(instances = s.instances - ids, today = s.today - ids)
    }

    private fun releaseResources(inst: WidgetInstance?) {
        inst ?: return
        if (inst.kind == WidgetKind.APP_WIDGET && inst.appWidgetId >= 0) GlassWidgetHost.get(context).deleteId(inst.appWidgetId)
        if (inst.kind == WidgetKind.PHOTOS) PhotoStore.clear(context, inst.id)
    }

    fun addToToday(id: String, index: Int? = null) = mutate { s ->
        val list = (s.today - id).toMutableList()
        list.add((index ?: list.size).coerceIn(0, list.size), id)
        s.copy(today = list)
    }

    /** Removes from the Today view and deletes the instance. */
    fun removeFromToday(id: String) = delete(id)

    fun moveToday(from: Int, to: Int) = mutate { s ->
        if (from !in s.today.indices || to !in s.today.indices) return@mutate s
        val list = s.today.toMutableList()
        list.add(to, list.removeAt(from))
        s.copy(today = list)
    }

    /** First run: fill the Today view with a sensible set of widgets. */
    fun seedTodayIfNeeded() {
        val s = _state.value ?: return
        if (s.seeded) return
        if (s.today.isNotEmpty()) { mutate { it.copy(seeded = true) }; return }
        val search = create(WidgetKind.SEARCH, WidgetSize.BAR)
        val stack = create(WidgetKind.SMART_STACK, WidgetSize.MEDIUM)
        val weather = create(WidgetKind.WEATHER, WidgetSize.SMALL)
        val battery = create(WidgetKind.BATTERY, WidgetSize.SMALL)
        val music = create(WidgetKind.MUSIC, WidgetSize.MEDIUM)
        val clock = create(WidgetKind.CLOCK, WidgetSize.MEDIUM, mapOf(WidgetKeys.CLOCK_STYLE to "world"))
        val notes = create(WidgetKind.NOTES, WidgetSize.MEDIUM)
        mutate { it.copy(today = listOf(search, stack, weather, battery, music, clock, notes).map { w -> w.id }, seeded = true) }
    }

    /**
     * Deletes instances nobody references (not on a home page, not in Today, not in a stack) and
     * frees Android widget ids the host still holds for them. Run once both stores are loaded.
     */
    fun prune(homeIds: Set<String>) {
        val s = _state.value ?: return
        val referenced = HashSet<String>()
        referenced += homeIds
        referenced += s.today
        s.instances.values.filter { it.id in referenced && it.kind == WidgetKind.SMART_STACK }
            .forEach { referenced += it.list(WidgetKeys.STACK_CHILDREN) }
        val orphans = s.instances.keys - referenced
        if (orphans.isNotEmpty()) {
            orphans.forEach { releaseResources(s.instances[it]) }
            mutate { it.copy(instances = it.instances - orphans) }
        }
        val liveIds = (s.instances - orphans).values.filter { it.kind == WidgetKind.APP_WIDGET }.map { it.appWidgetId }.toSet()
        GlassWidgetHost.get(context).deleteUnused(liveIds)
    }

    private fun newId(kind: WidgetKind) = kind.name.lowercase() + "-" + UUID.randomUUID().toString().take(8)

    companion object {
        private const val TAG = "GlassWidgets"

        @Volatile private var instance: WidgetRepository? = null

        fun get(context: Context): WidgetRepository = instance ?: synchronized(this) {
            instance ?: WidgetRepository(context.applicationContext, GlassApp.from(context).scope).also { instance = it }
        }
    }
}
