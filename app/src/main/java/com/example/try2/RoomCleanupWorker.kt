package com.example.try2

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.database.*



// Общий объект для работы с комнатами
object RoomManager {
    fun checkAndCleanRoom(roomCode: String, database: FirebaseDatabase, callback: (Boolean) -> Unit) {
        val roomRef = database.getReference("rooms/$roomCode")
        roomRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val usersData = mutableData.child("users")
                var onlineCount = 0L

                // Проходим по всем пользователям в комнате и считаем, сколько помечено как online.
                for (userSnapshot in usersData.children) {
                    val online = userSnapshot.child("online").getValue(Boolean::class.java) ?: false
                    if (online) onlineCount++
                }

                // Если ни один пользователь не онлайн, очищаем комнату и удаляем запись active_rooms.
                if (onlineCount == 0L) {
                    // Удаляем запись в active_rooms
                    database.getReference("active_rooms/$roomCode").removeValue()
                    // Удаляем данные комнаты (например, при окончательном уходе всех)
                    mutableData.value = null
                }
                // Иначе оставляем данные без изменений.
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null) {
                    Log.e("RoomManager", "Ошибка очистки комнаты: ${error.message}")
                }
                callback(committed)
            }
        })
    }
}





class RoomCleanupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val roomCode = inputData.getString("roomCode") ?: return Result.failure()
        val database = FirebaseDatabase.getInstance()

        RoomManager.checkAndCleanRoom(roomCode, database) { committed ->
            Log.d(TAG, "Фоновая проверка: ${if (committed) "успешно" else "не удалось"}")
        }

        return Result.success()
    }
}