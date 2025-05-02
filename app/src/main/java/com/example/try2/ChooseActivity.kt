package com.example.try2

import android.os.Bundle
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
import kotlinx.coroutines.launch
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

        // Загрузка фильмов
        loadMovies()
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
                        messageTextView.text = "Поздравляю, вы выбрали фильм!"
                        clearMovieDetails()
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