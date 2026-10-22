package ch.hygro.padelscore.sync

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class PermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = TextView(this).apply {
            text = "PadelScore Sync requests permission to write exercise sessions to Health Connect " +
                "only after you choose to connect. When sync is enabled on the watch, " +
                "the app can also write recorded heart-rate measurements. You can revoke access " +
                "at any time in Health Connect settings.\n\n" +
                "The watch records heart-rate samples and match steps locally only when Health Tracking is enabled. " +
                "Steps are not exported. Workout sessions and heart-rate samples are transferred through " +
                "the paired-device data layer only with Health Sync enabled on the watch. " +
                "New imports also require the phone sync switch, Health Connect write permissions, " +
                "and a live consent check with the source watch. No account or developer-operated server is used.\n\n" +
                "Deleting a match sends a durable deletion request for that match's PadelScore records. " +
                "Explicit deletion requests are processed even when sync is off. " +
                "Already imported copies in Samsung Health or other apps may require separate deletion. " +
                "Turning sync off does not delete existing data. Uninstalling the watch app before pending " +
                "deletions have been delivered can lose those deletion requests. " +
                "Incomplete transfers remain in private phone storage for retry. " +
                "This is a fitness feature, not a medical device."
            textSize = 17f
            setPadding(32, 64, 32, 32)
        }
        setContentView(android.widget.ScrollView(this).apply { addView(content) })
    }
}
