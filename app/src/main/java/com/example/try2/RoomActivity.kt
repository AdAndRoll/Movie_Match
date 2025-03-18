package com.example.try2

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.TimeUnit

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room)

        database = FirebaseDatabase.getInstance()
        prefs = getSharedPreferences("RoomPrefs", MODE_PRIVATE)
        handler = Handler(Looper.getMainLooper())

        roomCode = intent.getStringExtra("ROOM_CODE")!!
        userKey = intent.getStringExtra("USER_KEY")!!
        val userName = intent.getStringExtra("USER_NAME")!!

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
    }

    private fun setupPresenceSystem() {
        // Автоматическое обновление активности
        updatePresenceTask = object : Runnable {
            override fun run() {
                currentUserRef.child("last_active").setValue(System.currentTimeMillis())
                handler.postDelayed(this, 30000)
            }
        }
        handler.post(updatePresenceTask)

        // Обработка отключения
        currentUserRef.child("online").onDisconnect().setValue(false)
        currentUserRef.onDisconnect().removeValue().addOnSuccessListener {
            database.getReference("rooms/$roomCode").runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val users = mutableData.child("users")
                    if (users.children.none { it.child("online").getValue(Boolean::class.java) == true }) {
                        mutableData.value = null
                    }
                    return Transaction.success(mutableData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (committed) {
                        database.getReference("active_rooms/$roomCode").removeValue()
                        prefs.edit().remove("room_$roomCode").apply()
                    }
                }
            })
        }
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
        database.getReference("rooms/$roomCode/users").addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val users = snapshot.children.mapNotNull {
                        it.getValue(User::class.java)?.copy(
                            uid = it.key,
                            ownerId = it.child("ownerId").getValue(String::class.java)
                        )
                    }
                    adapter.submitList(users)
                    checkAutoStartCondition(users)
                }

                override fun onCancelled(error: DatabaseError) {
                    showError("Ошибка загрузки пользователей")
                }
            })
    }

    private fun checkAutoStartCondition(users: List<User>) {
        if (isOwner && users.isNotEmpty() && users.all { it.ready }) {
            database.getReference("rooms/$roomCode/timer_start")
                .setValue(System.currentTimeMillis())
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
        val totalTime = 5000L // 5 секунд
        val remaining = (startTime + totalTime) - System.currentTimeMillis()

        countDownTimer = object : CountDownTimer(remaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timerText.text = "${TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished)}"
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
        currentUserRef.child("ready").setValue(false)
        database.getReference("rooms/$roomCode/timer_start").setValue(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updatePresenceTask)
        countDownTimer?.cancel()

        currentUserRef.child("online").setValue(false)
        prefs.edit().putString("room_$roomCode", userKey).apply()

        database.getReference("rooms/$roomCode").runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val users = mutableData.child("users")
                if (users.children.none { it.child("online").getValue(Boolean::class.java) == true }) {
                    mutableData.value = null
                }
                return Transaction.success(mutableData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (committed) {
                    database.getReference("active_rooms/$roomCode").removeValue()
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
        val last_active: Long = 0,
        val ownerId: String? = null // Добавлено поле ownerId
    ) {
       // val ownerId: Any?
    }
}