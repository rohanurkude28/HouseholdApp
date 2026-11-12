package com.houseaffairs.app

import android.content.Intent
import com.houseaffairs.app.feature.add.SharedInbox
import com.houseaffairs.app.feature.add.sharedToAdd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.houseaffairs.app.ui.HouseAffairsRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        takeShare(intent)
        takeWidget(intent)
        setContent { HouseAffairsRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeShare(intent)
        takeWidget(intent)
    }

    /** A widget card was tapped: open the tab it's about once the main screen is up. */
    private fun takeWidget(intent: Intent?) {
        intent?.getStringExtra(com.houseaffairs.app.widget.WidgetInbox.EXTRA_ROUTE)?.let { com.houseaffairs.app.widget.WidgetInbox.route.value = it }
    }

    /** Text or a link shared in from another app waits in the inbox until the main screen opens it. */
    private fun takeShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        SharedInbox.pending.value = sharedToAdd(text, intent.getStringExtra(Intent.EXTRA_SUBJECT))
    }
}
