package com.laoli.hooktools

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.laoli.hooktools.util.ModuleFont

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 全局应用模块自定义字体
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                ModuleFont.applyToActivity(activity)
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
