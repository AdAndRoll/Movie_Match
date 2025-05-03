package com.example.try2

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable

object Supabase {
    private const val SUPABASE_URL = "https://zoqzfbefapkgvxycchuz.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpvcXpmYmVmYXBrZ3Z4eWNjaHV6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDUzNDMwODcsImV4cCI6MjA2MDkxOTA4N30.mbDRr1VHcALMhF8X-xx-0ONVwWO2RIfGExKTZZr1APQ"

    lateinit var client: SupabaseClient

    fun initialize(context: Context) {
        client = createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Postgrest)
            install(Realtime)

            httpEngine = OkHttp.create {
                config {
                    connectTimeout(30_000, TimeUnit.MILLISECONDS)
                    readTimeout(30_000, TimeUnit.MILLISECONDS)
                }
            }
        }
    }
}

object UserManager {
    private const val PREFS_NAME = "user_prefs"
    private const val USER_ID_KEY = "user_id"
    private const val LAST_ROOM_ID_KEY = "last_room_id"
    private const val LAST_ROOM_CODE_KEY = "last_room_code"
    fun getUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(USER_ID_KEY, null) ?: run {
            val newId = "user_${System.currentTimeMillis()}_${(1000..9999).random()}"
            prefs.edit().putString(USER_ID_KEY, newId).apply()
            newId
        }
    }
    fun saveLastRoom(context: Context, roomId: String, roomCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(LAST_ROOM_ID_KEY, roomId)
            .putString(LAST_ROOM_CODE_KEY, roomCode)
            .apply()
    }

    fun getLastRoom(context: Context): Pair<String?, String?> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(LAST_ROOM_ID_KEY, null) to
                prefs.getString(LAST_ROOM_CODE_KEY, null)
    }

    fun clearLastRoom(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(LAST_ROOM_ID_KEY)
            .remove(LAST_ROOM_CODE_KEY)
            .apply()
    }
}

@Serializable
data class Room(
    val id: String,
    val code: String,
    val status: String,
    val created_at: String

)

@Serializable
data class RoomInsert(
    val code: String,
    val status: String
)

@Serializable
data class UserSession(
    val user_id: String,
    val room_id: String,
    val is_online: Boolean,
    val last_active: String,
    val is_ready: Boolean = false // Новое поле
)
