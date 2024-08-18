package com.example.try2

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MovieSearchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_movie_search)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE)
        val selectedGenres = sharedPreferences.getStringSet("selectedGenres", emptySet())?.toList() ?: emptyList()
        val selectedYears = sharedPreferences.getStringSet("selectedYears", emptySet())?.toList() ?: emptyList()
        // Имитируем список фильмов (в реальном приложении это мог быть запрос к базе данных)
        val movies = listOf(
            Movie("Movie1", arrayOf("Биография", "Боевик"), "2021"),
            Movie("Movie2", arrayOf("Боевик"), "2020"),
            Movie("Movie3", arrayOf("Биография", "Вестерн"), "2022"),
            Movie("Movie4", arrayOf("Военный"), "2021")
        )

        // Фильтруем фильмы по выбранным жанрам и годам
        val filteredMovies = movies.filter { movie ->
            matchesGenres(movie.Genre, selectedGenres) && matchesYears(movie.Year, selectedYears)
        }


        val textView = findViewById<TextView>(R.id.textViewMovie)


        val yesButton = findViewById<Button>(R.id.buttonYes)
        yesButton.setOnClickListener{

            //для версии на 1, поздравление, фильм выбран
        }
        val NoButton = findViewById<Button>(R.id.buttonNo)
        NoButton.setOnClickListener{
            //для любой версии следующий фильм соответствующий параметрам
        }

        if (filteredMovies.isNotEmpty()) {
            textView.text = filteredMovies.joinToString("\n") { it.Name }
        } else {
            textView.text = "Фильмы не найдены по выбранным параметрам"
        }
    }


    // Проверка соответствия жанрам
    private fun matchesGenres(movieGenres: Array<String>, selectedGenres: List<String>): Boolean {
        return movieGenres.any { it in selectedGenres }
    }

    // Проверка соответствия годам
    private fun matchesYears(movieYear: String, selectedYears: List<String>): Boolean {
        for (year in selectedYears) {
            when {
                year.contains("–") -> {
                    // Обработка интервала, например, "2000-2010"
                    val (startYear, endYear) = year.split("–").map { it.toInt() }
                    if (movieYear.toInt() in startYear..endYear) return true
                }
                year.startsWith("до") -> {
                    // Обработка интервала, например, "до 2000"
                    val endYear = year.substringAfter("до ").toInt()
                    if (movieYear.toInt() < endYear) return true
                }
                else -> {
                    // Обработка конкретного года, например, "2021"
                    if (movieYear == year) return true
                }
            }
        }
        return false
    }

}
