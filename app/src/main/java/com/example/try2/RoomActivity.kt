package com.example.try2

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class RoomActivity : AppCompatActivity() {

    private lateinit var database: FirebaseDatabase
    private lateinit var roomCode: String
    private lateinit var userKey: String
    private lateinit var adapter: UserAdapter
    private lateinit var btnReady: Button
    private lateinit var btnStart: Button
    private lateinit var timerText: TextView
    private lateinit var currentUserRef: DatabaseReference
    private lateinit var prefs: SharedPreferences
    private lateinit var handler: Handler
    private lateinit var updatePresenceTask: Runnable
    private var countDownTimer: CountDownTimer? = null
    private var isOwner = false
    private var roomOwnerId: String? = null
    private var isExitingProperly = false

    companion object {
        private const val TAG = "RoomActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room)

        database = FirebaseDatabase.getInstance()
        prefs = getSharedPreferences("RoomPrefs", MODE_PRIVATE)
        handler = Handler(Looper.getMainLooper())

        roomCode = intent.getStringExtra("ROOM_CODE")!!
        userKey = intent.getStringExtra("USER_KEY")!!

        initializeUI()
        setupFirebaseReferences()
        setupPresenceSystem()
        checkOwnerStatus()
        setupListeners()
    }

    private fun initializeUI() {
        timerText = findViewById(R.id.timerText)
        btnReady = findViewById(R.id.btnReady)
        btnStart = findViewById(R.id.btnStart)

        findViewById<TextView>(R.id.roomCodeText).text = "Код комнаты: $roomCode"

        val recyclerView = findViewById<RecyclerView>(R.id.usersRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = UserAdapter()
        recyclerView.adapter = adapter
    }

    private fun setupFirebaseReferences() {
        currentUserRef = database.getReference("rooms/$roomCode/users/$userKey")
        currentUserRef.child("online").setValue(true)
            .addOnSuccessListener {
                database.getReference("active_rooms/$roomCode").setValue(true)
            }
    }

    private fun setupPresenceSystem() {
        updatePresenceTask = object : Runnable {
            override fun run() {
                currentUserRef.child("last_active").setValue(System.currentTimeMillis())
                handler.postDelayed(this, 30000)
            }
        }
        handler.post(updatePresenceTask)

        currentUserRef.child("online").onDisconnect().setValue(false)
    }

    private fun checkOwnerStatus() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        database.getReference("rooms/$roomCode/owner").get().addOnSuccessListener {
            isOwner = it.getValue(String::class.java) == currentUser?.uid
            btnStart.visibility = if (isOwner) View.VISIBLE else View.GONE
        }
    }

    private fun setupListeners() {
        setupUsersListener()
        setupReadyButton()
        setupStartButton()
        setupRoomStateListener()
    }

    private fun setupUsersListener() {
        val ownerRef = database.getReference("rooms/$roomCode/owner")
        ownerRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                roomOwnerId = snapshot.getValue(String::class.java)
                database.getReference("rooms/$roomCode/users")
                    .addValueEventListener(createUsersListener())
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Не удалось загрузить создателя комнаты")
            }
        })
    }

    private fun createUsersListener() = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val users = snapshot.children.mapNotNull {
                it.getValue(User::class.java)?.copy(uid = it.key)
            }
            adapter.setOwnerId(roomOwnerId)
            adapter.submitList(users)
            checkAutoStartCondition(users)
        }

        override fun onCancelled(error: DatabaseError) {
            showError("Ошибка загрузки пользователей")
        }
    }

    private fun checkAutoStartCondition(users: List<User>) {
        if (isOwner && users.isNotEmpty() && users.all { it.ready }) {
            AlertDialog.Builder(this)
                .setTitle("Все готовы!")
                .setMessage("Запустить автоматический отсчет?")
                .setPositiveButton("Да") { _, _ ->
                    database.getReference("rooms/$roomCode/timer_start")
                        .setValue(System.currentTimeMillis())
                }
                .setNegativeButton("Нет", null)
                .show()
        }
    }

    private fun setupReadyButton() {
        currentUserRef.child("ready").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    updateReadyButtonState(snapshot.getValue(Boolean::class.java) ?: false)
                }

                override fun onCancelled(error: DatabaseError) {
                    showError("Ошибка загрузки состояния")
                }
            })

        btnReady.setOnClickListener {
            currentUserRef.child("ready").get().addOnSuccessListener { snapshot ->
                val newState = !(snapshot.getValue(Boolean::class.java) ?: false)
                currentUserRef.child("ready").setValue(newState)
                updateReadyButtonState(newState)
            }
        }
    }

    private fun updateReadyButtonState(isReady: Boolean) {
        btnReady.text = if (isReady) "Не готов" else "Готов"
    }

    private fun setupStartButton() {
        btnStart.setOnClickListener {
            database.getReference("rooms/$roomCode").child("status").setValue("voting")
            startMovieSearchForAll()
        }
    }

    private fun setupRoomStateListener() {
        database.getReference("rooms/$roomCode/timer_start").addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.getValue(Long::class.java)?.let {
                        if (it > 0) startCountdown(it)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    showError("Ошибка синхронизации таймера")
                }
            })
    }

    private fun startCountdown(startTime: Long) {
        countDownTimer?.cancel()
        val totalTime = 5000L
        val remaining = (startTime + totalTime) - System.currentTimeMillis()

        countDownTimer = object : CountDownTimer(remaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timerText.text = "${millisUntilFinished / 1000}"
                timerText.animate().alpha(1f).withEndAction { timerText.animate().alpha(0.5f) }
            }

            override fun onFinish() {
                timerText.text = ""
                startMovieSearchForAll()
            }
        }.start()
    }

    private fun startMovieSearchForAll() {
        startActivity(Intent(this, MovieSearchActivity::class.java).apply {
            putExtra("ROOM_CODE", roomCode)
            putExtra("USER_KEY", userKey)
        })
        finish()
    }

    override fun onResume() {
        super.onResume()
        database.getReference("rooms/$roomCode/status").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.getValue(String::class.java) == "voting") {
                    startMovieSearchForAll()
                }
            }
    }

    override fun onBackPressed() {
        isExitingProperly = true
        exitRoomProperly()
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updatePresenceTask)
        countDownTimer?.cancel()

        if (!isExitingProperly) {
            // Для неожиданного закрытия - устанавливаем onDisconnect
            currentUserRef.child("online").onDisconnect().setValue(false)

            // Дополнительная проверка через время
            Handler(Looper.getMainLooper()).postDelayed({
                checkAndCleanupRoom()
            }, 5000)
        }
    }

    private fun exitRoomProperly() {
        // Отменяем все pending onDisconnect операции
        currentUserRef.child("online").onDisconnect().cancel()

        // Обновляем статус пользователя сразу
        currentUserRef.child("online").setValue(false)
            .addOnCompleteListener {
                // Запускаем проверку комнаты через 2 секунды для учета всех onDisconnect операций
                Handler(Looper.getMainLooper()).postDelayed({
                    checkAndCleanupRoom()
                }, 2000)
                finish()
            }
    }

    private fun checkAndCleanupRoom() {
        val roomRef = database.getReference("rooms/$roomCode")

        roomRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val users = mutableData.child("users")
                var hasOnline = false

                users.children.forEach { user ->
                    val online = user.child("online").getValue(Boolean::class.java) ?: false
                    if (online) {
                        hasOnline = true
                        return@forEach
                    }
                }

                if (!hasOnline) {
                    // Удаляем из active_rooms только если нет онлайн пользователей
                    database.getReference("active_rooms/$roomCode").removeValue()
                    mutableData.value = null
                }

                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null) {
                    Log.e(TAG, "Room cleanup failed", error.toException())
                } else {
                    Log.d(TAG, "Room cleanup completed: $committed")
                }
            }
        })
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    data class User(
        val uid: String? = null,
        val name: String? = null,
        val ready: Boolean = false,
        val online: Boolean = true,
        val last_active: Long = 0
    )
}