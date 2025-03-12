package com.example.try2

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {

    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        setupEdgeToEdge()

        database = FirebaseDatabase.getInstance()
        initializeButtons()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initializeButtons() {
        val createButton: Button = findViewById(R.id.CreateRoomButton)
        val joinButton: Button = findViewById(R.id.JoinButton)

        createButton.setOnClickListener { showCreateRoomDialog() }
        joinButton.setOnClickListener { showJoinRoomDialog() }
    }

    private fun showCreateRoomDialog() {
        val input = EditText(this).apply {
            hint = "Ваше имя"
        }

        AlertDialog.Builder(this)
            .setTitle("Создание комнаты")
            .setMessage("Введите ваше имя для создания комнаты")
            .setView(input)
            .setPositiveButton("Создать") { _, _ ->
                val userName = input.text.toString().trim()
                if (userName.isNotEmpty()) {
                    createNewRoom(userName)
                } else {
                    Toast.makeText(this, "Имя не может быть пустым", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun createNewRoom(userName: String) {
        val roomCode = generateRoomCode()
        val roomRef = database.getReference("rooms/$roomCode")
        val userKey = roomRef.child("users").push().key

        val roomData = hashMapOf(
            "owner" to userName,
            "status" to "waiting"
        )

        val userData = hashMapOf(
            "name" to userName,
            "ready" to false
        )

        roomRef.updateChildren(roomData as Map<String, Any>)
            .addOnSuccessListener {
                roomRef.child("users/$userKey").setValue(userData)
                    .addOnSuccessListener {
                        navigateToRoom(roomCode, userKey!!, userName)
                    }
                    .addOnFailureListener { e ->
                        showError("Ошибка создания пользователя: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                showError("Ошибка создания комнаты: ${e.message}")
            }
    }

    private fun showJoinRoomDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_join_room, null)
        AlertDialog.Builder(this)
            .setTitle("Присоединиться к комнате")
            .setView(dialogView)
            .setPositiveButton("Подключиться") { _, _ ->
                val roomCode = dialogView.findViewById<EditText>(R.id.roomCodeInput).text.toString().uppercase()
                val userName = dialogView.findViewById<EditText>(R.id.userNameInput).text.toString().trim()

                when {
                    roomCode.isEmpty() -> showError("Введите код комнаты")
                    userName.isEmpty() -> showError("Введите ваше имя")
                    else -> joinRoom(roomCode, userName)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun joinRoom(roomCode: String, userName: String) {
        val roomRef = database.getReference("rooms/$roomCode")
        roomRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val userKey = roomRef.child("users").push().key
                val userData = hashMapOf(
                    "name" to userName,
                    "ready" to false
                )

                roomRef.child("users/$userKey").setValue(userData)
                    .addOnSuccessListener {
                        navigateToRoom(roomCode, userKey!!, userName)
                    }
                    .addOnFailureListener { e ->
                        showError("Ошибка подключения: ${e.message}")
                    }
            } else {
                showError("Комната $roomCode не найдена")
            }
        }.addOnFailureListener { e ->
            showError("Ошибка подключения: ${e.message}")
        }
    }

    private fun navigateToRoom(roomCode: String, userKey: String, userName: String) {
        Intent(this, RoomActivity::class.java).apply {
            putExtra("ROOM_CODE", roomCode)
            putExtra("USER_KEY", userKey)
            putExtra("USER_NAME", userName)
            startActivity(this)
        }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return List(6) { chars.random() }.joinToString("")
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}