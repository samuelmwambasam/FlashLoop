package com.flashloop.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.flashloop.app.scheduling.Scheduler
import com.flashloop.app.ui.AppRoot
import com.flashloop.app.ui.FlashLoopTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var reviewRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EXTRA_OPEN_REVIEW, false) == true) reviewRequest = 1
        // Make sure the alarm exists whenever the app is opened.
        lifecycleScope.launch { Scheduler.reschedule(applicationContext) }
        setContent {
            FlashLoopTheme {
                AppRoot(reviewRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_REVIEW, false)) reviewRequest++
    }

    companion object {
        const val EXTRA_OPEN_REVIEW = "open_review"
    }
}
