package com.example.try2

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.FilterOperator
import kotlinx.coroutines.launch

class RoomActivity : AppCompatActivity() {

    private lateinit var roomId: String
    private lateinit var roomCode: String
    private lateinit var usersAdapter: UserAdapter
    private lateinit var tvRoomCode: TextView
    private lateinit var rvUsers: RecyclerView
    private lateinit var btnReady: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_room)

        tvRoomCode = findViewById(R.id.tvRoomCode)
        rvUsers = findViewById(R.id.rvUsers)
        btnReady = findViewById(R.id.btnReady)

        roomId = intent.getStringExtra("ROOM_ID") ?: return finish()
        roomCode = intent.getStringExtra("ROOM_CODE") ?: "------"

        tvRoomCode.text = "Код комнаты: $roomCode"

        rvUsers.layoutManager = LinearLayoutManager(this)
        usersAdapter = UserAdapter()
        rvUsers.adapter = usersAdapter
        android.util.Log.d("RoomActivity", "Получен roomId: $roomId, roomCode: $roomCode")
        btnReady.setOnClickListener {
            // пока без реализации
        }

        loadUsers()

        setOnlineStatus(true)
    }

    private fun loadUsers() {
        android.util.Log.d("RoomActivity", "Загрузка пользователей для комнаты $roomId")

        lifecycleScope.launch {
            try {
                val result = Supabase.client.postgrest["user_sessions"]
                    .select {
                        filter("room_id", FilterOperator.EQ, roomId)
                    }
                    .decodeList<UserSession>()

                android.util.Log.d("RoomActivity", "Загружено пользователей: ${result.size}")
                result.forEach { user ->
                    android.util.Log.d("RoomActivity", "Пользователь: $user")
                }

                usersAdapter.submitList(result)
            } catch (e: Exception) {
                android.util.Log.e("RoomActivity", "Ошибка загрузки пользователей", e)
            }
        }
    }


    private fun setOnlineStatus(online: Boolean) {
        lifecycleScope.launch {
            val userId = UserManager.getUserId(this@RoomActivity)
            android.util.Log.d("RoomActivity", "Установка is_online=$online для userId=$userId в комнате $roomId")

            try {
                Supabase.client.postgrest["user_sessions"]
                    .update(
                        {
                            set("is_online", online)
                        }
                    ) {
                        filter("user_id", FilterOperator.EQ, userId)
                        filter("room_id", FilterOperator.EQ, roomId)
                    }

                android.util.Log.d("RoomActivity", "is_online обновлено")
            } catch (e: Exception) {
                android.util.Log.e("RoomActivity", "Ошибка обновления is_online", e)
            }
        }
    }


    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        setOnlineStatus(false)
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        setOnlineStatus(false)
    }
}
