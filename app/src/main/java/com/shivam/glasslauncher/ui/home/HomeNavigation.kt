package com.shivam.glasslauncher.ui.home

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** In-process HOME delivery only while the launcher is resumed; external apps use system HOME. */
object HomeNavigation {
    private val events = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val requests = events.asSharedFlow()

    fun requestWhileResumed(): Boolean =
        events.subscriptionCount.value > 0 && events.tryEmit(Unit)
}
