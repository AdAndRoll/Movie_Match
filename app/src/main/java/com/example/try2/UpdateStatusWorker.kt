package com.example.try2

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateStatusWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val roomId = inputData.getString("room_id")
        val userId = inputData.getString("user_id")

        if (roomId == null || userId == null) {
            Log.w("UpdateStatusWorker", "RoomId or UserId is null, cannot update status")
            return Result.failure()
        }

        return try {
            withContext(Dispatchers.IO) {
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
            Log.d("UpdateStatusWorker", "Successfully set offline status for user=$userId, room=$roomId")
            Result.success()
        } catch (e: Exception) {
            Log.e("UpdateStatusWorker", "Failed to set offline status: ${e.message}", e)
            Result.retry() // Повторяем задачу при ошибке (например, если нет сети)
        }
    }
}