package com.flashloop.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    Scheduler.ACTION_FIRE -> Scheduler.onAlarm(appContext)
                    Scheduler.ACTION_SNOOZE ->
                        Scheduler.snooze(appContext, intent.getIntExtra(Scheduler.EXTRA_MINUTES, 5))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
