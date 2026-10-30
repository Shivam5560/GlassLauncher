package com.shivam.glasslauncher.overlay.notifications

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Invisible step between a lock-screen card and its app: asks the keyguard for fingerprint/PIN,
 * then opens the notification like a tap in the shade. Cancelling the unlock just closes it.
 */
class UnlockAndOpenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        val key = intent.getStringExtra(EXTRA_KEY) ?: return finish()
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            open(key)
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = open(key)
            override fun onDismissCancelled() = finish()
            override fun onDismissError() = finish()
        })
    }

    private fun open(key: String) {
        NotificationHub.open(this, key)
        finish()
    }

    companion object {
        private const val EXTRA_KEY = "key"

        fun start(context: Context, key: String) {
            context.startActivity(
                Intent(context, UnlockAndOpenActivity::class.java)
                    .putExtra(EXTRA_KEY, key)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        }
    }
}
