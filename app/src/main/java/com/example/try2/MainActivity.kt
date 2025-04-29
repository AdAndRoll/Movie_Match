package com.example.try2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Returning
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class MainActivity : AppCompatActivity() {

    private lateinit var loadingDialog: AlertDialog
    private lateinit var reconnectButton: Button
    private var roomCheckJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        Supabase.initialize(applicationContext)
        initLoadingDialog()
        setupUI()
        startRoomChecker()
    }

    private fun initLoadingDialog() {
        loadingDialog = AlertDialog.Builder(this)
            .setView(layoutInflater.inflate(R.layout.dialog_loading, null))
            .setCancelable(false)
            .create()
    }

    private fun setupUI() {
        reconnectButton = findViewById(R.id.reconnectButton)
        findViewById<Button>(R.id.CreateRoomButton).setOnClickListener { createRoom() }
        findViewById<Button>(R.id.JoinButton).setOnClickListener { showJoinDialog() }

        reconnectButton.setOnClickListener {
            UserManager.getLastRoom(this).let { (id, code) ->
                if (id != null && code != null) {
                    attemptReconnect(id, code)
                }
            }
        }
    }

    private fun startRoomChecker() {
        roomCheckJob?.cancel()
        roomCheckJob = lifecycleScope.launch {
            while (true) {
                checkLastRoom()
                delay(30000)
            }
        }
    }

    private fun checkLastRoom() {
        UserManager.getLastRoom(this).let { (roomId, roomCode) ->

            lifecycleScope.launch {
                try {
                    val exists = Supabase.client.postgrest.from("rooms")
                        .select {
                            filter {
                                eq("id", roomId ?: "")
                            }
                        }
                        .decodeSingleOrNull<Room>() != null

                    runOnUiThread {
                        if (exists) {
                            reconnectButton.text = "Переподключиться к: $roomCode"
                            reconnectButton.visibility = View.VISIBLE
                        } else {
                            UserManager.clearLastRoom(this@MainActivity)
                            reconnectButton.visibility = View.GONE
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RoomCheck", "Ошибка проверки комнаты", e)
                }
            }
        } ?: run {
            reconnectButton.visibility = View.GONE
        }
    }

    private fun createRoom() {
        loadingDialog.show()
        lifecycleScope.launch {
            try {
                val userId = UserManager.getUserId(this@MainActivity)
                val code = generateUniqueRoomCode()

                // 1) Вставляем новую комнату (не ждём от insert никакого тела)
                Supabase.client.postgrest
                    .from("rooms")
                    .insert(listOf(RoomInsert(code = code, status = "waiting")))

                // 2) Делаем отдельный запрос, чтобы получить только что созданную комнату
                val room = Supabase.client.postgrest
                    .from("rooms")
                    .select {
                        filter {
                            eq("code", code)
                        }
                    }
                    .decodeSingle<Room>()  // или decodeList<Room>().first()

                // Дальше как и раньше
                updateUserSession(userId, room.id)
                UserManager.saveLastRoom(this@MainActivity, room.id, room.code)
                goToRoom(room.id, room.code)

            } catch (e: Exception) {
                Log.e("CreateRoomError", """
                Ошибка: ${e.message}
                ${Log.getStackTraceString(e)}
            """.trimIndent())
            } finally {
                loadingDialog.dismiss()
            }
        }
    }





    private fun showJoinDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_join_room, null)
        val etCode = dialogView.findViewById<EditText>(R.id.etCode)

        AlertDialog.Builder(this)
            .setTitle("Присоединиться к комнате")
            .setView(dialogView)
            .setPositiveButton("Подключиться") { _, _ ->
                val code = etCode.text.toString().trim()
                if (code.length == 6) {
                    joinRoom(code.uppercase())
                } else {
                    showError("Некорректный код комнаты")
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun joinRoom(code: String) {
        loadingDialog.show()
        lifecycleScope.launch {
            try {
                val room = Supabase.client.postgrest.from("rooms")
                    .select {
                        filter {
                            eq("code", code)
                        }
                    }
                    .decodeSingle<Room>()

                val userId = UserManager.getUserId(this@MainActivity)
                updateUserSession(userId, room.id)
                UserManager.saveLastRoom(this@MainActivity, room.id, room.code)
                goToRoom(room.id, room.code)

            } catch (e: Exception) {
                handleError("JoinRoomError", "Ошибка подключения", e)
            } finally {
                loadingDialog.dismiss()
            }
        }
    }

    private fun attemptReconnect(roomId: String, roomCode: String) {
        loadingDialog.show()
        lifecycleScope.launch {
            try {
                val exists = Supabase.client.postgrest.from("rooms")
                    .select {
                        filter {
                            eq("id", roomId)
                        }
                    }
                    .decodeSingleOrNull<Room>() != null

                if (exists) {
                    val userId = UserManager.getUserId(this@MainActivity)
                    updateUserSession(userId, roomId)
                    goToRoom(roomId, roomCode)
                } else {
                    showError("Комната больше не существует")
                    UserManager.clearLastRoom(this@MainActivity)
                    reconnectButton.visibility = View.GONE
                }
            } catch (e: Exception) {
                handleError("ReconnectError", "Ошибка переподключения", e)
            } finally {
                loadingDialog.dismiss()
            }
        }
    }

    private suspend fun updateUserSession(userId: String, roomId: String) {
        Supabase.client.postgrest.from("user_sessions")
            .upsert(
                listOf(
                    UserSession(
                        user_id = userId,
                        room_id = roomId,
                        is_online = true,
                        last_active = Clock.System.now().toString()
                    )
                ),
            )
    }



    private suspend fun generateUniqueRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        repeat(10) { attempt ->
            val code = List(6) { chars.random() }.joinToString("")
            try {
                val exists = Supabase.client.postgrest.from("rooms")
                    .select {
                        filter {
                            eq("code", code)
                        }
                    }
                    .decodeList<Room>()
                    .isNotEmpty()
                if (!exists) return code
            } catch (e: Exception) {
                Log.e("RoomCode", "Ошибка генерации кода", e)
                if (attempt == 9) throw e
            }
        }
        throw IllegalStateException("Не удалось создать уникальный код комнаты")
    }

    private fun goToRoom(roomId: String, code: String) {
        startActivity(Intent(this, RoomActivity::class.java).apply {
            putExtra("ROOM_ID", roomId)
            putExtra("ROOM_CODE", code)
        })
    }

    private fun handleError(tag: String, message: String, e: Exception) {
        logError(tag, e)
        showError("$message: ${e.localizedMessage ?: "Неизвестная ошибка"}")
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun logError(tag: String, e: Exception) {
        Log.e(tag, """
            Ошибка: ${e.message}
            ${Log.getStackTraceString(e)}
        """.trimIndent())
    }

    override fun onDestroy() {
        super.onDestroy()
        roomCheckJob?.cancel()
    }
}
