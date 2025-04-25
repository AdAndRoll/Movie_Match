package com.example.try2

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.FilterOperator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoomActivity : AppCompatActivity() {

    private lateinit var roomId: String
    private lateinit var roomCode: String
    private lateinit var usersAdapter: UserAdapter
    private lateinit var tvRoomCode: TextView
    private lateinit var btnExit: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_room)

        initViews()
        setupRecyclerView()
        setupButtons()
        setOnlineStatus(true)
        loadUsers()
    }

    private fun initViews() {
        tvRoomCode = findViewById(R.id.tvRoomCode)
        roomId = intent.getStringExtra("ROOM_ID") ?: run {
            finish()
            return
        }
        roomCode = intent.getStringExtra("ROOM_CODE") ?: "------"
        tvRoomCode.text = "Код комнаты: $roomCode"
    }

    private fun setupRecyclerView() {
        val rvUsers = findViewById<RecyclerView>(R.id.rvUsers)
        rvUsers.layoutManager = LinearLayoutManager(this)
        usersAdapter = UserAdapter()
        rvUsers.adapter = usersAdapter
    }

    private fun setupButtons() {
        btnExit = findViewById<Button>(R.id.btnExit).apply {
            setOnClickListener { exitRoom() }
        }
        findViewById<Button>(R.id.btnReady).setOnClickListener {
            // Реализация кнопки готовности
        }
    }

    private fun exitRoom() {
        setOnlineStatus(false)
        finish()
    }

    private fun loadUsers() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    Supabase.client.postgrest["user_sessions"]
                        .select { filter("room_id", FilterOperator.EQ, roomId) }
                        .decodeList<UserSession>()
                }
                usersAdapter.submitList(result)
                Log.d("RoomActivity", "Загружено пользователей: ${result.size}")
            } catch (e: Exception) {
                Log.e("RoomActivity", "Ошибка загрузки пользователей", e)
            }
        }
    }

    private fun setOnlineStatus(online: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userId = UserManager.getUserId(this@RoomActivity)
                Supabase.client.postgrest["user_sessions"].update(
                    { set("is_online", online) }
                ) {
                    filter("user_id", FilterOperator.EQ, userId)
                    filter("room_id", FilterOperator.EQ, roomId)
                }
                Log.d("RoomActivity", "Статус обновлен: is_online=$online")
            } catch (e: Exception) {
                Log.e("RoomActivity", "Ошибка обновления статуса", e)
            }
        }
    }

    override fun onBackPressed() {
        exitRoom()
        super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            setOnlineStatus(false)
        }
    }

    override fun onDestroy() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Ожидание завершения операций
            }
        }.invokeOnCompletion {
            super.onDestroy()
        }
    }
}