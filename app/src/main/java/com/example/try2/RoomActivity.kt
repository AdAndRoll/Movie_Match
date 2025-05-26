package com.example.try2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoomActivity : AppCompatActivity() {

    private lateinit var roomId: String
    private lateinit var roomCode: String
    private lateinit var usersAdapter: UserAdapter
    private lateinit var tvRoomCode: TextView
    private lateinit var btnExit: Button
    private lateinit var btnReady: Button
    private var userSessionChannel: RealtimeChannel? = null
    private var isCountdownStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_room)

        initViews()
        setupRecyclerView()
        setupButtons()
        loadUsers()
        startPeriodicUserCheck()
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
        btnReady = findViewById<Button>(R.id.btnReady).apply {
            setOnClickListener {
                lifecycleScope.launch {
                    try {
                        val userId = UserManager.getUserId(this@RoomActivity)
                        withContext(Dispatchers.IO) {
                            Supabase.client.from("user_sessions")
                                .update(
                                    mapOf("is_ready" to true)
                                ) {
                                    filter {
                                        eq("user_id", userId)
                                        eq("room_id", roomId)
                                    }
                                }
                        }
                        Log.d("RoomActivity", "Пользователь $userId готов")
                        isEnabled = false
                    } catch (e: Exception) {
                        Log.e("RoomActivity", "Ошибка обновления is_ready", e)
                    }
                }
            }
        }
    }

    private fun exitRoom() {
        finish()
    }

    private fun loadUsers() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    Supabase.client.from("user_sessions")
                        .select {
                            filter {
                                eq("room_id", roomId)
                                eq("is_online", true)
                            }
                        }
                        .decodeList<UserSession>()
                }
                usersAdapter.submitList(result)
                Log.d("RoomActivity", "Загружено пользователей: ${result.size}")
                if (!isCountdownStarted && result.isNotEmpty() && result.all { it.is_ready }) {
                    isCountdownStarted = true
                    startCountdown()
                }
            } catch (e: Exception) {
                Log.e("RoomActivity", "Ошибка загрузки пользователей", e)
            }
        }
    }



    private fun startPeriodicUserCheck() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    Log.d("RoomActivity", "Периодическая проверка пользователей")
                    loadUsers()
                    delay(2_000)
                }
            }
        }
    }

    private fun startCountdown() {
        lifecycleScope.launch {
            val countdownTextView = findViewById<TextView>(R.id.countdownTextView)
            countdownTextView.visibility = View.VISIBLE
            for (i in 5 downTo 0) {
                countdownTextView.text = if (i > 0) "Переход через $i..." else "Переходим!"
                Log.d("RoomActivity", "Отсчет: $i")
                delay(1_000)
            }
            countdownTextView.visibility = View.GONE
            val intent = Intent(this@RoomActivity, MovieSearchActivity::class.java).apply {
                putExtra("ROOM_ID", roomId) // Унифицируем ключ на "ROOM_ID"
            }
            startActivity(intent)
            finish()
        }
    }
    override fun onBackPressed() {
        super.onBackPressed()
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("RESET_SESSION", true) // Устанавливаем флаг для сброса сессии
        }
        startActivity(intent)
        finish()
    }
    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                userSessionChannel?.unsubscribe()
            }
        }
    }
}