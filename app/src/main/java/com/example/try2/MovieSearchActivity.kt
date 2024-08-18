package com.example.try2

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MovieSearchActivity : AppCompatActivity() {
    private var currentMovieIndex = 0
    private lateinit var textView: TextView
    private lateinit var filteredMovies: List<Movie>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_movie_search)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // Получаем данные из SharedPreferences
        val sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE)
        val selectedGenres = sharedPreferences.getStringSet("selectedGenres", emptySet())?.toList() ?: emptyList()
        val selectedYears = sharedPreferences.getStringSet("selectedYears", emptySet())?.toList() ?: emptyList()

        // Имитируем список фильмов
        val movies = listOf(
            Movie("Movie1", arrayOf("Аниме", "Биография"), "2021"),
            Movie("Movie2", arrayOf("Биография"), "2020"),
            Movie("Movie3", arrayOf("Боевик", "Вестерн"), "2005"),
            Movie("Movie4", arrayOf("Военный"), "1999"),
            Movie("Movie5", arrayOf("Аниме"), "2015")
        )

        // Фильтруем фильмы по выбранным жанрам и годам
        filteredMovies = movies.filter { movie ->
            val genreMatch = matchesGenres(movie.Genre, selectedGenres)
            val yearMatch = matchesYears(movie.Year, selectedYears)

            // Логируем каждую проверку для отладки
            Log.d("MovieSearchActivity", "Checking movie: ${movie.Name}")
            Log.d("MovieSearchActivity", "Genre match: $genreMatch, Year match: $yearMatch")

            genreMatch && yearMatch
        }
        // Логируем результат фильтрации
        Log.d("MovieSearchActivity", "Filtered Movies: ${filteredMovies.map { it.Name }}")

        textView = findViewById(R.id.textViewMovie)

        // Настройка кнопок
        val yesButton = findViewById<Button>(R.id.buttonYes)
        val noButton = findViewById<Button>(R.id.buttonNo)

        yesButton.setOnClickListener {
            onYesClicked()
        }

        noButton.setOnClickListener {
            onNoClicked()
        }

        // Показ первого фильма
        showNextMovie()
    }

    private fun showNextMovie() {
        if (currentMovieIndex < filteredMovies.size) {
            val currentMovie = filteredMovies[currentMovieIndex]
            textView.text = "Подходит ли вам фильм: ${currentMovie.Name}?"
        } else {
            textView.text = "Фильмы, подходящие под критерии, закончились."

        }
    }

    private fun onYesClicked() {
        textView.text = "Поздравляю, вы выбрали фильм: ${filteredMovies[currentMovieIndex].Name}!"

    }

    private fun onNoClicked() {
        currentMovieIndex++
        showNextMovie()
    }

    // Проверка соответствия жанрам
    private fun matchesGenres(movieGenres: Array<String>, selectedGenres: List<String>): Boolean {
        return selectedGenres.any { it in movieGenres }
    }

    // Проверка соответствия годам
    private fun matchesYears(movieYear: String, selectedYears: List<String>): Boolean {
        for (year in selectedYears) {
            when {
                year.contains("–") -> {
                    val (startYear, endYear) = year.split("–").map { it.toInt() }
                    if (movieYear.toInt() in startYear..endYear) return true
                }
                year.startsWith("до") -> {
                    val endYear = year.substringAfter("до ").toInt()
                    if (movieYear.toInt() < endYear) return true
                }
                else -> {
                    if (movieYear == year) return true
                }
            }
        }
        return false
    }
}
