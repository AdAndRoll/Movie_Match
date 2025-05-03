package com.example.try2

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

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
    private lateinit var btnReady: Button // Кнопка готовности
    private var userSessionChannel: RealtimeChannel? = null
    private var isCountdownStarted = false // Флаг для предотвращения повторного отсчета

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_room)

        initViews()
        setupRecyclerView()
        setupButtons()
        setOnlineStatus(true)
        loadUsers()
        //subscribeToUserSessionChanges()

        startPeriodicUserCheck()

        //testRoomsRealtime()
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
                            Supabase.client.postgrest["user_sessions"]
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
        setOnlineStatus(false)
        finish()
    }

    private fun loadUsers() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    Supabase.client.postgrest["user_sessions"]
                        .select {
                            filter {
                                eq("room_id", roomId)
                                eq("is_online", true) // Загружаем только онлайн-пользователей
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





    private fun setOnlineStatus(online: Boolean) {
        lifecycleScope.launch {
            try {
                val userId = UserManager.getUserId(this@RoomActivity)
                withContext(Dispatchers.IO) {
                    Supabase.client.postgrest["user_sessions"]
                        .update(mapOf("is_online" to online)) {
                            filter {
                                eq("user_id", userId)
                                eq("room_id", roomId)
                            }
                        }
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
//        if (isFinishing) {
//            setOnlineStatus(false)
//        }
    }





/*    private fun subscribeToUserSessionChanges() {
        val channelName = "realtime:public:user_sessions"

        // Создаем канал
        val channel = Supabase.client.realtime.channel(channelName)

        lifecycleScope.launch {
            try {
                // Подписываемся на канал
                channel.subscribe()
                Log.d("RoomActivity", "Subscribed to $channelName")

                // Логируем состояние канала


                // Обрабатываем изменения через postgresChangeFlow
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    channel.postgresChangeFlow<PostgresAction>(schema = "public").collect { action ->
                        Log.d("RoomActivity", "Realtime event received: ${action::class.simpleName}")
                        when (action) {
                            is PostgresAction.Insert -> {
                                Log.d("RoomActivity", "Insert record: ${action.record}")
                                try {
                                    val newSession = Json.decodeFromString<UserSession>(action.record.toString())
                                    Log.d("RoomActivity", "Insert event: user_id=${newSession.user_id}, room_id=${newSession.room_id}")
                                    if (newSession.room_id == roomId) {
                                        val currentList = usersAdapter.currentList.toMutableList()
                                        if (!currentList.any { session -> session.user_id == newSession.user_id }) {
                                            currentList.add(newSession)
                                            usersAdapter.submitList(currentList)
                                            Log.d("RoomActivity", "Добавлен пользователь: ${newSession.user_id}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("RoomActivity", "Ошибка десериализации Insert: ${action.record}", e)
                                }
                            }
                            is PostgresAction.Update -> {
                                Log.d("RoomActivity", "Update record: ${action.record}")
                                try {
                                    val updatedSession = Json.decodeFromString<UserSession>(action.record.toString())
                                    Log.d("RoomActivity", "Update event: user_id=${updatedSession.user_id}, room_id=${updatedSession.room_id}")
                                    if (updatedSession.room_id == roomId) {
                                        val currentList = usersAdapter.currentList.toMutableList()
                                        val index = currentList.indexOfFirst { session -> session.user_id == updatedSession.user_id }
                                        if (index != -1) {
                                            currentList[index] = updatedSession
                                            usersAdapter.submitList(currentList)
                                            Log.d("RoomActivity", "Обновлен пользователь: ${updatedSession.user_id}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("RoomActivity", "Ошибка десериализации Update: ${action.record}", e)
                                }
                            }
                            is PostgresAction.Delete -> {
                                Log.d("RoomActivity", "Delete oldRecord: ${action.oldRecord}")
                                try {
                                    val deletedSession = Json.decodeFromString<UserSession>(action.oldRecord.toString())
                                    Log.d("RoomActivity", "Delete event: user_id=${deletedSession.user_id}, room_id=${deletedSession.room_id}")
                                    if (deletedSession.room_id == roomId) {
                                        val currentList = usersAdapter.currentList.toMutableList()
                                        currentList.removeAll { session -> session.user_id == deletedSession.user_id }
                                        usersAdapter.submitList(currentList)
                                        Log.d("RoomActivity", "Удален пользователь: ${deletedSession.user_id}")
                                    }
                                } catch (e: Exception) {
                                    Log.e("RoomActivity", "Ошибка десериализации Delete: ${action.oldRecord}", e)
                                }
                            }
                            else -> {
                                Log.d("RoomActivity", "Неподдерживаемое действие: ${action::class.simpleName}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RoomActivity", "Ошибка realtime-подписки", e)
            } finally {
                channel.unsubscribe()
                Log.d("RoomActivity", "Unsubscribed from $channelName")
            }
        }

        // Сохраняем канал для отписки
        userSessionChannel = channel
    }*/



    private fun startPeriodicUserCheck() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    Log.d("RoomActivity", "Периодическая проверка пользователей")
                    loadUsers()
                    delay(2_000) // Проверка каждые 3 секунды
                }
            }
        }
    }

/*    private fun testRoomsRealtime() {
        val channelName = "realtime:public:rooms"
        val channel = Supabase.client.realtime.channel(channelName)

        lifecycleScope.launch {
            try {
                channel.subscribe()
                Log.d("RoomActivity", "Subscribed to $channelName")


                channel.postgresChangeFlow<PostgresAction>(schema = "public").collect { action ->
                    Log.d("RoomActivity", "Rooms event received: ${action::class.simpleName}")
                    when (action) {
                        is PostgresAction.Insert -> Log.d("RoomActivity", "Rooms Insert record: ${action.record}")
                        is PostgresAction.Update -> Log.d("RoomActivity", "Rooms Update record: ${action.record}")
                        is PostgresAction.Delete -> Log.d("RoomActivity", "Rooms Delete oldRecord: ${action.oldRecord}")
                        else -> Log.d("RoomActivity", "Rooms неподдерживаемое действие: ${action::class.simpleName}")
                    }
                }
            } catch (e: Exception) {
                Log.e("RoomActivity", "Ошибка rooms realtime-подписки", e)
            }
        }
    }*/

    private fun startCountdown() {
        lifecycleScope.launch {
            val countdownTextView = findViewById<TextView>(R.id.countdownTextView)
            countdownTextView.visibility = View.VISIBLE // Делаем TextView видимым
            for (i in 5 downTo 0) {
                countdownTextView.text = if (i > 0) "Переход через $i..." else "Переходим!"
                Log.d("RoomActivity", "Отсчет: $i")
                delay(1_000)
            }
            countdownTextView.visibility = View.GONE // Скрываем после отсчета
            val intent = Intent(this@RoomActivity, MovieSearchActivity::class.java).apply {
                putExtra("room_id", roomId)
            }
            startActivity(intent)
            finish()
        }
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
