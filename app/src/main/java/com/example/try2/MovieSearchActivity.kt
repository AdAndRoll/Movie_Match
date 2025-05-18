package com.example.try2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.RangeSlider
import com.squareup.picasso.Picasso
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MovieSearchActivity : AppCompatActivity() {

    private lateinit var genreSpinner: MultiSelectionSpinner
    private lateinit var yearRangeSlider: RangeSlider
    private lateinit var searchButton: Button
    private lateinit var countdownTextView: TextView
    private lateinit var yearRangeText: TextView
    private lateinit var roomId: String
    private lateinit var userId: String
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var isSearchSubmitted = false // Флаг для отслеживания отправки запроса
    private var lastSelectedGenres: List<String> = emptyList() // Последние выбранные жанры
    private var lastSelectedYears: List<Float> = listOf(1990f, 2024f) // Последние выбранные года

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_movie_search)

        genreSpinner = findViewById(R.id.genreSpinner)
        yearRangeSlider = findViewById(R.id.yearRangeSlider)
        searchButton = findViewById(R.id.searchButton)
        countdownTextView = findViewById(R.id.countdownTextView)
        yearRangeText = findViewById(R.id.yearRangeText)

        roomId = intent.getStringExtra("room_id") ?: run {
            Toast.makeText(this, "Room ID not provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        userId = UserManager.getUserId(this)

        setupGenreSpinner()
        setupYearRangeSlider()
        setupSearchButton()
    }

    private fun setupGenreSpinner() {
        val genres = listOf(
            "аниме", "биография", "боевик", "вестерн", "военный", "детектив", "документальный", "драма", "игра", "история", "комедия",
            "короткометражка", "криминал", "мелодрама", "мультфильм", "мюзикл",
            "приключения", "семейный", "триллер",
            "ужасы", "фантастика", "фильм-нуар", "фэнтези"
        )
        genreSpinner.setItems(genres)
        genreSpinner.setSelection(emptyList())
        // Отслеживаем изменения жанров
        genreSpinner.setOnSelectionChangedListener { selectedGenres ->
            if (isSearchSubmitted) {
                // Проверяем, изменились ли жанры
                if (selectedGenres != lastSelectedGenres) {
                    enableSearchButton()
                    Log.d("MovieSearchActivity", "Genres changed, re-enabling search button: $selectedGenres")
                }
            }
            lastSelectedGenres = selectedGenres
        }
    }

    private fun setupYearRangeSlider() {
        yearRangeSlider.valueFrom = 1990f
        yearRangeSlider.valueTo = 2024f
        yearRangeSlider.values = listOf(1990f, 2024f)
        yearRangeSlider.stepSize = 1f
        yearRangeSlider.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            yearRangeText.text = "Выбранные года: ${values[0].toInt()} - ${values[1].toInt()}"
            if (isSearchSubmitted) {
                // Проверяем, изменились ли года
                if (values != lastSelectedYears) {
                    enableSearchButton()
                    Log.d("MovieSearchActivity", "Years changed, re-enabling search button: $values")
                }
            }
            lastSelectedYears = values
        }
    }

    private fun setupSearchButton() {
        searchButton.setOnClickListener {
            if (!isSearchSubmitted) {
                submitPreferences()
            }
        }
    }

    private fun disableSearchButton() {
        searchButton.isEnabled = false
        searchButton.text = "Ожидание..."
        isSearchSubmitted = true
        Log.d("MovieSearchActivity", "Search button disabled")
    }

    private fun enableSearchButton() {
        searchButton.isEnabled = true
        searchButton.text = "Искать фильмы"
        isSearchSubmitted = false
        Log.d("MovieSearchActivity", "Search button re-enabled")
    }

    private fun submitPreferences() {
        val selectedYears = yearRangeSlider.values
        val years = listOf(selectedYears[0].toInt(), selectedYears[1].toInt())
        val selectedGenres = genreSpinner.getSelectedItems()

        if (selectedGenres.isEmpty()) {
            Toast.makeText(this, "Выберите хотя бы один жанр", Toast.LENGTH_SHORT).show()
            return
        }

        // Отключаем кнопку
        disableSearchButton()

        val request = PreferencesRequest(
            user_id = userId,
            room_id = roomId,
            genres = selectedGenres,
            years = years
        )
        Log.d("MovieSearchActivity", "Sending preferences: $request")

        coroutineScope.launch {
            try {
                val response = ServerClient.api.submitPreferences(request)
                Log.d("MovieSearchActivity", "Response: ${response.body()}")
                if (response.isSuccessful) {
                    val statusResponse = response.body()
                    when (statusResponse?.status) {
                        "waiting" -> {
                            Toast.makeText(this@MovieSearchActivity, "Ожидаем других пользователей...", Toast.LENGTH_SHORT).show()
                            startStatusPolling()
                        }
                        "ready" -> {
                            Toast.makeText(this@MovieSearchActivity, "Результаты готовы!", Toast.LENGTH_SHORT).show()
                            startCountdownAndNavigate()
                        }
                        else -> {
                            Toast.makeText(this@MovieSearchActivity, "Ошибка: ${statusResponse?.error}", Toast.LENGTH_SHORT).show()
                            enableSearchButton() // В случае ошибки включаем кнопку
                        }
                    }
                } else {
                    Toast.makeText(this@MovieSearchActivity, "Ошибка сервера: ${response.message()}", Toast.LENGTH_SHORT).show()
                    enableSearchButton() // В случае ошибки включаем кнопку
                }
            } catch (e: Exception) {
                Log.e("MovieSearchActivity", "Network error: ${e.message}")
                Toast.makeText(this@MovieSearchActivity, "Ошибка сети: ${e.message}", Toast.LENGTH_SHORT).show()
                enableSearchButton() // В случае ошибки включаем кнопку
            }
        }
    }

    private fun startStatusPolling() {
        coroutineScope.launch {
            while (isActive) {
                try {
                    val response = withContext(Dispatchers.IO) {
                        ServerClient.api.checkStatus(roomId)
                    }
                    Log.d("MovieSearchActivity", "Polling status: ${response.body()}")
                    if (response.isSuccessful && response.body()?.status == "ready") {
                        Toast.makeText(this@MovieSearchActivity, "Результаты готовы!", Toast.LENGTH_SHORT).show()
                        startCountdownAndNavigate()
                        break
                    }
                } catch (e: Exception) {
                    Log.e("MovieSearchActivity", "Polling error: ${e.message}")
                    Toast.makeText(this@MovieSearchActivity, "Ошибка опроса: ${e.message}", Toast.LENGTH_SHORT).show()
                    enableSearchButton() // В случае ошибки включаем кнопку
                }
                delay(2_000)
            }
        }
    }

    private fun startCountdownAndNavigate() {
        coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            countdownTextView.visibility = View.VISIBLE
            var movieList: List<Movie> = emptyList()

            // Загружаем фильмы из Supabase во время отсчета
            try {
                val response = withContext(Dispatchers.IO) {
                    Supabase.client.from("room_results")
                        .select {
                            filter {
                                eq("room_id", roomId)
                            }
                        }
                        .decodeSingle<RoomResults>()
                }
                movieList = response.movies
                Log.d("MovieSearchActivity", "Loaded ${movieList.size} movies during countdown")
            } catch (e: Exception) {
                Log.e("MovieSearchActivity", "Error loading movies during countdown: ${e.message}")
                Toast.makeText(this@MovieSearchActivity, "Ошибка загрузки фильмов: ${e.message}", Toast.LENGTH_SHORT).show()
                enableSearchButton() // В случае ошибки включаем кнопку
            }

            // Начинаем предзагрузку постеров
            if (movieList.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    movieList.take(10).forEach { movie ->
                        try {
                            Picasso.get()
                                .load(movie.poster.url)
                                .fetch()
                            Log.d("MovieSearchActivity", "Preloaded poster for ${movie.name}")
                        } catch (e: Exception) {
                            Log.e("MovieSearchActivity", "Error preloading poster for ${movie.name}: ${e.message}")
                        }
                    }
                }
            }

            // Отсчет
            for (i in 5 downTo 0) {
                Log.d("MovieSearchActivity", "Countdown: $i")
                countdownTextView.text = "Переход через: $i"
                delay(1_000)
                if (!isActive) {
                    Log.d("MovieSearchActivity", "Countdown interrupted")
                    countdownTextView.visibility = View.GONE
                    enableSearchButton() // Если прервано, включаем кнопку
                    return@launch
                }
            }

            val elapsedTime = System.currentTimeMillis() - startTime
            Log.d("MovieSearchActivity", "Countdown completed, elapsed: $elapsedTime ms")
            countdownTextView.visibility = View.GONE

            // Передаем список фильмов в ChooseActivity
            val intent = Intent(this@MovieSearchActivity, ChooseActivity::class.java).apply {
                putExtra("ROOM_ID", roomId)
                if (movieList.isNotEmpty()) {
                    putExtra("MOVIE_LIST", Json.encodeToString(movieList))
                }
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}