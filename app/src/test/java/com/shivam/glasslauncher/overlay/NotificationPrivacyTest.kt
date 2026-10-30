package com.shivam.glasslauncher.overlay

import com.shivam.glasslauncher.overlay.notifications.*
import org.junit.Assert.*
import org.junit.Test

class NotificationPrivacyTest {
    private fun entry(key: String, visibility: Int = 0) = NotifEntry(
        key, "app", "Messages", "Private sender", "Private message", 1,
        subText = "Private account", actions = listOf(NotifAction(0, "Reply", true)), visibility = visibility,
    )

    @Test fun lockScreenOmitsSecretAndMediaNotifications() {
        assertEquals(listOf("public", "private"), lockScreenEntries(listOf(
            entry("secret", -1), entry("public", 1), entry("private"), entry("media").copy(isMedia = true),
        )).map { it.key })
    }

    @Test fun lockedCardsNeverContainPrivateTextOrActions() {
        val redacted = lockScreenEntries(listOf(entry("a").copy(progress = 45, progressMax = 100))).single()
        assertEquals("Messages", redacted.title)
        assertEquals("Unlock to view notification", redacted.text)
        assertEquals("", redacted.subText)
        assertTrue(redacted.actions.isEmpty())
        assertEquals(0, redacted.progressMax)
    }

    @Test fun lockScreenFollowsAndroidSensitiveContentRules() {
        val noLock = LockPrivacy(deviceSecure = false, allowPrivate = false)
        val showSensitive = LockPrivacy(deviceSecure = true, allowPrivate = true)
        val hideSensitive = LockPrivacy(deviceSecure = true, allowPrivate = false)
        // No PIN: nothing to protect, even secret notifications show.
        assertEquals("Private message", lockScreenEntries(listOf(entry("s", -1)), noLock).single().text)
        // PIN + "show sensitive content": private text shows, secret still hidden, actions wait for unlock.
        val shown = lockScreenEntries(listOf(entry("p"), entry("s", -1)), showSensitive)
        assertEquals(listOf("p"), shown.map { it.key })
        assertEquals("Private message", shown.single().text)
        assertTrue(shown.single().actions.isEmpty())
        // PIN + hide sensitive: the app's public version, else redacted; public notifications stay readable.
        val pub = entry("v").copy(publicTitle = "WhatsApp", publicText = "2 new messages")
        assertEquals("2 new messages", lockScreenEntries(listOf(pub), hideSensitive).single().text)
        assertEquals(LOCK_REDACTED_TEXT, lockScreenEntries(listOf(entry("r")), hideSensitive).single().text)
        assertEquals("Private message", lockScreenEntries(listOf(entry("u", 1)), hideSensitive).single().text)
        // An app locked in Glass stays redacted even when sensitive content is allowed.
        assertEquals(LOCK_REDACTED_TEXT, lockScreenEntries(listOf(entry("g", 1)), showSensitive.copy(lockedPackages = setOf("app"))).single().text)
    }

    @Test fun filtersPartitionByAndroidChannelImportance() {
        val entries = (0..5).map { entry("$it").copy(importance = it) }
        assertEquals(listOf(3, 4, 5), NotificationFilter.ALERTING.select(entries).map { it.importance })
        assertEquals(listOf(0, 1, 2), NotificationFilter.QUIET.select(entries).map { it.importance })
        assertEquals(entries, NotificationFilter.ALL.select(entries))
    }

    @Test fun systemHiddenNotificationsDoNotEnableGlassCards() {
        assertFalse(OverlaySettings().lockScreenCards)
        assertFalse(shouldShowLockCards(false, false, true, true))
        assertTrue(shouldShowLockCards(true, false, true, true))
        assertFalse(shouldShowLockCards(true, true, true, true))
        assertFalse(shouldShowLockCards(true, false, false, true))
        assertFalse(shouldShowLockCards(true, false, true, false))
    }

    @Test fun channelAndRankingPrivacyOverridesCannotBeRelaxedByNotification() {
        assertEquals(-1, effectiveLockScreenVisibility(1, -1, 1))
        assertEquals(-1, effectiveLockScreenVisibility(1, -1000, -1))
        assertEquals(-1, effectiveLockScreenVisibility(-1, 1, 1))
        assertEquals(0, effectiveLockScreenVisibility(0, -1000, -1000))
        assertEquals(1, effectiveLockScreenVisibility(1, null, null))
        val secretChannel = entry("secret-channel", effectiveLockScreenVisibility(1, null, -1))
        assertTrue(lockScreenEntries(listOf(secretChannel)).isEmpty())
    }
}
