package com.example.try2

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.squareup.picasso.MemoryPolicy
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ChooseActivity : AppCompatActivity() {

    private lateinit var movieNameTextView: TextView
    private lateinit var movieDescriptionTextView: TextView
    private lateinit var movieGenreTextView: TextView
    private lateinit var movieYearTextView: TextView
    private lateinit var movieLengthTextView: TextView
    private lateinit var movieRatingTextView: TextView
    private lateinit var moviePosterImageView: ImageView // Только один ImageView
    private lateinit var yesButton: Button
    private lateinit var noButton: Button
    private lateinit var messageTextView: TextView
    private lateinit var roomId: String
    private lateinit var userId: String
    private var movieList: List<Movie> = emptyList()
    private var currentMovieIndex: Int = 0
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val posterCache = mutableMapOf<Long, Bitmap>() // Кэш для 15 постеров

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
        moviePosterImageView = findViewById(R.id.moviePosterImageView1)
        yesButton = findViewById(R.id.yesButton)
        noButton = findViewById(R.id.noButton)
        messageTextView = findViewById(R.id.messageTextView)

        // Получение room_id и movie_list
        roomId = intent.getStringExtra("ROOM_ID") ?: run {
            Toast.makeText(this, "Room ID not provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val movieListJson = intent.getStringExtra("MOVIE_LIST")
        userId = UserManager.getUserId(this)

        // Проверяем, есть ли переданный список фильмов
        if (movieListJson != null) {
            try {
                movieList = Json.decodeFromString<List<Movie>>(movieListJson)
                Log.d("ChooseActivity", "Received ${movieList.size} movies from Intent")
            } catch (e: SerializationException) {
                Log.e("ChooseActivity", "Error decoding movie list: ${e.message}")
                Toast.makeText(this, "Ошибка парсинга фильмов: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // Загрузка фильмов и запуск опроса
        if (movieList.isEmpty()) {
            loadMoviesFromSupabase()
        } else {
            setupMovieDisplay()
        }
        startPolling()
    }

    private fun loadMoviesFromSupabase() {
        coroutineScope.launch {
            try {
                Log.d("ChooseActivity", "Loading movies from Supabase for room_id: $roomId")
                val response = Supabase.client.from("room_results")
                    .select {
                        filter {
                            eq("room_id", roomId)
                        }
                    }
                    .decodeSingle<RoomResults>()
                movieList = response.movies
                Log.d("ChooseActivity", "Loaded ${movieList.size} movies from Supabase")
                setupMovieDisplay()
            } catch (e: SerializationException) {
                Log.e("ChooseActivity", "Serialization error: ${e.message}")
                Toast.makeText(this@ChooseActivity, "Ошибка парсинга данных: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            } catch (e: Exception) {
                Log.e("ChooseActivity", "General error: ${e.message}")
                Toast.makeText(this@ChooseActivity, "Ошибка загрузки фильмов: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun setupMovieDisplay() {
        if (movieList.isNotEmpty()) {
            // Предзагрузка постеров
            preloadPosters(currentMovieIndex)
            coroutineScope.launch {
                delay(1000) // Дать время на предзагрузку
                currentMovieIndex = 0
                displayMovie(movieList[currentMovieIndex])
                // Установка обработчиков кнопок после первого отображения
                yesButton.setOnClickListener {
                    saveMovieChoice(movieList[currentMovieIndex].id)
                }
                noButton.setOnClickListener {
                    // Очищаем кэш текущего фильма
                    posterCache.remove(movieList[currentMovieIndex].id)
                    Log.d("ChooseActivity", "Cleared poster cache for ${movieList[currentMovieIndex].name}")
                    currentMovieIndex = (currentMovieIndex + 1) % movieList.size
                    preloadPosters(currentMovieIndex)
                    displayMovie(movieList[currentMovieIndex])
                }
            }
        } else {
            Log.e("ChooseActivity", "No movies found for room_id: $roomId")
            Toast.makeText(this@ChooseActivity, "Фильмы не найдены", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun preloadPosters(startIndex: Int) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                // Предзагружаем текущий и следующие 14 фильмов (всего 15)
                val endIndex = minOf(startIndex + 15, movieList.size)
                for (i in startIndex until endIndex) {
                    val movie = movieList[i]
                    if (!posterCache.containsKey(movie.id)) {
                        try {
                            val bitmap = Picasso.get()
                                .load(movie.poster.url)
                                .resize(360, 540)
                                .centerCrop()
                                .memoryPolicy(MemoryPolicy.NO_CACHE)
                                .get()
                            posterCache[movie.id] = bitmap
                            Log.d("ChooseActivity", "Preloaded poster for ${movie.name} (id: ${movie.id}) into cache")
                        } catch (e: Exception) {
                            Log.e("ChooseActivity", "Error preloading poster for ${movie.name} (id: ${movie.id}): ${e.message}")
                        }
                    }
                }
                // Предзагружаем остальные в кэш Picasso
                movieList.forEachIndexed { index, movie ->
                    if (index >= endIndex && !posterCache.containsKey(movie.id)) {
                        try {
                            Picasso.get()
                                .load(movie.poster.url)
                                .resize(360, 540)
                                .centerCrop()
                                .memoryPolicy(MemoryPolicy.NO_CACHE)
                                .fetch()
                            Log.d("ChooseActivity", "Preloaded poster for ${movie.name} (id: ${movie.id}) into Picasso cache")
                        } catch (e: Exception) {
                            Log.e("ChooseActivity", "Error preloading poster for ${movie.name} (id: ${movie.id}): ${e.message}")
                        }
                    }
                }
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
                preloadPosters(currentMovieIndex)
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
        // Обновляем текстовые поля
        movieNameTextView.text = movie.name
        movieDescriptionTextView.text = movie.description
        movieGenreTextView.text = movie.genres.joinToString(", ") { it.name }
        movieYearTextView.text = "Year: ${movie.year}"
        movieLengthTextView.text = "Length: ${movie.movieLength} min"
        movieRatingTextView.text = "Rating: ${movie.rating.kp}"

        // Загружаем текущий постер
        val startTime = System.currentTimeMillis()
        val bitmap = posterCache[movie.id]
        if (bitmap != null) {
            moviePosterImageView.setImageBitmap(bitmap)
            moviePosterImageView.alpha = 1f
            val elapsed = System.currentTimeMillis() - startTime
            Log.d("ChooseActivity", "Loaded poster for ${movie.name} (id: ${movie.id}) from cache in $elapsed ms")
        } else {
            Log.w("ChooseActivity", "Poster for ${movie.name} (id: ${movie.id}) not in cache, loading synchronously")
            try {
                val bitmap = Picasso.get()
                    .load(movie.poster.url)
                    .resize(360, 540)
                    .centerCrop()
                    .memoryPolicy(MemoryPolicy.NO_CACHE)
                    .get()
                moviePosterImageView.setImageBitmap(bitmap)
                moviePosterImageView.alpha = 1f
                val elapsed = System.currentTimeMillis() - startTime
                Log.d("ChooseActivity", "Loaded poster for ${movie.name} (id: ${movie.id}) synchronously in $elapsed ms")
            } catch (e: Exception) {
                Log.e("ChooseActivity", "Error loading poster for ${movie.name} (id: ${movie.id}): ${e.message}")
                moviePosterImageView.alpha = 1f
                Toast.makeText(this@ChooseActivity, "Ошибка загрузки постера: ${movie.name}", Toast.LENGTH_SHORT).show()
            }
        }

        // Немедленно начинаем предзагрузку следующего постера
        val nextIndex = (currentMovieIndex + 1) % movieList.size
        val nextMovie = movieList[nextIndex]
        if (!posterCache.containsKey(nextMovie.id)) {
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val bitmap = Picasso.get()
                            .load(nextMovie.poster.url)
                            .resize(360, 540)
                            .centerCrop()
                            .memoryPolicy(MemoryPolicy.NO_CACHE)
                            .get()
                        posterCache[nextMovie.id] = bitmap
                        Log.d("ChooseActivity", "Preloaded next poster for ${nextMovie.name} (id: ${nextMovie.id}) into cache")
                    } catch (e: Exception) {
                        Log.e("ChooseActivity", "Error preloading next poster for ${nextMovie.name} (id: ${nextMovie.id}): ${e.message}")
                    }
                }
            }
        } else {
            Log.d("ChooseActivity", "Next poster for ${nextMovie.name} (id: ${nextMovie.id}) already in cache")
        }
    }

    private fun displayFinalMovie(movie: Movie) {
        yesButton.isEnabled = false
        noButton.isEnabled = false
        messageTextView.text = "Фильм выбран всеми: ${movie.name}!"
        val startTime = System.currentTimeMillis()
        val bitmap = posterCache[movie.id]
        if (bitmap != null) {
            moviePosterImageView.setImageBitmap(bitmap)
            moviePosterImageView.alpha = 1f
            val elapsed = System.currentTimeMillis() - startTime
            Log.d("ChooseActivity", "Loaded final poster for ${movie.name} (id: ${movie.id}) from cache in $elapsed ms")
        } else {
            Log.w("ChooseActivity", "Final poster for ${movie.name} (id: ${movie.id}) not in cache, loading synchronously")
            try {
                val bitmap = Picasso.get()
                    .load(movie.poster.url)
                    .resize(360, 540)
                    .centerCrop()
                    .memoryPolicy(MemoryPolicy.NO_CACHE)
                    .get()
                moviePosterImageView.setImageBitmap(bitmap)
                moviePosterImageView.alpha = 1f
                val elapsed = System.currentTimeMillis() - startTime
                Log.d("ChooseActivity", "Loaded final poster for ${movie.name} (id: ${movie.id}) synchronously in $elapsed ms")
            } catch (e: Exception) {
                Log.e("ChooseActivity", "Error loading final poster for ${movie.name} (id: ${movie.id}): ${e.message}")
                moviePosterImageView.alpha = 1f
                Toast.makeText(this@ChooseActivity, "Ошибка загрузки постера: ${movie.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        UserSessionManager.updateOnlineStatus(this, roomId, userId, isOnline = false)
        coroutineScope.cancel()
        posterCache.clear() // Очищаем кэш Bitmap
        // Инвалидируем кэш Picasso для всех постеров
        movieList.forEach { movie ->
            Picasso.get().invalidate(movie.poster.url)
        }
        Log.d("ChooseActivity", "Cleared poster cache and invalidated Picasso cache")
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