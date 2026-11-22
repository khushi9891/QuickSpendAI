package com.khushi.quickspendai.network

import com.google.ai.client.generativeai.GenerativeModel
import com.khushi.quickspendai.data.SharedFinanceEngine
import com.khushi.quickspendai.model.Expense

class GeminiAdvisor(private val apiKey: String) {

    private val model: GenerativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey
        )
    }

    suspend fun suggestCategory(expenseTitle: String, amount: Double): String {
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY") {
            return fallbackCategory(expenseTitle)
        }
        return try {
            val prompt = """
                Categorize this expense into ONE word from [Food, Transport, Shopping, Bills, Study, Entertainment, General].
                Title: $expenseTitle, Amount: $amount. Reply with only the category word.
            """.trimIndent()
            val response = model.generateContent(prompt)
            val text = response.text?.trim().orEmpty()
            val valid = listOf("Food", "Transport", "Shopping", "Bills", "Study", "Entertainment", "General")
            valid.firstOrNull { text.contains(it, ignoreCase = true) } ?: fallbackCategory(expenseTitle)
        } catch (_: Exception) {
            fallbackCategory(expenseTitle)
        }
    }

    suspend fun generateSpendingTip(expenses: List<Expense>, monthlyLimit: Double): String {
        val total = SharedFinanceEngine.calculateTotal(expenses)
        val remaining = monthlyLimit - total
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY") {
            return "You have spent ${SharedFinanceEngine.formatAmount(total)} out of ${SharedFinanceEngine.formatAmount(monthlyLimit)} (${SharedFinanceEngine.formatAmount(remaining)} remaining)."
        }
        return try {
            val topCat = SharedFinanceEngine.topSpendingCategory(expenses)
            val prompt = """
                Give one short, friendly 2-sentence money tip for a student who spent $total out of a $monthlyLimit budget. Top category: $topCat.
            """.trimIndent()
            val response = model.generateContent(prompt)
            response.text?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Remaining budget: ${SharedFinanceEngine.formatAmount(remaining)}."
        } catch (_: Exception) {
            "Remaining budget: ${SharedFinanceEngine.formatAmount(remaining)}."
        }
    }

    private fun fallbackCategory(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.contains("food") || lower.contains("lunch") || lower.contains("coffee") || lower.contains("snack") -> "Food"
            lower.contains("bus") || lower.contains("metro") || lower.contains("cab") || lower.contains("auto") || lower.contains("petrol") -> "Transport"
            lower.contains("book") || lower.contains("course") || lower.contains("exam") || lower.contains("pen") -> "Study"
            lower.contains("movie") || lower.contains("game") || lower.contains("netflix") -> "Entertainment"
            lower.contains("bill") || lower.contains("wifi") || lower.contains("recharge") || lower.contains("rent") -> "Bills"
            lower.contains("shop") || lower.contains("shirt") || lower.contains("shoe") -> "Shopping"
            else -> "General"
        }
    }
}