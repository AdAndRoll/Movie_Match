package com.example.try2

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.RangeSlider
import kotlinx.coroutines.*
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

class MovieSearchActivity : AppCompatActivity() {

    private lateinit var yearRangeSlider: RangeSlider
    private lateinit var yearRangeText: TextView
    private lateinit var genreSpinner: Spinner
    private lateinit var searchButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_movie_search)

        // Инициализация компонентов
        yearRangeSlider = findViewById(R.id.yearRangeSlider)
        yearRangeText = findViewById(R.id.yearRangeText)
        genreSpinner = findViewById(R.id.genreSpinner)
        searchButton = findViewById(R.id.searchButton)
        yearRangeSlider.setValues(1990f, 2024f)

        // Установка обработчика ползунка
        yearRangeSlider.addOnChangeListener { _, _, _ ->
            val range = yearRangeSlider.values
            val startYear = range[0].toInt()
            val endYear = range[1].toInt()
            yearRangeText.text = "Выбранные года: $startYear - $endYear"
        }

        // Настройка выпадающего списка для жанров
        val genres = listOf("драма", "ужасы", "комедия", "боевик", "фантастика")
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            genres
        )
        genreSpinner.adapter = adapter

        // Обработчик кнопки поиска
        searchButton.setOnClickListener {
            val selectedYears = yearRangeSlider.values
            // Преобразуем значения в строковый список (List<String>)
            val yearRange = listOf("${selectedYears[0].toInt()}", "${selectedYears[1].toInt()}")
            val selectedGenre = genreSpinner.selectedItem as String
            println("Searching for movies with genres: $selectedGenre, year range: $yearRange")
            // Вызов API с выбранными параметрами
            searchMovies(selectedGenre, yearRange)
        }
    }

    // Изменения в параметре yearRange: теперь это список строк
    fun searchMovies(genre: String, yearRange: List<String>) {
        val apiClient = MovieApiClient("T31MD52-6ZJ4RVQ-KBK3C0T-0AVR9WS")
        val genres = listOf(genre)

        // Кодируем жанр для передачи в URL
        val encodedGenre = try {
            URLEncoder.encode(genres[0], "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
            genres[0] // Если кодировка не удалась, используем исходное значение
        }

        // Запуск корутины для выполнения запроса в фоновом потоке
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Вызов API в фоновом потоке
                val movieSearchResponse = apiClient.searchMovies(genres, yearRange)
                println("Response: $movieSearchResponse")

                // Формируем запрос URL с передачей параметров
                val url = "https://api.kinopoisk.dev/v1.4/movie?page=1&limit=5&selectFields=id&selectFields=name&selectFields=year&selectFields=movieLength&selectFields=rating&selectFields=description&selectFields=genres&selectFields=poster&notNullFields=name&notNullFields=description&notNullFields=poster.url&sortField=rating.kp&sortType=-1&type=movie&year=${yearRange[0]}&year=${yearRange[1]}&genres.name=$encodedGenre"
                println("Request URL: $url")

                // После выполнения запроса, возвращаемся на главный поток для обновления UI
                withContext(Dispatchers.Main) {
                    if (movieSearchResponse != null) {
                        val movies = movieSearchResponse.docs
                        if (!movies.isNullOrEmpty()) {
                            // Выводим название первого фильма в логи
                            println("First movie: ${movies[0].name}")
                            Toast.makeText(this@MovieSearchActivity, "First movie: ${movies[0].name}", Toast.LENGTH_SHORT).show()
                        } else {
                            println("No movies found")
                            Toast.makeText(this@MovieSearchActivity, "No movies found", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        println("Failed to fetch movie data")
                        Toast.makeText(this@MovieSearchActivity, "Failed to fetch movie data", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Обработка ошибок (например, отсутствие интернета)
                    e.printStackTrace()
                    Toast.makeText(this@MovieSearchActivity, "Error occurred: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
