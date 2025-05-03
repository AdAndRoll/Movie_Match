package com.example.try2

import android.os.Parcel
import android.os.Parcelable
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// Ответ от API с фильмами
data class MovieSearchResponse(
    val docs: List<Movie>,
    val total: Int,
    val limit: Int,
    val page: Int,
    val pages: Int
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.createTypedArrayList(Movie) ?: emptyList(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeTypedList(docs)
        parcel.writeInt(total)
        parcel.writeInt(limit)
        parcel.writeInt(page)
        parcel.writeInt(pages)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<MovieSearchResponse> {
        override fun createFromParcel(parcel: Parcel): MovieSearchResponse {
            return MovieSearchResponse(parcel)
        }

        override fun newArray(size: Int): Array<MovieSearchResponse?> {
            return arrayOfNulls(size)
        }
    }
}

// Класс для room_results
@Serializable
data class RoomResults(
    val room_id: String,
    val movies: List<Movie>,
    val created_at: String
)

// Класс фильма
@Serializable
data class Movie(
    val id: Long,
    val name: String,
    val year: Int,
    val movieLength: Int? = null, // Поддержка null
    val rating: Rating,
    val description: String,
    val genres: List<Genre>,
    val poster: Poster
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readInt().let { if (it == -1) null else it }, // -1 как индикатор null
        parcel.readParcelable(Rating::class.java.classLoader) ?: Rating(0f, 0f, null, 0f, 0f),
        parcel.readString() ?: "",
        parcel.createTypedArrayList(Genre) ?: emptyList(),
        parcel.readParcelable(Poster::class.java.classLoader) ?: Poster("", "")
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(name)
        parcel.writeInt(year)
        parcel.writeInt(movieLength ?: -1) // -1 как индикатор null
        parcel.writeParcelable(rating, flags)
        parcel.writeString(description)
        parcel.writeTypedList(genres)
        parcel.writeParcelable(poster, flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Movie> {
        override fun createFromParcel(parcel: Parcel): Movie {
            return Movie(parcel)
        }

        override fun newArray(size: Int): Array<Movie?> {
            return arrayOfNulls(size)
        }
    }
}

// Класс рейтинга
@Serializable
data class Rating(
    val kp: Float,
    val imdb: Float? = null,
    val await: Float? = null,
    @SerialName("filmCritics") val filmCritics: Float? = null,
    @SerialName("russianFilmCritics") val russianFilmCritics: Float? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readFloat(),
        parcel.readFloat().let { if (it == -1f) null else it },
        parcel.readFloat().let { if (it == -1f) null else it },
        parcel.readFloat().let { if (it == -1f) null else it },
        parcel.readFloat().let { if (it == -1f) null else it }
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeFloat(kp)
        parcel.writeFloat(imdb ?: -1f)
        parcel.writeFloat(await ?: -1f)
        parcel.writeFloat(filmCritics ?: -1f)
        parcel.writeFloat(russianFilmCritics ?: -1f)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Rating> {
        override fun createFromParcel(parcel: Parcel): Rating {
            return Rating(parcel)
        }

        override fun newArray(size: Int): Array<Rating?> {
            return arrayOfNulls(size)
        }
    }
}

// Класс жанра
@Serializable
data class Genre(
    val name: String
) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readString() ?: "")

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Genre> {
        override fun createFromParcel(parcel: Parcel): Genre {
            return Genre(parcel)
        }

        override fun newArray(size: Int): Array<Genre?> {
            return arrayOfNulls(size)
        }
    }
}

// Класс постера
@Serializable
data class Poster(
    val url: String,
    @SerialName("previewUrl") val previewUrl: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(url)
        parcel.writeString(previewUrl)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Poster> {
        override fun createFromParcel(parcel: Parcel): Poster {
            return Poster(parcel)
        }

        override fun newArray(size: Int): Array<Poster?> {
            return arrayOfNulls(size)
        }
    }
}