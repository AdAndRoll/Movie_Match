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
import androidx.work.*
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
    private lateinit var handler: Handler
    private lateinit var updatePresenceTask: Runnable
    private var countDownTimer: CountDownTimer? = null
    private var isOwner = false
    private var roomOwnerId: String? = null
    private var isExitingProperly = false

    companion object {
        private const val TAG = "RoomActivity"
        private const val DELAY_BEFORE_CHECK = 5000L // 5 секунд
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room)

        database = FirebaseDatabase.getInstance()
        handler = Handler(Looper.getMainLooper())

        roomCode = intent.getStringExtra("ROOM_CODE")!!
        userKey = intent.getStringExtra("USER_KEY")!!

        initializeUI()
        setupFirebaseReferences()
        setupPresenceSystem()
        checkOwnerStatus()
        setupListeners()
        setupActiveRoomListener()
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
            currentUserRef.child("online").onDisconnect().setValue(false)
            scheduleRoomCleanupWorker()
        }
    }

    private fun exitRoomProperly() {
        currentUserRef.child("online").onDisconnect().cancel()
        currentUserRef.child("online").setValue(false)
            .addOnCompleteListener {
                Handler(Looper.getMainLooper()).postDelayed({
                    checkAndCleanupRoom()
                }, 2000)
                finish()
            }
    }

    private fun checkAndCleanupRoom() {
        RoomManager.checkAndCleanRoom(roomCode, database) { committed ->
            Log.d(TAG, if (committed) "Комната удалена" else "Комната сохранена")
        }
    }

    private fun setupActiveRoomListener() {
        database.getReference("active_rooms/$roomCode").addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        finish()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Ошибка слушателя active_rooms", error.toException())
                }
            })
    }

    private fun scheduleRoomCleanupWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val data = Data.Builder()
            .putString("roomCode", roomCode)
            .putString("userKey", userKey)
            .build()

        val cleanupRequest = OneTimeWorkRequestBuilder<RoomCleanupWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setInitialDelay(DELAY_BEFORE_CHECK, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueue(cleanupRequest)
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