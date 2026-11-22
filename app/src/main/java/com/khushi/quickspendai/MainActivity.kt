package com.khushi.quickspendai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.khushi.quickspendai.data.SharedFinanceEngine
import com.khushi.quickspendai.model.Expense
import com.khushi.quickspendai.network.CurrencyNetworkClient
import com.khushi.quickspendai.network.GeminiAdvisor
import com.khushi.quickspendai.ui.theme.QuickSpendAITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuickSpendAITheme {
                QuickSpendApp()
            }
        }
    }
}

private enum class AppRoute(val route: String) {
    AUTH("auth"),
    DASHBOARD("dashboard"),
    ADD_EXPENSE("add_expense")
}

@Composable
fun QuickSpendApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    // Paste your free Google AI Studio key here (from aistudio.google.com/apikey)
    val geminiAdvisor = remember { GeminiAdvisor("AQ.Ab8RN6KrTdw0ODhm-xJM_rJtBZyumF4GkmxlQwdP7X-SUmyg5g\n") }

    var currentUserEmail by remember { mutableStateOf(auth.currentUser?.email) }
    var currentUserId by remember { mutableStateOf(auth.currentUser?.uid) }
    val expenses = remember { mutableStateListOf<Expense>() }
    var monthlyLimit by remember { mutableStateOf(10000.0) }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedCurrency by remember { mutableStateOf("INR") }
    var liveRateToInr by remember { mutableStateOf<Double?>(null) }
    var aiTip by remember { mutableStateOf("Tap Refresh AI Tip to get personalized Gemini spending advice.") }
    var statusMessage by remember { mutableStateOf("") }

    fun loadUserData(uid: String) {
        db.collection("users")
            .document(uid)
            .addSnapshotListener { doc, _ ->
                val savedLimit = doc?.getDouble("monthlyLimit")
                if (savedLimit != null && savedLimit > 0.0) {
                    monthlyLimit = savedLimit
                }
            }

        db.collection("users")
            .document(uid)
            .collection("expenses")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    statusMessage = "Could not sync expenses: ${error.localizedMessage}"
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    expenses.clear()
                    for (doc in snapshot.documents) {
                        val item = doc.toObject(Expense::class.java)?.copy(id = doc.id)
                        if (item != null) {
                            expenses.add(item)
                        }
                    }
                }
            }
    }

    LaunchedEffect(currentUserId) {
        val uid = currentUserId
        if (uid != null) {
            loadUserData(uid)
        } else {
            expenses.clear()
            monthlyLimit = 10000.0
        }
    }

    LaunchedEffect(selectedCurrency) {
        if (selectedCurrency == "INR") {
            liveRateToInr = 1.0
        } else {
            withContext(Dispatchers.IO) {
                try {
                    val response = CurrencyNetworkClient.api.getRates(selectedCurrency)
                    val rate = response.rates["INR"]
                    withContext(Dispatchers.Main) {
                        liveRateToInr = rate
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        liveRateToInr = null
                    }
                }
            }
        }
    }

    fun convertAmountForDisplay(amountInInr: Double): Double {
        if (selectedCurrency == "INR") return amountInInr
        val rate = liveRateToInr
        return if (rate != null && rate > 0.0) {
            amountInInr / rate
        } else {
            CurrencyNetworkClient.convertOfflineFallback(amountInInr, "INR", selectedCurrency)
        }
    }

    val startDestination = if (currentUserId != null) AppRoute.DASHBOARD.route else AppRoute.AUTH.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(AppRoute.AUTH.route) {
            AuthScreen(
                statusMessage = statusMessage,
                onSignIn = { email, password ->
                    statusMessage = "Signing in..."
                    auth.signInWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { result ->
                            currentUserEmail = result.user?.email
                            currentUserId = result.user?.uid
                            statusMessage = ""
                            navController.navigate(AppRoute.DASHBOARD.route) {
                                popUpTo(AppRoute.AUTH.route) { inclusive = true }
                            }
                        }
                        .addOnFailureListener { e ->
                            statusMessage = "Sign in failed: ${e.localizedMessage ?: "Check credentials"}"
                        }
                },
                onSignUp = { email, password ->
                    statusMessage = "Creating account..."
                    auth.createUserWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { result ->
                            currentUserEmail = result.user?.email
                            currentUserId = result.user?.uid
                            statusMessage = ""
                            navController.navigate(AppRoute.DASHBOARD.route) {
                                popUpTo(AppRoute.AUTH.route) { inclusive = true }
                            }
                        }
                        .addOnFailureListener { e ->
                            statusMessage = "Sign up failed: ${e.localizedMessage ?: "Use 6+ char password"}"
                        }
                }
            )
        }

        composable(AppRoute.DASHBOARD.route) {
            val totalInr = SharedFinanceEngine.calculateTotal(expenses)
            val remainingInr = SharedFinanceEngine.calculateRemainingBudget(expenses, monthlyLimit)
            val topCategory = SharedFinanceEngine.topSpendingCategory(expenses)
            val filteredExpenses = SharedFinanceEngine.filterByCategory(expenses, selectedCategoryFilter)

            DashboardScreen(
                userEmail = currentUserEmail ?: "Student",
                selectedCurrency = selectedCurrency,
                monthlyLimitInr = monthlyLimit,
                budgetDisplay = SharedFinanceEngine.formatAmount(convertAmountForDisplay(monthlyLimit), selectedCurrency),
                totalDisplay = SharedFinanceEngine.formatAmount(convertAmountForDisplay(totalInr), selectedCurrency),
                remainingDisplay = SharedFinanceEngine.formatAmount(convertAmountForDisplay(remainingInr), selectedCurrency),
                topCategory = topCategory,
                aiTip = aiTip,
                selectedCategoryFilter = selectedCategoryFilter,
                expenses = filteredExpenses,
                convertItemAmount = { item ->
                    val converted = convertAmountForDisplay(item.amount)
                    SharedFinanceEngine.formatAmount(converted, selectedCurrency)
                },
                onUpdateBudget = { newLimitInr ->
                    val uid = currentUserId
                    if (uid != null && newLimitInr > 0.0) {
                        monthlyLimit = newLimitInr
                        db.collection("users")
                            .document(uid)
                            .set(mapOf("monthlyLimit" to newLimitInr), SetOptions.merge())
                    }
                },
                onSelectCategoryFilter = { category -> selectedCategoryFilter = category },
                onSelectCurrency = { code -> selectedCurrency = code },
                onRefreshAiTip = {
                    scope.launch {
                        aiTip = "Generating spending insight..."
                        aiTip = geminiAdvisor.generateSpendingTip(expenses.toList(), monthlyLimit)
                    }
                },
                onDeleteExpense = { expense ->
                    val uid = currentUserId
                    if (uid != null && expense.id.isNotBlank()) {
                        db.collection("users")
                            .document(uid)
                            .collection("expenses")
                            .document(expense.id)
                            .delete()
                    }
                },
                onNavigateAdd = { navController.navigate(AppRoute.ADD_EXPENSE.route) },
                onSignOut = {
                    auth.signOut()
                    currentUserEmail = null
                    currentUserId = null
                    navController.navigate(AppRoute.AUTH.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(AppRoute.ADD_EXPENSE.route) {
            AddExpenseScreen(
                geminiAdvisor = geminiAdvisor,
                statusMessage = statusMessage,
                onBackToDashboard = {
                    statusMessage = ""
                    navController.navigate(AppRoute.DASHBOARD.route) {
                        popUpTo(AppRoute.DASHBOARD.route) { inclusive = true }
                    }
                },
                onSaveExpense = { title, amountInr, category, insight ->
                    val uid = currentUserId
                    if (uid == null) {
                        statusMessage = "Please sign in first."
                        return@AddExpenseScreen
                    }
                    val newExpense = Expense(
                        title = title.trim(),
                        amount = amountInr,
                        currency = "INR",
                        category = category.ifBlank { "General" },
                        aiInsight = insight,
                        timestamp = System.currentTimeMillis()
                    )
                    db.collection("users")
                        .document(uid)
                        .collection("expenses")
                        .add(newExpense)
                        .addOnSuccessListener {
                            statusMessage = ""
                            navController.navigate(AppRoute.DASHBOARD.route) {
                                popUpTo(AppRoute.DASHBOARD.route) { inclusive = true }
                            }
                        }
                        .addOnFailureListener { e ->
                            statusMessage = "Save failed: ${e.localizedMessage ?: "Try again"}"
                        }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthScreen(
    statusMessage: String,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("QuickSpendAI - Account") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Sign in or create an account to sync your expenses with Firebase.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (6+ characters)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onSignIn(email, password) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sign In")
                }
                OutlinedButton(
                    onClick = { onSignUp(email, password) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sign Up")
                }
            }
            if (statusMessage.isNotBlank()) {
                Text(text = statusMessage, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(
    userEmail: String,
    selectedCurrency: String,
    monthlyLimitInr: Double,
    budgetDisplay: String,
    totalDisplay: String,
    remainingDisplay: String,
    topCategory: String,
    aiTip: String,
    selectedCategoryFilter: String,
    expenses: List<Expense>,
    convertItemAmount: (Expense) -> String,
    onUpdateBudget: (Double) -> Unit,
    onSelectCategoryFilter: (String) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onRefreshAiTip: () -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onNavigateAdd: () -> Unit,
    onSignOut: () -> Unit
) {
    val currencies = listOf("INR", "USD", "EUR", "GBP")
    val categories = listOf("All", "Food", "Transport", "Study", "Bills", "Shopping", "Entertainment", "General")
    var budgetInput by remember(monthlyLimitInr) { mutableStateOf(monthlyLimitInr.toInt().toString()) }
    var budgetStatus by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QuickSpendAI") },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Sign out"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateAdd,
                    icon = { Icon(Icons.Default.Add, contentDescription = "Add Expense") },
                    label = { Text("Add Expense") }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add new expense")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Signed in as $userEmail",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "Monthly Budget: $budgetDisplay", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Total Spent: $totalDisplay", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Remaining Budget: $remainingDisplay", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Top Category: $topCategory", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "Set Custom Monthly Budget (INR)", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = budgetInput,
                                onValueChange = { budgetInput = it },
                                label = { Text("Budget in INR") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val parsed = budgetInput.toDoubleOrNull()
                                    if (parsed != null && parsed > 0.0) {
                                        onUpdateBudget(parsed)
                                        budgetStatus = "Budget updated to INR $parsed"
                                    } else {
                                        budgetStatus = "Enter a valid positive number"
                                    }
                                }
                            ) {
                                Text("Save")
                            }
                        }
                        if (budgetStatus.isNotBlank()) {
                            Text(text = budgetStatus, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Text(text = "Currency View:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    currencies.forEach { code ->
                        if (code == selectedCurrency) {
                            Button(onClick = { onSelectCurrency(code) }) {
                                Text(code)
                            }
                        } else {
                            OutlinedButton(onClick = { onSelectCurrency(code) }) {
                                Text(code)
                            }
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "AI Coach Insight", style = MaterialTheme.typography.titleSmall)
                        Text(text = aiTip, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onRefreshAiTip) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text("Refresh AI Tip")
                        }
                    }
                }
            }
            item {
                Text(text = "Filter by Category:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        if (cat == selectedCategoryFilter) {
                            Button(onClick = { onSelectCategoryFilter(cat) }) {
                                Text(cat)
                            }
                        } else {
                            OutlinedButton(onClick = { onSelectCategoryFilter(cat) }) {
                                Text(cat)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    text = "Expenses (${expenses.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (expenses.isEmpty()) {
                item {
                    Text(
                        text = "No expenses in this view. Tap Add Expense to create an entry.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(expenses, key = { it.id.ifEmpty { it.timestamp.toString() } }) { expense ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = expense.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${convertItemAmount(expense)} - ${expense.category}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (expense.aiInsight.isNotBlank()) {
                                    Text(text = expense.aiInsight, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { onDeleteExpense(expense) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete ${expense.title}"
                                )
                            }
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseScreen(
    geminiAdvisor: GeminiAdvisor,
    statusMessage: String,
    onBackToDashboard: () -> Unit,
    onSaveExpense: (String, Double, String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }
    var aiInsight by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Expense") },
                navigationIcon = {
                    IconButton(onClick = onBackToDashboard) {
                        Icon(Icons.Default.Home, contentDescription = "Back to Dashboard")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Expense title (e.g., Metro card, Lunch)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount in INR") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank()) {
                        scope.launch {
                            aiInsight = "Detecting category..."
                            category = geminiAdvisor.suggestCategory(title, amt)
                            aiInsight = "AI categorized as $category."
                        }
                    }
                }
            ) {
                Text("Auto-Detect Category with AI")
            }
            if (aiInsight.isNotBlank()) {
                Text(text = aiInsight, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (title.isNotBlank() && amt != null && amt > 0.0) {
                        onSaveExpense(title, amt, category, aiInsight)
                    } else {
                        aiInsight = "Enter a valid title and positive amount."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Expense")
            }
            if (statusMessage.isNotBlank()) {
                Text(text = statusMessage, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}