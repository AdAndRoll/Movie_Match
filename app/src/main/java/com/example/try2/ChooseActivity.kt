package com.example.try2

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

class ChooseActivity : AppCompatActivity() {

    private lateinit var movieNameTextView: TextView
    private lateinit var movieDescriptionTextView: TextView
    private lateinit var movieGenreTextView: TextView
    private lateinit var movieYearTextView: TextView
    private lateinit var movieLengthTextView: TextView
    private lateinit var movieRatingTextView: TextView
    private lateinit var moviePosterImageView: ImageView
    private lateinit var yesButton: Button
    private lateinit var noButton: Button
    private lateinit var messageTextView: TextView
    private lateinit var roomId: String
    private lateinit var userId: String
    private var movieList: List<Movie> = emptyList()
    private var currentMovieIndex: Int = 0
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose)

        // Инициализация views
        movieNameTextView = findViewById(R.id.movieNameTextView)
        movieDescriptionTextView = findViewById(R.id.movieDescriptionTextView)
        movieGenreTextView = findViewById(R.id.movieGenreTextView)
        movieYearTextView = findViewById(R.id.movieYearTextView)
        movieLengthTextView = findViewById(R.id.movieLengthTextView)
        movieRatingTextView = findViewById(R.id.movieRatingTextView)
        moviePosterImageView = findViewById(R.id.moviePosterImageView)
        yesButton = findViewById(R.id.yesButton)
        noButton = findViewById(R.id.noButton)
        messageTextView = findViewById(R.id.messageTextView)

        // Получение room_id
        roomId = intent.getStringExtra("ROOM_ID") ?: run {
            Toast.makeText(this, "Room ID not provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        userId = UserManager.getUserId(this)

        // Загрузка фильмов и запуск опроса
        loadMovies()
        startPolling()
    }

    private fun loadMovies() {
        coroutineScope.launch {
            try {
                println("Loading movies for room_id: $roomId")
                val response = Supabase.client.from("room_results")
                    .select {
                        filter {
                            eq("room_id", roomId)
                        }
                    }
                    .decodeSingle<RoomResults>()
                println("Loaded movies: ${response.movies}")
                movieList = response.movies
                if (movieList.isNotEmpty()) {
                    currentMovieIndex = 0
                    displayMovie(movieList[currentMovieIndex])

                    // Установка обработчиков кнопок
                    yesButton.setOnClickListener {
                        saveMovieChoice(movieList[currentMovieIndex].id)
                    }

                    noButton.setOnClickListener {
                        currentMovieIndex = (currentMovieIndex + 1) % movieList.size
                        displayMovie(movieList[currentMovieIndex])
                    }
                } else {
                    println("No movies found for room_id: $roomId")
                    Toast.makeText(this@ChooseActivity, "Фильмы не найдены", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: SerializationException) {
                println("Serialization error: ${e.message}")
                Toast.makeText(this@ChooseActivity, "Ошибка парсинга данных: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            } catch (e: Exception) {
                println("General error: ${e.message}")
                Toast.makeText(this@ChooseActivity, "Ошибка загрузки фильмов: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun saveMovieChoice(movieId: Long) {
        coroutineScope.launch {
            try {
                // Удаляем предыдущий выбор пользователя
                withContext(Dispatchers.IO) {
                    Supabase.client.from("movie_choices")
                        .delete {
                            filter {
                                eq("user_id", userId)
                                eq("room_id", roomId)
                            }
                        }
                }
                // Сохраняем новый выбор
                withContext(Dispatchers.IO) {
                    Supabase.client.from("movie_choices")
                        .insert(MovieChoiceInsert(user_id = userId, room_id = roomId, movie_id = movieId))
                }
                Log.d("ChooseActivity", "Saved movie choice: user=$userId, movie=$movieId")
                Toast.makeText(this@ChooseActivity, "Фильм выбран, ожидаем других...", Toast.LENGTH_SHORT).show()

                // Переключаемся на следующий фильм
                currentMovieIndex = (currentMovieIndex + 1) % movieList.size
                displayMovie(movieList[currentMovieIndex])
            } catch (e: Exception) {
                Log.e("ChooseActivity", "Error saving movie choice: ${e.message}")
                Toast.makeText(this@ChooseActivity, "Ошибка сохранения выбора: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startPolling() {
        coroutineScope.launch {
            while (isActive) {
                checkForMatch()
                delay(2_000) // Опрос каждые 2 секунды
            }
        }
    }

    private fun checkForMatch() {
        coroutineScope.launch {
            try {
                // Получаем всех активных пользователей в комнате
                val users = withContext(Dispatchers.IO) {
                    Supabase.client.from("user_sessions")
                        .select {
                            filter {
                                eq("room_id", roomId)
                                eq("is_online", true)
                            }
                        }
                        .decodeList<UserSession>()
                }
                val userCount = users.size
                Log.d("ChooseActivity", "Active users in room: $userCount")

                // Получаем все выборы для комнаты
                val choices = withContext(Dispatchers.IO) {
                    Supabase.client.from("movie_choices")
                        .select {
                            filter {
                                eq("room_id", roomId)
                            }
                        }
                        .decodeList<MovieChoice>()
                }
                Log.d("ChooseActivity", "Choices: $choices")

                // Проверяем, все ли пользователи сделали выбор
                val choiceCount = choices.distinctBy { it.user_id }.size
                if (choiceCount < userCount) {
                    Log.d("ChooseActivity", "Not all users have chosen yet ($choiceCount/$userCount)")
                    return@launch
                }

                // Проверяем, есть ли совпадение
                val movieIds = choices.map { it.movie_id }
                val commonMovieId = movieIds.groupingBy { it }.eachCount().filter { it.value == userCount }.keys.firstOrNull()
                if (commonMovieId != null) {
                    Log.d("ChooseActivity", "Match found! Common movie ID: $commonMovieId")
                    val selectedMovie = movieList.find { it.id == commonMovieId }
                    if (selectedMovie != null) {
                        displayFinalMovie(selectedMovie)
                    } else {
                        Log.e("ChooseActivity", "Selected movie not found in movieList")
                        Toast.makeText(this@ChooseActivity, "Ошибка: фильм не найден", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.d("ChooseActivity", "No match yet, continue choosing")
                }
            } catch (e: Exception) {
                Log.e("ChooseActivity", "Error checking match: ${e.message}")
                Toast.makeText(this@ChooseActivity, "Ошибка проверки совпадения: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun displayMovie(movie: Movie) {
        movieNameTextView.text = movie.name
        movieDescriptionTextView.text = movie.description
        movieGenreTextView.text = movie.genres.joinToString(", ") { it.name }
        movieYearTextView.text = "Year: ${movie.year}"
        movieLengthTextView.text = "Length: ${movie.movieLength} min"
        movieRatingTextView.text = "Rating: ${movie.rating.kp}"

        // Загрузка постера через Picasso
        Picasso.get()
            .load(movie.poster.url)
            .into(moviePosterImageView)
    }

    private fun displayFinalMovie(movie: Movie) {
        yesButton.isEnabled = false
        noButton.isEnabled = false
        messageTextView.text = "Фильм выбран всеми: ${movie.name}!"
        displayMovie(movie)
    }

    private fun clearMovieDetails() {
        movieNameTextView.text = ""
        movieDescriptionTextView.text = ""
        movieGenreTextView.text = ""
        movieYearTextView.text = ""
        movieLengthTextView.text = ""
        movieRatingTextView.text = ""
        moviePosterImageView.setImageDrawable(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}

@Serializable
data class MovieChoiceInsert(
    val user_id: String,
    val room_id: String,
    val movie_id: Long
)

@Serializable
data class MovieChoice(
    val id: Long,
    val user_id: String,
    val room_id: String,
    val movie_id: Long,
    val created_at: String
)

