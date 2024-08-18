package com.example.try2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class CreateActivity : AppCompatActivity() {
    private lateinit var selectedItemsGenres: BooleanArray
    private lateinit var selectedItemsYears: BooleanArray
    private fun showMultiSelectDialog(
        items: Array<String>,
        selectedItems: BooleanArray,
        textView: TextView
    ) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Выберите элементы")

        builder.setMultiChoiceItems(items, selectedItems) { _, which, isChecked ->
            // Обновляем массив состояний выбора
            selectedItems[which] = isChecked
        }

        builder.setPositiveButton("OK") { _, _ ->
            // Обрабатываем выбранные элементы
            val selectedItemsText = items.filterIndexed { index, _ -> selectedItems[index] }
                .joinToString(", ")
            textView.text = "$selectedItemsText"
        }

        builder.setNegativeButton("Отмена", null)

        val dialog = builder.create()
        dialog.show()
    }

    private fun saveSelectionItems(genre: Array<String>, year: Array<String>) {
        // Собираем выбранные элементы из первого списка
        val selectedItemsListGenre = genre.filterIndexed { index, _ -> selectedItemsGenres[index] }

        // Собираем выбранные элементы из второго списка
        val selectedItemsListYear = year.filterIndexed { index, _ ->
            selectedItemsYears[index]
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val genres = resources.getStringArray(R.array.Genre)
        val year = resources.getStringArray(R.array.Years)

        selectedItemsGenres = BooleanArray(genres.size) { false } // Инициализация массива состояний
        selectedItemsYears = BooleanArray(year.size) { false }

        val buttonGenre = findViewById<Button>(R.id.buttonForGenre) // Кнопка для открытия списка
        val buttonYear = findViewById<Button>(R.id.buttonForYear)

        val textViewGenre =
            findViewById<TextView>(R.id.textViewForGenre) // Текстовое поле для отображения выбранных элементов
        val textViewYear = findViewById<TextView>(R.id.textViewForYear)

        buttonGenre.setOnClickListener {
            showMultiSelectDialog(genres, selectedItemsGenres, textViewGenre)
        }
        buttonYear.setOnClickListener {
            showMultiSelectDialog(year, selectedItemsYears, textViewYear)
        }


        val backToMain: TextView = findViewById(R.id.BackText)
        backToMain.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }



        val continueButton = findViewById<Button>(R.id.ContinueButton)
        continueButton.setOnClickListener{
            saveSelectedItemsToSharedPreferences(genres, year)
            val intent = Intent(this, MovieSearchActivity::class.java)
            startActivity(intent)

        }
    }
    private fun saveSelectedItemsToSharedPreferences(genres: Array<String>, years: Array<String>) {
        val selectedGenresList = genres.filterIndexed { index, _ -> selectedItemsGenres[index] }
        val selectedYearsList = years.filterIndexed { index, _ -> selectedItemsYears[index] }

        val sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        // Сохраняем выбранные элементы как строки, разделенные запятыми
        editor.putStringSet("selectedGenres", selectedGenresList.toSet())
        editor.putStringSet("selectedYears", selectedYearsList.toSet())
        editor.apply()
    }
}




