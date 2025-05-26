package com.example.try2

import android.content.Context
import android.util.AttributeSet
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog

class MultiSelectionSpinner @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : androidx.appcompat.widget.AppCompatSpinner(context, attrs) {

    private var items: List<String> = emptyList()
    private var selectedItems: MutableList<String> = mutableListOf()
    private var listener: ((List<String>) -> Unit)? = null

    init {
        // Устанавливаем начальный текст
        updateSpinnerText()
    }

    fun setItems(items: List<String>) {
        this.items = items
        updateSpinnerText()
    }

    fun setSelection(selected: List<String>) {
        selectedItems.clear()
        selectedItems.addAll(selected)
        updateSpinnerText()
    }

    fun getSelectedItems(): List<String> = selectedItems.toList()

    fun setOnSelectionChangedListener(listener: (List<String>) -> Unit) {
        this.listener = listener
    }

    override fun performClick(): Boolean {
        val checkedItems = BooleanArray(items.size) { selectedItems.contains(items[it]) }
        AlertDialog.Builder(context)
            .setTitle("Выберите жанры")
            .setMultiChoiceItems(
                items.toTypedArray(),
                checkedItems
            ) { _, which, isChecked ->
                if (isChecked) {
                    if (!selectedItems.contains(items[which])) {
                        selectedItems.add(items[which])
                    }
                } else {
                    selectedItems.remove(items[which])
                }
            }
            .setPositiveButton("ОК") { _, _ ->
                updateSpinnerText()
                listener?.invoke(selectedItems)
            }
            .setNegativeButton("Отмена", null)
            .show()
        return true
    }

    private fun updateSpinnerText() {
        val displayText = if (selectedItems.isEmpty()) {
            "Выберите жанры" // Начальное значение
        } else {
            selectedItems.joinToString(", ")
        }
        val adapter = ArrayAdapter(context, R.layout.spinner_item, listOf(displayText))
        this.adapter = adapter
    }
}