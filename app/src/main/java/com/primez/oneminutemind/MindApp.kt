package com.primez.oneminutemind

import android.app.Application
import com.primez.oneminutemind.notify.Reminder

class MindApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Reminder.createChannel(this)
    }
}
