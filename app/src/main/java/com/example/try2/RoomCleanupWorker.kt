package com.example.try2

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.database.*



object RoomManager {
    fun checkAndCleanRoom(roomCode: String, database: FirebaseDatabase, callback: (Boolean) -> Unit) {
        val roomRef = database.getReference("rooms/$roomCode")
        val activeRoomsRef = database.getReference("active_rooms/$roomCode")

        roomRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                // 1. Добавляем проверку существования комнаты
                if (!mutableData.hasChildren()) {
                    return Transaction.abort()
                }

                // 2. Улучшенная проверка online-статуса
                var onlineCount = 0
                mutableData.child("users").children.forEach { user ->
                    if (user.child("online").getValue(Boolean::class.java) == true) {
                        onlineCount++
                    }
                }


                // 3. Удаляем только при полном отсутствии онлайн
                return if (onlineCount == 0) {
                    activeRoomsRef.removeValue()
                    mutableData.value = null
                    Transaction.success(mutableData)
                } else {
                    // 4. Явно сохраняем active_rooms
                    activeRoomsRef.setValue(true)
                    Transaction.abort()
                }
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                // 5. Логируем результат
                Log.d("RoomManager", "Transaction result: ${committed}, Error: ${error?.message}")
                callback(committed)
            }
        })
    }
}




class RoomCleanupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val roomCode = inputData.getString("roomCode") ?: return Result.failure()
        val database = FirebaseDatabase.getInstance()

        database.getReference("rooms/$roomCode").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return

                    RoomManager.checkAndCleanRoom(roomCode, database) { committed ->
                        if (committed) {
                            database.getReference("active_rooms/$roomCode").removeValue()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Worker room check cancelled")
                }
            })

        return Result.success()
    }
}