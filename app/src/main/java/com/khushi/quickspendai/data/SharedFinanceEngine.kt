package com.khushi.quickspendai.data

import com.khushi.quickspendai.model.Expense
import kotlin.math.roundToInt

/**
 * Pure Kotlin finance calculation engine structured so it can be shared
 * across Android and other Kotlin Multiplatform (KMP) targets.
 */
object SharedFinanceEngine {

    fun calculateTotal(expenses: List<Expense>): Double {
        return expenses.sumOf { it.amount }
    }

    fun calculateRemainingBudget(expenses: List<Expense>, monthlyLimit: Double): Double {
        return monthlyLimit - calculateTotal(expenses)
    }

    fun topSpendingCategory(expenses: List<Expense>): String {
        if (expenses.isEmpty()) return "No expenses yet"
        return expenses
            .groupBy { it.category }
            .maxByOrNull { (_, items) -> items.sumOf { it.amount } }
            ?.key ?: "General"
    }

    fun filterByCategory(expenses: List<Expense>, category: String): List<Expense> {
        if (category.equals("All", ignoreCase = true)) return expenses
        return expenses.filter { it.category.equals(category, ignoreCase = true) }
    }

    fun formatAmount(amount: Double, currencyCode: String = "INR"): String {
        val rounded = (amount * 100.0).roundToInt() / 100.0
        return "$currencyCode $rounded"
    }
}