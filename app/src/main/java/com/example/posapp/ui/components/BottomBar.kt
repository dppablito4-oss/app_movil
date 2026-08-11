package com.example.posapp.ui.components

import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Inicio", Icons.Default.Home)
    object Inventory : Screen("inventory", "Inventario", Icons.Default.List)
    object Sales : Screen("sales", "Venta", Icons.Default.ShoppingCart)
    object Fiados : Screen("fiados", "Fiados", Icons.Default.Book)
}

private val bottomScreens = listOf(
    Screen.Dashboard,
    Screen.Inventory,
    Screen.Sales,
    Screen.Fiados
)

@Composable
fun BottomBar(navController: NavController) {
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    BottomNavigation(elevation = 8.dp) {
        bottomScreens.forEach { screen ->
            BottomNavigationItem(
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title) },
                selected = currentRoute == screen.route,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            // Pop up to graph start to avoid building back stack indefinitely
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
