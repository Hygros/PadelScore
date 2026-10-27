package ch.hygro.padelscore.sync

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class PermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = TextView(this).apply {
            text = getString(R.string.permissions_rationale)
            textSize = 17f
            setPadding(32, 64, 32, 32)
        }
        setContentView(android.widget.ScrollView(this).apply { addView(content) })
    }
}
