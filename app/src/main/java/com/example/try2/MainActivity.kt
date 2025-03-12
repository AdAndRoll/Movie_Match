package com.example.try2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val createButton: Button=findViewById(R.id.CreateRoomButton)
        val joinButton: Button=findViewById(R.id.JoinButton)
        val database = FirebaseDatabase.getInstance()
        val ref = database.getReference("test")
        ref.setValue("Firebase подключен!") // Это создаст запись в БД
        createButton.setOnClickListener{
            val intent = Intent(this,MovieSearchActivity::class.java)
            startActivity(intent)
        }


        joinButton.setOnClickListener {
            //описание присоединения
        }
    }
}