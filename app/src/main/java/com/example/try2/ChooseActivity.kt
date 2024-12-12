package com.example.try2

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.squareup.picasso.Picasso

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

    private var movieList: ArrayList<Movie>? = null
    private var currentMovieIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose)

        // Initialize views
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

        // Get the list of movies passed via Intent
        movieList = intent.getParcelableArrayListExtra("MOVIES_LIST")

        // Show the first movie in the list
        movieList?.let { movies ->
            displayMovie(movies[currentMovieIndex])

            // Set listeners for Yes and No buttons
            yesButton.setOnClickListener {
                messageTextView.text = "Поздравляю, вы выбрали фильм!"
                clearMovieDetails()
            }

            noButton.setOnClickListener {
                // Show the next movie in the list
                currentMovieIndex = (currentMovieIndex + 1) % movies.size
                displayMovie(movies[currentMovieIndex])
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

        // Load the poster using Picasso
        Picasso.get()
            .load(movie.poster.url) // Load the poster image URL
            .into(moviePosterImageView) // Set the loaded image into the ImageView
    }

    private fun clearMovieDetails() {
        movieNameTextView.text = ""
        movieDescriptionTextView.text = ""
        movieGenreTextView.text = ""
        movieYearTextView.text = ""
        movieLengthTextView.text = ""
        movieRatingTextView.text = ""
        moviePosterImageView.setImageDrawable(null) // Clear the poster
    }
}
