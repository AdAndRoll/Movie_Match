package com.example.try2

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class RoomActivity : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    private lateinit var roomCode: String
    private lateinit var userKey: String
    private lateinit var adapter: UserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room)

        // Получаем данные из Intent
        roomCode = intent.getStringExtra("ROOM_CODE")!!
        userKey = intent.getStringExtra("USER_KEY")!!
        val userName = intent.getStringExtra("USER_NAME")!! // Теперь используется

        // Инициализируем Firebase
        database = FirebaseDatabase.getInstance()

        // Настройка UI
        setupRoomCodeDisplay()
        setupRecyclerView()
        setupUsersListener()
    }

    private fun setupRoomCodeDisplay() {
        findViewById<TextView>(R.id.roomCodeText).text = "Код комнаты: $roomCode"
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.usersRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = UserAdapter()
        recyclerView.adapter = adapter
    }

    private fun setupUsersListener() {
        val usersRef = database.getReference("rooms/$roomCode/users")
        usersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = snapshot.children.mapNotNull {
                    it.getValue(User::class.java)?.copy(uid = it.key)
                }
                adapter.submitList(users)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@RoomActivity, "Ошибка загрузки", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Класс User должен быть объявлен внутри RoomActivity
    data class User(
        val uid: String? = null,
        val name: String? = null,
        val ready: Boolean = false
    )

    override fun onDestroy() {
        super.onDestroy()
        database.getReference("rooms/$roomCode/users/$userKey").removeValue()
    }
}