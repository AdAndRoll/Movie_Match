package com.example.try2

import android.os.Build
import android.os.Bundle
import android.util.Log
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
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest

import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.RealtimeMessage
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow

import io.github.jan.supabase.realtime.realtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.util.Locale.filter

class RoomActivity : AppCompatActivity() {

    private lateinit var roomId: String
    private lateinit var roomCode: String
    private lateinit var usersAdapter: UserAdapter
    private lateinit var tvRoomCode: TextView
    private lateinit var btnExit: Button
    private var userSessionChannel: RealtimeChannel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_room)

        initViews()
        setupRecyclerView()
        setupButtons()
        setOnlineStatus(true)
        loadUsers()
        subscribeToUserSessionChanges()
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
                        .select {
                            filter {
                                eq("room_id", roomId)
                            }
                        }
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



    private fun subscribeToUserSessionChanges() {
        val channelName = "realtime:public:user_sessions:room_id=$roomId"

        // Создаем канал без params, так как они не поддерживаются в 2.2.1
        val channel = Supabase.client.realtime.channel(channelName)

        lifecycleScope.launch {
            try {
                // Подписываемся на канал
                channel.subscribe()
                Log.d("RoomActivity", "Subscribed to $channelName")

                // Обрабатываем изменения в таблице user_sessions
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    channel.postgresChangeFlow<PostgresAction>(schema = "public").collect { action ->
                        Log.d("RoomActivity", "Realtime event: ${action::class.simpleName}")

                        // Проверяем, что событие относится к текущей комнате
                        val record = when (action) {
                            is PostgresAction.Insert -> action.record
                            is PostgresAction.Update -> action.record
                            is PostgresAction.Delete -> action.oldRecord
                            else -> null
                        }

                        val eventRoomId = record?.jsonObject?.get("room_id")?.toString()?.trim('"')
                        if (eventRoomId != roomId) return@collect // Игнорируем события из других комнат

                        when (action) {
                            is PostgresAction.Insert -> {
                                // Десериализуем JSON в UserSession
                                val newSession = Json.decodeFromString<UserSession>(action.record.toString())
                                val currentList = usersAdapter.currentList.toMutableList()
                                if (!currentList.any { session -> session.user_id == newSession.user_id }) {
                                    currentList.add(newSession)
                                    usersAdapter.submitList(currentList)
                                    Log.d("RoomActivity", "Добавлен пользователь: ${newSession.user_id}")
                                }
                            }
                            is PostgresAction.Update -> {
                                val updatedSession = Json.decodeFromString<UserSession>(action.record.toString())
                                val currentList = usersAdapter.currentList.toMutableList()
                                val index = currentList.indexOfFirst { session -> session.user_id == updatedSession.user_id }
                                if (index != -1) {
                                    currentList[index] = updatedSession
                                    usersAdapter.submitList(currentList)
                                    Log.d("RoomActivity", "Обновлен пользователь: ${updatedSession.user_id}")
                                }
                            }
                            is PostgresAction.Delete -> {
                                val deletedSession = Json.decodeFromString<UserSession>(action.oldRecord.toString())
                                val currentList = usersAdapter.currentList.toMutableList()
                                currentList.removeAll { session -> session.user_id == deletedSession.user_id }
                                usersAdapter.submitList(currentList)
                                Log.d("RoomActivity", "Удален пользователь: ${deletedSession.user_id}")
                            }
                            else -> {
                                // Игнорируем неподдерживаемые действия
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

        // Сохраняем канал для отписки в onDestroy
        userSessionChannel = channel
    }



    @OptIn(SupabaseInternal::class)
    private fun handleRealtimeMessage(message: RealtimeMessage) {
        val payload = message.payload as? Map<*, *> ?: return
        val newData = payload["new"] as? Map<*, *>
        val oldData = payload["old"] as? Map<*, *>

        val target = newData ?: oldData
        val eventRoomId = target?.get("room_id")?.toString()

        if (eventRoomId == roomId) {
            Log.d("RoomActivity", "Изменения в user_sessions, обновляем пользователей")
            loadUsers()
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
