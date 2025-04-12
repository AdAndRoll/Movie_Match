package com.example.try2

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var database: FirebaseDatabase
    private lateinit var prefs: SharedPreferences
    private lateinit var roomsList: ListView
    private lateinit var roomsAdapter: ArrayAdapter<String>
    private val activeRooms = mutableListOf<String>()
    private lateinit var activeRoomsListener: ValueEventListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        initializeComponents()
        setupFirebase()
        setupUI()
        setupRoomsList()
        checkSavedRooms()
        auth = FirebaseAuth.getInstance()
        checkAuthentication()
    }


    private fun checkAuthentication() {
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnCompleteListener(this) { task ->
                    if (!task.isSuccessful) {
                        showError("Ошибка аутентификации")
                        finish()
                    }
                }
        }
    }


    private fun initializeComponents() {
        prefs = getSharedPreferences("RoomPrefs", MODE_PRIVATE)
        roomsList = findViewById(R.id.roomsList)
        roomsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, activeRooms)
        roomsList.adapter = roomsAdapter
    }

    private fun setupFirebase() {
        database = FirebaseDatabase.getInstance()
        database.setLogLevel(com.google.firebase.database.Logger.Level.DEBUG)
    }

    private fun setupUI() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.CreateRoomButton).setOnClickListener { showCreateRoomDialog() }
        findViewById<Button>(R.id.JoinButton).setOnClickListener { showJoinRoomDialog() }
    }

    private fun setupRoomsList() {
        val query = database.getReference("active_rooms").orderByValue().equalTo(true)
        activeRoomsListener = query.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val roomKeys = snapshot.children.mapNotNull { it.key }
                val validRooms = mutableListOf<String>()
                var pendingChecks = roomKeys.size

                if (pendingChecks == 0) {
                    activeRooms.clear()
                    roomsAdapter.notifyDataSetChanged()
                    return
                }

                // Для каждого кода комнаты проверяем наличие в "rooms"
                roomKeys.forEach { roomCode ->
                    database.getReference("rooms/$roomCode").get()
                        .addOnSuccessListener { roomSnapshot ->
                            if (roomSnapshot.exists()) {
                                // Только если данные о комнате сохранены в prefs, добавляем в список
                                if (prefs.contains("room_$roomCode")) {
                                    validRooms.add(roomCode)
                                }
                            } else {
                                // Если комнаты нет в базе, удаляем её из active_rooms и prefs
                                database.getReference("active_rooms/$roomCode").removeValue()
                                    .addOnFailureListener {
                                        Log.e("Firebase", "Ошибка при удалении комнаты $roomCode из active_rooms: ${it.message}")
                                    }
                                prefs.edit().remove("room_$roomCode").apply()
                            }
                            pendingChecks--
                            if (pendingChecks == 0) {
                                activeRooms.clear()
                                activeRooms.addAll(validRooms)
                                roomsAdapter.notifyDataSetChanged()
                                Log.d("Firebase", "Обновлённый список комнат: $validRooms")
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firebase", "Ошибка проверки комнаты $roomCode: ${e.message}")
                            pendingChecks--
                            if (pendingChecks == 0) {
                                activeRooms.clear()
                                activeRooms.addAll(validRooms)
                                roomsAdapter.notifyDataSetChanged()
                            }
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Ошибка загрузки комнат: ${error.message}")
            }
        })

        roomsList.setOnItemClickListener { _, _, position, _ ->
            if (position < activeRooms.size) {
                rejoinRoom(activeRooms[position])
            }
        }
    }


    private fun validateRoomExistence(roomCode: String): Boolean {
        database.getReference("rooms/$roomCode").get().addOnSuccessListener {
            if (!it.exists()) {
                database.getReference("active_rooms/$roomCode").removeValue()
                prefs.edit().remove("room_$roomCode").apply()
            }
        }
        return prefs.contains("room_$roomCode")
    }

    private fun checkSavedRooms() {
        val savedRooms = prefs.all.keys.filter { it.startsWith("room_") }
        savedRooms.forEach { key ->
            val roomCode = key.substringAfter("room_")
            verifyRoomExistence(roomCode)
        }
    }

    private fun verifyRoomExistence(roomCode: String) {
        database.getReference("rooms/$roomCode").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        if (!activeRooms.contains(roomCode)) {
                            activeRooms.add(roomCode)
                            roomsAdapter.notifyDataSetChanged()
                        }
                    } else {
                        prefs.edit().remove("room_$roomCode").apply()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w("MainActivity", "Room verification cancelled", error.toException())
                }
            })
    }

    private fun rejoinRoom(roomCode: String) {
        val userKey = prefs.getString("room_$roomCode", null) ?: run {
            showError("Данные комнаты утеряны")
            return
        }

        database.getReference("rooms/$roomCode/users/$userKey").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    snapshot.ref.child("online").setValue(true)
                    navigateToRoom(
                        roomCode,
                        userKey,
                        snapshot.child("name").getValue(String::class.java) ?: ""
                    )
                } else {
                    prefs.edit().remove("room_$roomCode").apply()
                    showJoinRoomDialog(roomCode)
                }
            }
            .addOnFailureListener { e ->
                showError("Ошибка подключения: ${e.message}")
            }
    }

    private fun showCreateRoomDialog() {
        val input = EditText(this).apply {
            hint = "Ваше имя"
            maxLines = 1
            setText(prefs.getString("last_username", ""))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Создать комнату")
            .setView(input)
            .setPositiveButton("Создать", null)
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
                hideKeyboard(input)
            }
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                handleCreateRoomInput(input, dialog)
            }
        }

        dialog.show()
        showKeyboard(input)
    }

    private fun handleCreateRoomInput(input: EditText, dialog: AlertDialog) {
        val name = input.text.toString().trim()
        when {
            name.isEmpty() -> input.error = "Введите имя"
            name.length < 2 -> input.error = "Имя слишком короткое"
            name.length > 20 -> input.error = "Имя слишком длинное"
            else -> {
                prefs.edit().putString("last_username", name).apply()
                createNewRoom(name)
                dialog.dismiss()
                hideKeyboard(input)
            }
        }
    }

    private fun createNewRoom(userName: String) {
        val roomCode = generateRoomCode()
        val roomRef = database.getReference("rooms/$roomCode")
        val userKey = auth.currentUser?.uid ?: return showError("Ошибка аутентификации")

        val roomData = hashMapOf(
            "owner" to userKey,
            "status" to "waiting",
            "created_at" to ServerValue.TIMESTAMP,
            "users" to hashMapOf( // Добавляем сразу список пользователей
                userKey to hashMapOf(
                    "name" to userName,
                    "ready" to false,
                    "online" to true,
                    "last_active" to ServerValue.TIMESTAMP,
                    "ownerId" to userKey
                )
            )
        )

        roomRef.setValue(roomData)
            .addOnSuccessListener {
                prefs.edit().putString("room_$roomCode", userKey).apply()
                database.getReference("active_rooms/$roomCode").setValue(true)
                navigateToRoom(roomCode, userKey, userName)
            }
            .addOnFailureListener { e ->
                showError("Ошибка: ${e.message}")
            }
    }

    private fun showJoinRoomDialog(prefilledCode: String? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_join_room, null)
        val roomCodeInput = dialogView.findViewById<EditText>(R.id.roomCodeInput).apply {
            setText(prefilledCode ?: "")
        }
        val userNameInput = dialogView.findViewById<EditText>(R.id.userNameInput).apply {
            setText(prefs.getString("last_username", ""))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Присоединиться к комнате")
            .setView(dialogView)
            .setPositiveButton("Подключиться", null)
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
                hideKeyboard(roomCodeInput)
            }
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                handleJoinRoomInput(roomCodeInput, userNameInput, dialog)
            }
        }

        dialog.show()
        showKeyboard(roomCodeInput)
    }

    private fun handleJoinRoomInput(
        roomCodeInput: EditText,
        userNameInput: EditText,
        dialog: AlertDialog
    ) {
        val roomCode = roomCodeInput.text.toString().uppercase().trim()
        val userName = userNameInput.text.toString().trim()

        when {
            roomCode.isEmpty() -> roomCodeInput.error = "Введите код комнаты"
            userName.isEmpty() -> userNameInput.error = "Введите ваше имя"
            userName.length < 2 -> userNameInput.error = "Имя слишком короткое"
            userName.length > 20 -> userNameInput.error = "Имя слишком длинное"
            else -> {
                prefs.edit().putString("last_username", userName).apply()
                joinRoom(roomCode, userName)
                dialog.dismiss()
                hideKeyboard(roomCodeInput)
            }
        }
    }

    private fun joinRoom(roomCode: String, userName: String) {
        database.getReference("rooms/$roomCode").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    createUserInRoom(roomCode, userName)
                } else {
                    showError("Комната $roomCode не найдена")
                }
            }
            .addOnFailureListener { e ->
                showError("Ошибка подключения: ${e.message}")
            }
    }

    private fun createUserInRoom(roomCode: String, userName: String) {
        val userKey = auth.currentUser?.uid ?: run {
            showError("Ошибка аутентификации")
            return
        }

        val userRef = database.getReference("rooms/$roomCode/users/$userKey")

        val userData = hashMapOf(
            "name" to userName,
            "ready" to false,
            "online" to true,
            "last_active" to ServerValue.TIMESTAMP
        )

        userRef.setValue(userData)
            .addOnSuccessListener {
                prefs.edit().putString("room_$roomCode", userKey).apply()
                database.getReference("active_rooms/$roomCode").setValue(true)
                navigateToRoom(roomCode, userKey, userName)
            }
            .addOnFailureListener { e ->
                showError("Ошибка подключения: ${e.message}")
                Log.e("Firebase", "Join room error", e)
            }
    }

    private fun navigateToRoom(roomCode: String, userKey: String, userName: String) {
        startActivity(Intent(this, RoomActivity::class.java).apply {
            putExtra("ROOM_CODE", roomCode)
            putExtra("USER_KEY", userKey)
            putExtra("USER_NAME", userName)
        })
    }

    override fun onResume() {
        super.onResume()
        prefs.all.keys.filter { it.startsWith("room_") }.forEach { key ->
            val roomCode = key.substringAfter("room_")
            database.getReference("rooms/$roomCode").get()
                .addOnSuccessListener {
                    if (it.exists() && !activeRooms.contains(roomCode)) {
                        activeRooms.add(roomCode)
                        roomsAdapter.notifyDataSetChanged()
                    }
                }
        }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return buildString {
            repeat(6) {
                append(chars.random())
            }
        }
    }

    private fun showError(message: String) {
        if (!isDestroyed) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showKeyboard(view: View) {
        view.postDelayed({
            view.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    private fun hideKeyboard(view: View?) {
        view?.let {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        database.getReference("active_rooms").removeEventListener(activeRoomsListener)
    }
}