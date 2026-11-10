package com.ayushpant.habitquest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ayushpant.habitquest.detect.CatchUp
import com.ayushpant.habitquest.data.HabitType
import com.ayushpant.habitquest.ui.EditorScreen
import com.ayushpant.habitquest.ui.Welcome
import com.ayushpant.habitquest.ui.WelcomeScreen
import com.ayushpant.habitquest.ui.GuideAction
import com.ayushpant.habitquest.ui.GuideScreen
import com.ayushpant.habitquest.ui.ZonesScreen
import com.ayushpant.habitquest.ui.CollectionScreen
import kotlinx.coroutines.launch
import com.ayushpant.habitquest.ui.HabitQuestTheme
import com.ayushpant.habitquest.ui.HistoryScreen
import com.ayushpant.habitquest.ui.HistoryViewModel
import com.ayushpant.habitquest.ui.ItemKind
import com.ayushpant.habitquest.ui.SettingsScreen
import com.ayushpant.habitquest.ui.SummaryScreen
import com.ayushpant.habitquest.ui.SummaryViewModel
import com.ayushpant.habitquest.ui.TodayScreen
import com.ayushpant.habitquest.ui.TodayViewModel

/** A screen in the bottom navigation bar. */
private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("today", "Today", Icons.Default.Home),
    Tab("history", "History", Icons.Default.DateRange),
    Tab("summary", "Summary", Icons.Default.Star),
    Tab("settings", "Settings", Icons.Default.Settings),
)

/** The app's single window. Screens are switched inside it by the NavHost below. */
class MainActivity : ComponentActivity() {
    /** Each time the app comes to the front, look for sessions the live signal missed (spec 5.2). */
    override fun onResume() {
        super.onResume()
        val app = application as HabitQuestApp
        app.appScope.launch { runCatching { CatchUp.run(app) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as HabitQuestApp
        val repo = app.repository

        setContent {
            HabitQuestTheme {
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route

                Scaffold(
                    bottomBar = {
                        // Hidden in the editor, shown on the four main screens.
                        if (TABS.any { it.route == route }) {
                            NavigationBar {
                                for (tab in TABS) {
                                    NavigationBarItem(
                                        selected = route == tab.route,
                                        onClick = {
                                            nav.navigate(tab.route) {
                                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(tab.icon, contentDescription = null) },
                                        label = { Text(tab.label) },
                                    )
                                }
                            }
                        }
                    },
                ) { inner ->
                    NavHost(
                        navController = nav,
                        // First open: the welcome tour, then Today.
                        startDestination = if (Welcome.seen(this@MainActivity)) "today" else "welcome",
                        modifier = Modifier.padding(inner).consumeWindowInsets(inner),
                    ) {
                        composable("today") {
                            val vm: TodayViewModel = viewModel(factory = viewModelFactory { initializer { TodayViewModel(repo) } })
                            TodayScreen(
                                vm = vm,
                                onAdd = { kind -> nav.navigate(if (kind == ItemKind.TASK) "task/0" else "habit/0") },
                                onEditHabit = { id -> nav.navigate("habit/$id") },
                                onEditTask = { id -> nav.navigate("task/$id") },
                                onCollection = { nav.navigate("collection") },
                            )
                        }
                        composable("history") {
                            val vm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(repo) } })
                            HistoryScreen(vm)
                        }
                        composable("summary") {
                            val vm: SummaryViewModel = viewModel(factory = viewModelFactory { initializer { SummaryViewModel(repo) } })
                            SummaryScreen(vm)
                        }
                        composable("welcome") {
                            WelcomeScreen { addHabit ->
                                Welcome.markSeen(this@MainActivity)
                                nav.navigate("today") { popUpTo("welcome") { inclusive = true } }
                                if (addHabit) nav.navigate("habit/0")
                            }
                        }
                        composable("zones") { ZonesScreen(repo, onBack = { nav.popBackStack() }) }
                        composable("collection") { CollectionScreen(repo, onBack = { nav.popBackStack() }) }
                        composable("guide") {
                            GuideScreen(onBack = { nav.popBackStack() }) { action ->
                                when (action) {
                                    GuideAction.ADD_HABIT -> nav.navigate("habit/0")
                                    GuideAction.ADD_TASK -> nav.navigate("task/0")
                                    GuideAction.PLACES -> nav.navigate("zones")
                                    GuideAction.SETTINGS -> nav.popBackStack()
                                    GuideAction.SUMMARY, GuideAction.HISTORY -> nav.navigate(if (action == GuideAction.SUMMARY) "summary" else "history") {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                        composable("settings") {
                            SettingsScreen(
                                app.reminders,
                                onLinkHabit = { id, type -> nav.navigate("habit/$id?type=${type.name}") },
                                onZones = { nav.navigate("zones") },
                                onPermissionsChanged = { app.rescheduleReminders() },
                                onShowWelcome = { nav.navigate("welcome") },
                                onGuide = { nav.navigate("guide") },
                            )
                        }
                        // id 0 = new item; the editor then shows the Habit | Task switch.
                        for ((r, kind) in listOf("habit" to ItemKind.HABIT, "task" to ItemKind.TASK)) {
                            composable(
                                "$r/{id}?type={type}",
                                arguments = listOf(
                                    navArgument("id") { type = NavType.LongType },
                                    // From the Settings guides: open with "App" or "At a place" already chosen.
                                    navArgument("type") { type = NavType.StringType; defaultValue = "" },
                                ),
                            ) { e ->
                                EditorScreen(
                                    repo = repo,
                                    initialKind = kind,
                                    id = e.arguments?.getLong("id") ?: 0L,
                                    onDone = { nav.popBackStack() },
                                    startType = e.arguments?.getString("type")?.let { t -> HabitType.entries.firstOrNull { it.name == t } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
