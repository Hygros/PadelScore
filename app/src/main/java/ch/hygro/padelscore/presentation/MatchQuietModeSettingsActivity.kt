package ch.hygro.padelscore.presentation

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import ch.hygro.padelscore.R

/** System entry point for the app-owned automatic DND rule. */
class MatchQuietModeSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ongoing_match_title))
            .setMessage(getString(R.string.quiet_rule_message))
            .setPositiveButton(getString(R.string.ok)) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
