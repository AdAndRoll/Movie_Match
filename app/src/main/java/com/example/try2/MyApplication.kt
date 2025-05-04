package com.example.try2

import android.app.Activity
import android.app.Application
import android.os.Bundle

class MyApplication : Application() {

    private var activeRoomActivityCount = 0
    private var lastRoomId: String? = null
    private var lastUserId: String? = null

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                if (activity is RoomActivity || activity is MovieSearchActivity || activity is ChooseActivity) {
                    activeRoomActivityCount++
                    lastRoomId = activity.intent.getStringExtra("ROOM_ID")
                    lastUserId = UserManager.getUserId(activity)
                }
            }

            override fun onActivityResumed(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                if (activity is RoomActivity || activity is MovieSearchActivity || activity is ChooseActivity) {
                    activeRoomActivityCount--
                    if (activeRoomActivityCount <= 0) {
                        // Пользователь покинул все активности, связанные с комнатой
                        lastRoomId?.let { roomId ->
                            lastUserId?.let { userId ->
                                UserSessionManager.updateOnlineStatus(activity, roomId, userId, isOnline = false)
                            }
                        }
                    }
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}