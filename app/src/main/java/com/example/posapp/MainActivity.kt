package com.example.posapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.DrawerValue
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalDrawer
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.posapp.ui.components.Screen
import com.example.posapp.ui.screens.AddProductScreen
import com.example.posapp.ui.screens.ClientDetailScreen
import com.example.posapp.ui.screens.DashboardScreen
import com.example.posapp.ui.screens.FiadosScreen
import com.example.posapp.ui.screens.InventoryScreen
import com.example.posapp.ui.screens.SalesScreen
import com.example.posapp.ui.screens.SetupScreen
import com.example.posapp.vm.InventoryViewModel
import com.example.posapp.vm.UserProfileViewModel
import com.example.posapp.utils.BackupUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tryStartAppNormal()
    }

    private fun tryStartAppNormal() {
        setContent {
            com.example.posapp.ui.theme.PablitoTheme {
                AppContent()
            }
        }
        setupSystemBarsSafe()
    }

    private fun setupSystemBarsSafe() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.parseColor("#0F0F16")
        val decor = window.peekDecorView() ?: window.decorView
        decor.post {
            runCatching {
                WindowCompat.getInsetsController(window, decor).setAppearanceLightStatusBars(false)
            }
        }
    }
}

@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun AppContent() {
    val userProfileViewModel: UserProfileViewModel = viewModel()
    val profile by userProfileViewModel.profile.collectAsState()
    var editingProfile by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val backupScope = rememberCoroutineScope()
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            backupScope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { BackupUtils.exportDatabaseToUri(context, uri) }
                }
                Toast.makeText(
                    context,
                    if (result.isSuccess) "Respaldo guardado" else "No se pudo guardar el respaldo: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    if (profile == null && !editingProfile) {
        SetupScreen(
            existingProfile = null,
            onDone = { saved -> userProfileViewModel.saveProfile(saved) },
            onCancel = null
        )
        return
    }

    val navController = rememberNavController()
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val tabs = listOf(Screen.Dashboard, Screen.Inventory, Screen.Sales, Screen.Fiados)
        val navigateToIndex: (Int) -> Unit = { targetIndex ->
            tabs.getOrNull(targetIndex)?.let { targetTab ->
                navController.navigate(targetTab.route) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                }
            }
        }

        ModalDrawer(
            drawerState = drawerState,
            drawerContent = {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Drawer header (keep TopAppBar title only; removed duplicate PABLITO FAST)
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // Prominent action: Add Client
                    androidx.compose.material.ListItem(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clickable {
                                scope.launch {
                                    drawerState.close()
                                    navController.navigate("add_client")
                                }
                            },
                        text = { Text("Agregar Cliente") },
                        icon = { Icon(Icons.Default.Menu, contentDescription = null) }
                    )

                    androidx.compose.material.ListItem(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clickable {
                                scope.launch {
                                    drawerState.close()
                                    navController.navigate("clients")
                                }
                            },
                        text = { Text("Lista de Clientes") },
                        icon = { Icon(Icons.Default.List, contentDescription = null) }
                    )

                    Divider()

                    // Navigation items repeated for accessibility
                    tabs.forEach { s ->
                        androidx.compose.material.ListItem(
                            modifier = Modifier.padding(vertical = 4.dp).clickable {
                                scope.launch { drawerState.close() }
                                navController.navigate(s.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                }
                            },
                            text = { Text(s.title) },
                            icon = { Icon(s.icon, contentDescription = s.title) }
                        )
                    }

                    Divider()
                    androidx.compose.material.ListItem(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clickable {
                                scope.launch { drawerState.close() }
                                backupLauncher.launch("pablito_backup.json")
                            },
                        text = { Text("Exportar respaldo") },
                        icon = { Icon(Icons.Default.List, contentDescription = null) }
                    )
                    androidx.compose.material.ListItem(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clickable {
                                scope.launch {
                                    drawerState.close()
                                    editingProfile = true
                                }
                            },
                        text = { Text("Perfil y negocio") },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) }
                    )
                }
            }
        ) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route
            val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
            val gestureModifier = if (tabs.any { it.route == currentRoute }) Modifier.pointerInput(selectedIndex) {
                var accumulatedDrag = 0f
                detectHorizontalDragGestures(
                    onDragCancel = { accumulatedDrag = 0f },
                    onDragEnd = {
                        val threshold = 80f
                        when {
                            accumulatedDrag > threshold -> navigateToIndex((selectedIndex - 1).coerceAtLeast(0))
                            accumulatedDrag < -threshold -> navigateToIndex((selectedIndex + 1).coerceAtMost(tabs.lastIndex))
                        }
                        accumulatedDrag = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        accumulatedDrag += dragAmount
                    }
                )
            } else Modifier

            Scaffold(
                topBar = {
                    Column {
                        TopAppBar(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            title = {
                                // Temporary placeholder icon to avoid drawable inflation issues on some devices.
                                Icon(
                                    imageVector = Icons.Filled.Home,
                                    contentDescription = "App logo",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(28.dp)
                                )
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    content = {
                                        // Compact two-line menu icon
                                        val neon = Color(0xFF00E5FF)
                                        Column(
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(20.dp)
                                                    .height(2.dp)
                                                    .background(neon, shape = RoundedCornerShape(2.dp))
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Box(
                                                modifier = Modifier
                                                    .width(14.dp)
                                                    .height(2.dp)
                                                    .background(neon, shape = RoundedCornerShape(2.dp))
                                            )
                                        }
                                    }
                                )
                            },
                            backgroundColor = MaterialTheme.colors.surface,
                            contentColor = MaterialTheme.colors.onSurface,
                            elevation = 0.dp
                        )

                        // Tabs under the TopAppBar: icon + text in Spanish, equally distributed
                        val config = androidx.compose.ui.platform.LocalConfiguration.current
                        val tabWidth = (config.screenWidthDp.dp) / tabs.size
                        val neon = Color(0xFF00E5FF)

                        TabRow(
                            selectedTabIndex = selectedIndex,
                            backgroundColor = MaterialTheme.colors.surface,
                            contentColor = neon,
                            indicator = { tabPositions ->
                                TabRowDefaults.Indicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                                    color = neon,
                                    height = 3.dp
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            tabs.forEachIndexed { index, screen ->
                                val selected = index == selectedIndex
                                Tab(
                                    selected = selected,
                                    onClick = {
                                        if (screen.route != currentRoute) {
                                            navController.navigate(screen.route) {
                                                launchSingleTop = true
                                                restoreState = true
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            }
                                        }
                                    },
                                    modifier = Modifier.width(tabWidth)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = screen.icon,
                                            contentDescription = screen.title,
                                            tint = if (selected) neon else MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            screen.title,
                                            color = if (selected) neon else MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                if (editingProfile) {
                    SetupScreen(
                        existingProfile = profile,
                        onDone = { saved -> userProfileViewModel.saveProfile(saved) { editingProfile = false } },
                        onCancel = { editingProfile = false }
                    )
                    return@Scaffold
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(navController = navController, startDestination = Screen.Dashboard.route, modifier = Modifier
                        .padding(innerPadding)
                        .then(gestureModifier)) {
                        composable(Screen.Dashboard.route) {
                            DashboardScreen(
                                navController = navController,
                                userName = profile?.userName ?: "Usuario",
                                logoPath = profile?.logoPath
                            )
                        }
                        composable(Screen.Inventory.route) { InventoryScreen(navController = navController) }
                        composable(Screen.Sales.route) { SalesScreen(navController = navController) }
                        composable(Screen.Fiados.route) { FiadosScreen(navController = navController) }
                        composable("fiado/{clientId}") { backStack ->
                            val clientId = backStack.arguments?.getString("clientId")?.toLongOrNull()
                            if (clientId != null) {
                                ClientDetailScreen(clientId = clientId, navController = navController)
                            }
                        }
                        composable("add_product") { AddProductScreen(navController = navController) }
                        composable("add_client") { com.example.posapp.ui.screens.AddClientScreen(navController = navController) }
                        composable("clients") { com.example.posapp.ui.screens.ClientsScreen(navController = navController) }
                    }

                    if (tabs.any { it.route == currentRoute }) FloatingActionButton(
                        onClick = {
                            navController.navigate(Screen.Sales.route) { launchSingleTop = true }
                        },
                        backgroundColor = Color(0xFFFF2D92),
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp, top = 0.dp, bottom = 16.dp)
                    ) {
                        Text("Venta", color = Color.White)
                    }
                }
            }
        }
    }
}
