package com.khushi.quickspendai.model

data class Expense(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val currency: String = "INR",
    val category: String = "General",
    val aiInsight: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class BudgetSummary(
    val totalSpent: Double = 0.0,
    val monthlyLimit: Double = 10000.0,
    val topCategory: String = "General"
)
