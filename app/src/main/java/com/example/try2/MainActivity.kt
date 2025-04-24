package com.example.try2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.FilterOperator
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class MainActivity : AppCompatActivity() {

    private lateinit var loadingDialog: AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        Supabase.initialize(applicationContext)

        loadingDialog = AlertDialog.Builder(this)
            .setView(layoutInflater.inflate(R.layout.dialog_loading, null))
            .setCancelable(false)
            .create()

        val createButton: Button = findViewById(R.id.CreateRoomButton)
        val joinButton: Button = findViewById(R.id.JoinButton)

        createButton.setOnClickListener { createRoom() }
        joinButton.setOnClickListener { showJoinDialog() }
    }

    private fun createRoom() {
        Log.d("MainActivity", "Кнопка 'Создать комнату' нажата")
        loadingDialog.show()
        lifecycleScope.launch {
            try {
                Log.d("MainActivity", "Получаем userId")
                val userId = UserManager.getUserId(this@MainActivity)

                Log.d("MainActivity", "Генерируем уникальный код комнаты...")
                val code = generateUniqueRoomCode()
                Log.d("MainActivity", "Сгенерирован код комнаты: $code")

                Log.d("MainActivity", "Создаём комнату в Supabase")
                val room = Supabase.client.postgrest["rooms"]
                    .insert(RoomInsert(code = code, status = "waiting"))
                    .decodeSingle<Room>()

                Log.d("MainActivity", "Комната создана с id: ${room.id}")

                Log.d("MainActivity", "Добавляем пользователя $userId в user_sessions")
                Supabase.client.postgrest["user_sessions"].insert(
                    UserSession(
                        user_id = userId,
                        room_id = room.id,
                        is_online = true,
                        last_active = Clock.System.now().toString()
                    ),
                    upsert = true,
                    onConflict = "user_id,room_id"
                )

                Log.d("MainActivity", "Пользователь успешно добавлен в сессию")
                Log.d("MainActivity", "Переходим в RoomActivity")
                goToRoom(room.id, room.code)

            } catch (e: Exception) {
                logError("CreateRoomError", e)
                showError("Ошибка создания комнаты: ${e.localizedMessage}")
            } finally {
                Log.d("MainActivity", "Скрываем диалог загрузки")
                loadingDialog.dismiss()
            }
        }
    }


    private fun showJoinDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_join_room, null)
        val etCode = dialogView.findViewById<EditText>(R.id.etCode)

        AlertDialog.Builder(this)
            .setTitle("Вход в комнату")
            .setView(dialogView)
            .setPositiveButton("Присоединиться") { _, _ ->
                val code = etCode.text.toString().trim().uppercase()
                if (code.length == 6) {
                    joinRoom(code)
                } else {
                    showError("Код должен содержать 6 символов")
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun joinRoom(code: String) {
        loadingDialog.show()
        lifecycleScope.launch {
            try {
                val userId = UserManager.getUserId(this@MainActivity)

                val room = Supabase.client.postgrest["rooms"]
                    .select { eq("code", code) }
                    .decodeSingle<Room>()

                Supabase.client.postgrest["user_sessions"].insert(
                    UserSession(
                        user_id = userId,
                        room_id = room.id,
                        is_online = true,
                        last_active = Clock.System.now().toString()
                    ),
                    upsert = true,
                    onConflict = "user_id,room_id"
                )

                goToRoom(room.id, room.code)

            } catch (e: Exception) {
                logError("JoinRoomError", e)
                showError("Ошибка подключения: ${e.localizedMessage}")
            } finally {
                loadingDialog.dismiss()
            }
        }
    }

    private suspend fun generateUniqueRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        var attempt = 0
        while (attempt < 10) {
            val code = (1..6).joinToString("") { chars.random().toString() }
            Log.d("RoomCode", "Пробуем код: $code (попытка $attempt)")
            try {
                val response = Supabase.client.postgrest["rooms"]
                    .select { eq("code", code) }
                    .decodeList<Room>()
                if (response.isEmpty()) {
                    Log.d("RoomCode", "Код $code уникален")
                    return code
                }
            } catch (e: Exception) {
                Log.e("RoomCode", "Ошибка при проверке кода: ${e.localizedMessage}")
                throw e
            }
            attempt++
        }
        throw IllegalStateException("Не удалось сгенерировать уникальный код комнаты после 10 попыток.")
    }


    private fun goToRoom(roomId: String, code: String) {
        startActivity(
            Intent(this, RoomActivity::class.java).apply {
                putExtra("ROOM_ID", roomId)
                putExtra("ROOM_CODE", code)
            }
        )
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun logError(tag: String, e: Exception) {
        Log.e(tag, """
            Ошибка: ${e.message}
            Stack trace: ${Log.getStackTraceString(e)}
        """.trimIndent())
    }
}
