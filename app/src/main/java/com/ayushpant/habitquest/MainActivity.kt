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
import com.ayushpant.habitquest.ui.EditorScreen
import com.ayushpant.habitquest.ui.HabitQuestTheme
import com.ayushpant.habitquest.ui.ItemKind
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
                            onAdd = { nav.navigate("habit/0") },
                            onEditHabit = { id -> nav.navigate("habit/$id") },
                            onEditTask = { id -> nav.navigate("task/$id") },
                        )
                    }
                    // id 0 = new item; the editor then shows the Habit | Task switch.
                    for ((route, kind) in listOf("habit" to ItemKind.HABIT, "task" to ItemKind.TASK)) {
                        composable("$route/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                            EditorScreen(
                                repo = repo,
                                initialKind = kind,
                                id = entry.arguments?.getLong("id") ?: 0L,
                                onDone = { nav.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
