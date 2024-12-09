package com.example.try2

data class MovieSearchResponse(
    val docs: List<Movie>,  // Используем docs, так как это ключ в ответе от API
    val total: Int,
    val limit: Int,
    val page: Int,
    val pages: Int
)

data class Movie(
    val id: Long,  // В API id фильмов передается как Long, изменим тип
    val name: String,
    val year: Int,
    val movieLength: Int,
    val rating: Rating,
    val description: String,
    val genres: List<Genre>,
    val poster: Poster
)

data class Rating(
    val kp: Float  // Значение рейтинга возвращается как число с плавающей точкой
)

data class Genre(
    val name: String
)

data class Poster(
    val url: String
)
