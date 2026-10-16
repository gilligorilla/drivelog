package com.example.drivelog

import android.app.Application
import com.example.drivelog.session.PileSession

class DriveLogApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PileSession.init(this)
    }
}
