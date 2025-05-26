package com.example.try2

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class MyApplication : Application() {

    private var activeRoomActivityCount = 0
    private var lastRoomId: String? = null
    private var lastUserId: String? = null
    private var isSessionClosed = false
    private val lock = Any()
    private var isMainActivityActive = false

    override fun onCreate() {
        super.onCreate()
        Supabase.initialize(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                Log.d("MyApplication", "onActivityCreated: ${activity.javaClass.simpleName}")
                if (activity is MainActivity && (savedInstanceState == null || activity.intent.getBooleanExtra("RESET_SESSION", false))) {
                    isMainActivityActive = true
                    Log.d("MyApplication", "MainActivity created, checking session reset")
                    if (activeRoomActivityCount > 0 && !isSessionClosed) {
                        Log.d("MyApplication", "Closing session due to MainActivity reset")
                        closeRoomSession()
                    }
                }
            }

            override fun onActivityStarted(activity: Activity) {
                if (activity is RoomActivity || activity is MovieSearchActivity || activity is ChooseActivity) {
                    if (activeRoomActivityCount == 0) {
                        isSessionClosed = false
                    }
                    activeRoomActivityCount++
                    val roomId = activity.intent.getStringExtra("ROOM_ID")
                    val userId = UserManager.getUserId(activity)
                    Log.d("MyApplication", "Extracted roomId: $roomId, userId: $userId from intent: ${activity.intent.extras?.keySet()}")
                    if (roomId != null && userId != null) {
                        lastRoomId = roomId
                        lastUserId = userId
                    } else {
                        Log.w("MyApplication", "Failed to extract roomId or userId: roomId=$roomId, userId=$userId")
                    }
                    Log.d("MyApplication", "Activity started: ${activity.javaClass.simpleName}, count: $activeRoomActivityCount, lastRoomId: $lastRoomId, lastUserId: $lastUserId")
                } else if (activity is MainActivity) {
                    isMainActivityActive = true
                    Log.d("MyApplication", "MainActivity started")
                }
            }

            override fun onActivityResumed(activity: Activity) {
                Log.d("MyApplication", "onActivityResumed: ${activity.javaClass.simpleName}")
            }

            override fun onActivityPaused(activity: Activity) {
                Log.d("MyApplication", "onActivityPaused: ${activity.javaClass.simpleName}")
                if (activity is MainActivity) {
                    isMainActivityActive = false
                    Log.d("MyApplication", "MainActivity paused")
                }
            }

            override fun onActivityStopped(activity: Activity) {
                if (activity is RoomActivity || activity is MovieSearchActivity || activity is ChooseActivity) {
                    activeRoomActivityCount = maxOf(0, activeRoomActivityCount - 1)
                    Log.d("MyApplication", "Activity stopped: ${activity.javaClass.simpleName}, count: $activeRoomActivityCount")
                    if (activeRoomActivityCount == 0 && !isSessionClosed && !isMainActivityActive) {
                        Log.d("MyApplication", "All room activities stopped and no MainActivity active, scheduling session close")
                        runBlocking {
                            delay(100) // Даём 100 мс для запуска новой активности
                            if (activeRoomActivityCount == 0 && !isSessionClosed && !isMainActivityActive) {
                                Log.d("MyApplication", "Confirmed: closing session")
                                closeRoomSession()
                            } else {
                                Log.d("MyApplication", "Session close cancelled: count=$activeRoomActivityCount, isMainActivityActive=$isMainActivityActive")
                            }
                        }
                    }
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                Log.d("MyApplication", "onActivityDestroyed: ${activity.javaClass.simpleName}")
            }
        })
    }

    private fun closeRoomSession() {
        synchronized(lock) {
            if (isSessionClosed) {
                Log.d("MyApplication", "Session already closed, skipping")
                return
            }
            lastRoomId?.let { roomId ->
                lastUserId?.let { userId ->
                    Log.d("MyApplication", "Setting offline for user=$userId, room=$roomId")
                    runBlocking {
                        try {
                            withContext(Dispatchers.IO) {
                                Thread.sleep(300)
                                Supabase.client.from("user_sessions")
                                    .update(
                                        mapOf("is_online" to false)
                                    ) {
                                        filter {
                                            eq("user_id", userId)
                                            eq("room_id", roomId)
                                        }
                                    }
                            }
                            Log.d("MyApplication", "Successfully set offline status for user=$userId, room=$roomId")
                        } catch (e: Exception) {
                            Log.e("MyApplication", "Failed to set offline status: ${e.message}", e)
                        }
                    }
                } ?: Log.w("MyApplication", "lastUserId is null, cannot update status")
            } ?: Log.w("MyApplication", "lastRoomId is null, cannot update status")
            isSessionClosed = true
            lastRoomId = null
            lastUserId = null
        }
    }
}