package com.shivam.glasslauncher.ui.home

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeNavigationTest {
    @Test fun homeOnlyGoesToActiveLauncherAndDoesNotReplayOnResume() = runBlocking {
        // No resumed launcher: the service must fall back to the system HOME action.
        assertFalse(HomeNavigation.requestWhileResumed())
        val firstResume = async(start = CoroutineStart.UNDISPATCHED) { HomeNavigation.requests.first() }
        try {
            assertTrue(HomeNavigation.requestWhileResumed())
            withTimeout(1_000) { firstResume.await() }
        } finally {
            firstResume.cancelAndJoin()
        }
        assertFalse(HomeNavigation.requestWhileResumed())

        // Returning from another app must not replay a HOME press from the previous session.
        val nextResume = async(start = CoroutineStart.UNDISPATCHED) { HomeNavigation.requests.first() }
        try {
            assertFalse(nextResume.isCompleted)
            assertTrue(HomeNavigation.requestWhileResumed())
            withTimeout(1_000) { nextResume.await() }
        } finally {
            nextResume.cancelAndJoin()
        }
    }
}
