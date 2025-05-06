package com.example.try2

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log

class MyApplication : Application() {

    private var activeRoomActivityCount = 0
    private var lastRoomId: String? = null
    private var lastUserId: String? = null
    private var isSessionClosed = false

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                if (activity is RoomActivity || activity is MovieSearchActivity || activity is ChooseActivity) {
                    if (activeRoomActivityCount == 0) {
                        // Сбрасываем флаг при старте новой сессии комнаты
                        isSessionClosed = false
                    }
                    activeRoomActivityCount++
                    val roomId = activity.intent.getStringExtra("room_id")
                    val userId = UserManager.getUserId(activity)
                    if (roomId != null && userId != null) {
                        lastRoomId = roomId
                        lastUserId = userId
                    }
                    Log.d("MyApplication", "Activity started: ${activity.javaClass.simpleName}, count: $activeRoomActivityCount, roomId: $roomId, userId: $userId")
                } else if (activity is MainActivity && activeRoomActivityCount > 0 && !isSessionClosed) {
                    Log.d("MyApplication", "MainActivity started, closing room session")
                    closeRoomSession(activity)
                }
            }

            override fun onActivityResumed(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                if (activity is RoomActivity || activity is MovieSearchActivity || activity is ChooseActivity) {
                    activeRoomActivityCount = maxOf(0, activeRoomActivityCount - 1)
                    Log.d("MyApplication", "Activity stopped: ${activity.javaClass.simpleName}, count: $activeRoomActivityCount")
                    if (activeRoomActivityCount == 0 && !isSessionClosed) {
                        Log.d("MyApplication", "No active room activities, closing session")
                        closeRoomSession(activity)
                    }
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun closeRoomSession(activity: Activity) {
        if (isSessionClosed) {
            Log.d("MyApplication", "Session already closed, skipping")
            return
        }
        lastRoomId?.let { roomId ->
            lastUserId?.let { userId ->
                Log.d("MyApplication", "Setting offline for user=$userId, room=$roomId")
                UserSessionManager.updateOnlineStatus(activity, roomId, userId, isOnline = false)
            } ?: Log.w("MyApplication", "lastUserId is null, cannot update status")
        } ?: Log.w("MyApplication", "lastRoomId is null, cannot update status")
        activeRoomActivityCount = 0
        lastRoomId = null
        lastUserId = null
        isSessionClosed = true
    }
}