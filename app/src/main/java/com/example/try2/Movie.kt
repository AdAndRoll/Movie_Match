package com.example.try2

import android.os.Parcel
import android.os.Parcelable

// Ответ от API с фильмами
data class MovieSearchResponse(
    val docs: List<Movie>,  // Используем docs, так как это ключ в ответе от API
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

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<MovieSearchResponse> {
        override fun createFromParcel(parcel: Parcel): MovieSearchResponse {
            return MovieSearchResponse(parcel)
        }

        override fun newArray(size: Int): Array<MovieSearchResponse?> {
            return arrayOfNulls(size)
        }
    }
}

// Класс фильма
data class Movie(
    val id: Long,  // В API id фильмов передается как Long, изменим тип
    val name: String,
    val year: Int,
    val movieLength: Int,
    val rating: Rating,
    val description: String,
    val genres: List<Genre>,
    val poster: Poster
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readInt(),
        parcel.readParcelable(Rating::class.java.classLoader) ?: Rating(0f),
        parcel.readString() ?: "",
        parcel.createTypedArrayList(Genre) ?: emptyList(),
        parcel.readParcelable(Poster::class.java.classLoader) ?: Poster("")
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(name)
        parcel.writeInt(year)
        parcel.writeInt(movieLength)
        parcel.writeParcelable(rating, flags)
        parcel.writeString(description)
        parcel.writeTypedList(genres)
        parcel.writeParcelable(poster, flags)
    }

    override fun describeContents(): Int {
        return 0
    }

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
data class Rating(
    val kp: Float  // Значение рейтинга возвращается как число с плавающей точкой
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readFloat()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeFloat(kp)
    }

    override fun describeContents(): Int {
        return 0
    }

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
data class Genre(
    val name: String
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
    }

    override fun describeContents(): Int {
        return 0
    }

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
data class Poster(
    val url: String
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(url)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Poster> {
        override fun createFromParcel(parcel: Parcel): Poster {
            return Poster(parcel)
        }

        override fun newArray(size: Int): Array<Poster?> {
            return arrayOfNulls(size)
        }
    }
}
