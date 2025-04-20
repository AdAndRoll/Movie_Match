package com.example.try2

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.RangeSlider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
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

        yearRangeSlider = findViewById(R.id.yearRangeSlider)
        yearRangeText = findViewById(R.id.yearRangeText)
        genreSpinner = findViewById(R.id.genreSpinner)
        searchButton = findViewById(R.id.searchButton)

        yearRangeSlider.setValues(1990f, 2024f)

        yearRangeSlider.addOnChangeListener { _, _, _ ->
            val range = yearRangeSlider.values
            val startYear = range[0].toInt()
            val endYear = range[1].toInt()
            yearRangeText.text = "Выбранные года: $startYear - $endYear"
        }

        val genres = listOf("драма", "ужасы", "комедия", "боевик", "фантастика")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genres)
        genreSpinner.adapter = adapter

        searchButton.setOnClickListener {
            val selectedYears = yearRangeSlider.values
            val fromYear = selectedYears[0].toInt()
            val toYear = selectedYears[1].toInt()
            val selectedGenre = genreSpinner.selectedItem as String
            val roomId = intent.getStringExtra("ROOM_CODE") ?: return@setOnClickListener

            saveFiltersToFirebase(selectedGenre, fromYear, toYear)
            saveUserFiltersSelected(roomId)

            checkAllUsersReadyForSearch(roomId) {
                loadAllFiltersAndSearch(roomId)
            }
        }
    }

    private fun saveFiltersToFirebase(genre: String, fromYear: Int, toYear: Int) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val roomId = intent.getStringExtra("ROOM_CODE") ?: return

        val filtersRef = FirebaseDatabase.getInstance()
            .getReference("rooms")
            .child(roomId)
            .child("filters")
            .child(userId)

        val filterData = mapOf(
            "genres" to listOf(genre),
            "years" to mapOf(
                "from" to fromYear,
                "to" to toYear
            )
        )

        filtersRef.setValue(filterData)
            .addOnSuccessListener {
                Toast.makeText(this, "Фильтры сохранены", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Ошибка при сохранении фильтров", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserFiltersSelected(roomId: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val userRef = FirebaseDatabase.getInstance()
            .getReference("rooms")
            .child(roomId)
            .child("users")
            .child(userId)

        userRef.child("filtersSelected").setValue(true)
    }

    private fun checkAllUsersReadyForSearch(roomId: String, onAllReady: () -> Unit) {
        val usersRef = FirebaseDatabase.getInstance()
            .getReference("rooms")
            .child(roomId)
            .child("users")

        usersRef.get().addOnSuccessListener { snapshot ->
            val allReady = snapshot.children.all { userSnap ->
                val isSelected = userSnap.child("filtersSelected").getValue(Boolean::class.java) ?: false
                isSelected
            }

            if (allReady) {
                onAllReady()
            } else {
                Toast.makeText(this, "Ожидание других участников...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadAllFiltersAndSearch(roomId: String) {
        val filtersRef = FirebaseDatabase.getInstance()
            .getReference("rooms")
            .child(roomId)
            .child("filters")

        filtersRef.get().addOnSuccessListener { dataSnapshot ->
            val allGenres = mutableSetOf<String>()
            var minYear = Int.MAX_VALUE
            var maxYear = Int.MIN_VALUE

            for (userSnapshot in dataSnapshot.children) {
                val userFilters = userSnapshot.value as? Map<*, *> ?: continue
                val genres = userFilters["genres"] as? List<*> ?: continue
                val years = userFilters["years"] as? Map<*, *> ?: continue

                genres.forEach { genre ->
                    (genre as? String)?.let { allGenres.add(it) }
                }

                val from = (years["from"] as? Long)?.toInt() ?: continue
                val to = (years["to"] as? Long)?.toInt() ?: continue

                if (from < minYear) minYear = from
                if (to > maxYear) maxYear = to
            }

            if (allGenres.isEmpty() || minYear == Int.MAX_VALUE || maxYear == Int.MIN_VALUE) {
                Toast.makeText(this, "Недостаточно данных для поиска", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            val genresAsString = allGenres.joinToString(",")
            val yearRangeList = listOf(minYear.toString(), maxYear.toString())

            searchMovies(genresAsString, yearRangeList)
        }.addOnFailureListener {
            Toast.makeText(this, "Ошибка получения фильтров", Toast.LENGTH_SHORT).show()
        }
    }

    fun searchMovies(genre: String, yearRange: List<String>) {
        val apiClient = MovieApiClient("T31MD52-6ZJ4RVQ-KBK3C0T-0AVR9WS")
        val genres = listOf(genre)

        val encodedGenre = try {
            URLEncoder.encode(genres[0], "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
            genres[0]
        }

        val yearRangeString = when {
            yearRange.size == 1 -> yearRange[0]
            yearRange.size == 2 -> "${yearRange[0]}-${yearRange[1]}"
            else -> yearRange.joinToString(",")
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val movieSearchResponse = apiClient.searchMovies(genres, listOf(yearRangeString))
                println("Response: $movieSearchResponse")

                val url =
                    "https://api.kinopoisk.dev/v1.4/movie?page=1&limit=5&selectFields=id&selectFields=name&selectFields=year&selectFields=movieLength&selectFields=rating&selectFields=description&selectFields=genres&selectFields=poster&notNullFields=name&notNullFields=description&notNullFields=poster.url&sortField=rating.kp&sortType=-1&type=movie&year=$yearRangeString&genres.name=$encodedGenre"
                println("Request URL: $url")

                withContext(Dispatchers.Main) {
                    if (movieSearchResponse != null) {
                        val movies = movieSearchResponse.docs
                        if (!movies.isNullOrEmpty()) {
                            println("First movie: ${movies[0].name}")
                            val intent =
                                Intent(this@MovieSearchActivity, ChooseActivity::class.java)
                            intent.putParcelableArrayListExtra("MOVIES_LIST", ArrayList(movies))
                            startActivity(intent)
                        } else {
                            println("No movies found")
                            Toast.makeText(
                                this@MovieSearchActivity,
                                "No movies found",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        println("Failed to fetch movie data")
                        Toast.makeText(
                            this@MovieSearchActivity,
                            "Failed to fetch movie data",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    e.printStackTrace()
                    Toast.makeText(
                        this@MovieSearchActivity,
                        "Error occurred: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
