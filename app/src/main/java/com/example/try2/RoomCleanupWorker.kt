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
                val users = mutableData.child("users")
                var hasOnline = false

                users.children.forEach { user ->
                    if (user.child("online").getValue(Boolean::class.java) == true) {
                        hasOnline = true
                        return@forEach
                    }
                }

                if (!hasOnline) {
                    database.getReference("active_rooms/$roomCode").removeValue()
                    mutableData.value = null
                }

                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
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