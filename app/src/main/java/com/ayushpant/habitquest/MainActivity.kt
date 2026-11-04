package com.ayushpant.habitquest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ayushpant.habitquest.ui.HabitEditorScreen
import com.ayushpant.habitquest.ui.HabitQuestTheme
import com.ayushpant.habitquest.ui.TodayScreen
import com.ayushpant.habitquest.ui.TodayViewModel

/** The app's single window. Screens are switched inside it by the NavHost below. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as HabitQuestApp).repository

        setContent {
            HabitQuestTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "today") {
                    composable("today") {
                        val vm: TodayViewModel = viewModel(factory = viewModelFactory { initializer { TodayViewModel(repo) } })
                        TodayScreen(
                            vm = vm,
                            onAddHabit = { nav.navigate("habit/0") },
                            onEditHabit = { id -> nav.navigate("habit/$id") },
                        )
                    }
                    composable("habit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        HabitEditorScreen(
                            repo = repo,
                            habitId = entry.arguments?.getLong("id") ?: 0L,
                            onDone = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
