package com.example.try2

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Response
import retrofit2.http.Header

// Интерфейс API, использующий suspend для асинхронных запросов
interface MovieApiService {

    @GET("v1.4/movie")
    suspend fun searchMovies(
        @Header("X-API-KEY") apiKey: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 15,
        @Query("year") years: List<String>,  // Строковый список для года или диапазона
        @Query("genres.name") genres: List<String>,  // Список жанров
        @Query("selectFields") selectFields: List<String> = listOf("id", "name", "year", "movieLength", "rating", "description", "genres", "poster"),
        @Query("notNullFields") notNullFields: List<String> = listOf("name", "description", "poster.url"),
        @Query("sortField") sortField: String = "rating.kp",
        @Query("sortType") sortType: String = "-1",
        @Query("type") type: String = "movie"
    ): Response<MovieSearchResponse>
}

// Класс клиента для взаимодействия с API
class MovieApiClient(private val apiKey: String) {

    // Настройка логирования
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Логируем тело запроса и ответа
    }

    // Настройка клиента OkHttp с интерцептором
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)  // Добавляем интерцептор для логирования
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://api.kinopoisk.dev/")  // Базовый URL для API
        .client(okHttpClient)  // Используем OkHttpClient с логированием
        .addConverterFactory(GsonConverterFactory.create())  // Использование Gson для конвертации JSON в объекты
        .build()

    private val apiService: MovieApiService = retrofit.create(MovieApiService::class.java)

    // Метод для выполнения запроса с использованием корутин
    suspend fun searchMovies(genres: List<String>, yearRange: List<String>): MovieSearchResponse? {
        // Выполнение запроса и получение ответа
        println("Searching for movies with genres: $genres, year range: $yearRange")
        val response = apiService.searchMovies(
            genres = genres,
            years = yearRange,  // Передаем список годов и диапазонов
            apiKey = apiKey,
            selectFields = listOf("id", "name", "year", "movieLength", "rating", "description", "genres", "poster"),  // Передаем как массив строк
            notNullFields = listOf("name", "description", "poster.url")  // Передаем как массив строк
        )

        // Возвращаем тело ответа, если запрос успешен, или null, если нет
        return if (response.isSuccessful) {
            println("Response: ${response.body()}")
            response.body()
        } else {
            println("Error: ${response.code()}")
            println("Response body: ${response.errorBody()?.string()}")
            null
        }
    }
}

// Пример использования метода searchMovies в корутине
