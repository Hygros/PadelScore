package ch.hygro.padelscore.presentation

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle

/** System entry point for the app-owned automatic DND rule. */
class MatchQuietModeSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertDialog.Builder(this)
            .setTitle("Padel Score match")
            .setMessage("This rule silences calls and notifications while a match is open in Padel Score. Alarms remain allowed. It stops when the match ends, you return home, or you leave the app. You can disable or delete the rule in your watch settings. If the app crashes, reopen it to clear the rule or disable it manually.")
            .setPositiveButton("OK") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
