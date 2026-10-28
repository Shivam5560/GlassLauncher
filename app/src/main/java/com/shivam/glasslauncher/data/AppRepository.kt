package com.shivam.glasslauncher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Collator

/**
 * Source of truth for installed launchable apps across all profiles. Re-queries
 * [LauncherApps] whenever a package is added, removed or changed.
 */
class AppRepository(private val context: Context, private val scope: CoroutineScope) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    /** Bumped when a package's resources change (update), so icon caches can be invalidated. */
    private val _packageChanges = MutableStateFlow(0L)
    val packageChanges: StateFlow<Long> = _packageChanges.asStateFlow()
    var lastChangedPackage: String? = null
        private set

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed(null)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed(null)
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = changed(null)
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = changed(null)
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    private fun changed(pkg: String?) {
        lastChangedPackage = pkg
        _packageChanges.value = _packageChanges.value + 1
        refresh()
    }

    fun refresh() {
        scope.launch(Dispatchers.Default) {
            _apps.value = load()
        }
    }

    private fun load(): List<AppEntry> {
        val me = Process.myUserHandle()
        val collator = Collator.getInstance()
        val list = ArrayList<AppEntry>()
        for (user in launcherApps.profiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val isWork = user != me
            val activities = try {
                launcherApps.getActivityList(null, user)
            } catch (e: SecurityException) {
                emptyList()
            }
            for (info in activities) {
                if (info.componentName.packageName == context.packageName && info.componentName.className.endsWith("MainActivity")) continue
                list += AppEntry(
                    key = AppEntry.keyOf(info.componentName, if (isWork) serial else 0L),
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
                    component = info.componentName,
                    user = user,
                    isWorkProfile = isWork,
                    category = AppCategory.of(info.applicationInfo),
                    info = info,
                )
            }
        }
        return list.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun stop() {
        launcherApps.unregisterCallback(callback)
    }
}
