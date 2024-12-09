package com.example.try2

data class MovieSearchResponse(
    val movies: List<Movie>
)

data class Movie(
    val id: Int,
    val name: String,
    val year: Int,
    val movieLength: Int,
    val rating: Rating,
    val description: String,
    val genres: List<Genre>,
    val poster: Poster
)

data class Rating(
    val kp: Float
)

data class Genre(
    val name: String
)

data class Poster(
    val url: String
)

