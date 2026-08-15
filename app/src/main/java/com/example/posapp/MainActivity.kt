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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.AlertDialog
import androidx.compose.material.BottomNavigationItem
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
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.example.posapp.ui.screens.AccountScreen
import com.example.posapp.ui.screens.ClientDetailScreen
import com.example.posapp.ui.screens.DashboardScreen
import com.example.posapp.ui.screens.FiadosScreen
import com.example.posapp.ui.screens.InventoryScreen
import com.example.posapp.ui.screens.SalesScreen
import com.example.posapp.ui.screens.SetupScreen
import com.example.posapp.ui.screens.auth.AuthGate
import com.example.posapp.ui.theme.PablitoColors
import com.example.posapp.ui.theme.PablitoSizes
import com.example.posapp.ui.theme.SpaceSaleColors
import com.example.posapp.ui.theme.SpaceSaleRadii
import com.example.posapp.ui.theme.SpaceSaleSizes
import com.example.posapp.ui.theme.SpaceSaleSpacing
import com.example.posapp.vm.InventoryViewModel
import com.example.posapp.vm.UserProfileViewModel
import com.example.posapp.auth.AuthViewModel
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
            com.example.posapp.ui.theme.SpaceSaleTheme {
                AppContent()
            }
        }
        setupSystemBarsSafe()
    }

    private fun setupSystemBarsSafe() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.parseColor("#060912")
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
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.uiState.collectAsState()

    AuthGate(
        state = authState,
        onChooseMode = authViewModel::chooseMode,
        onBackToWelcome = authViewModel::backToWelcome,
        onBackToEmail = authViewModel::backToEmail,
        onSendOtp = authViewModel::sendOtp,
        onVerifyOtp = authViewModel::verifyOtp,
        onResendOtp = authViewModel::resendOtp,
        onCreateBusiness = authViewModel::createFirstBusiness,
        onClearFeedback = authViewModel::clearFeedback,
        onSignOut = authViewModel::signOut,
        onRetrySession = authViewModel::retrySession
    ) {
        val business = authState.business ?: return@AuthGate
        MainAppContent(
            authenticatedEmail = authState.email,
            authenticatedBusinessName = business.name,
            isSigningOut = authState.isSubmitting,
            onSignOut = authViewModel::signOut
        )
    }

    if (authState.showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = authViewModel::cancelSignOut,
            title = { Text("Hay cambios sin sincronizar") },
            text = {
                Text(
                    "Quedan ${authState.pendingSignOutChanges} cambios guardados solo en este telefono. " +
                        "Si cierras ahora, se descartaran junto con los datos locales."
                )
            },
            confirmButton = {
                TextButton(onClick = authViewModel::discardPendingAndSignOut) {
                    Text("Descartar y cerrar", color = MaterialTheme.colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = authViewModel::cancelSignOut) { Text("Seguir trabajando") }
            }
        )
    }
}

@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
private fun MainAppContent(
    authenticatedEmail: String,
    authenticatedBusinessName: String,
    isSigningOut: Boolean,
    onSignOut: () -> Unit
) {
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

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route

        ModalDrawer(
            drawerState = drawerState,
            drawerShape = RoundedCornerShape(topEnd = SpaceSaleRadii.Large, bottomEnd = SpaceSaleRadii.Large),
            drawerElevation = 0.dp,
            drawerBackgroundColor = SpaceSaleColors.Surface,
            drawerContentColor = SpaceSaleColors.TextPrimary,
            scrimColor = Color.Black.copy(alpha = 0.56f),
            drawerContent = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SpaceSaleColors.Surface)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = SpaceSaleSpacing.Md, vertical = SpaceSaleSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Xs)
                ) {
                    DrawerHeader(
                        businessName = authenticatedBusinessName,
                        onClose = { scope.launch { drawerState.close() } }
                    )
                    Divider(color = SpaceSaleColors.Border)

                    DrawerSectionLabel("NAVEGACIÓN")
                    tabs.forEachIndexed { index, screen ->
                        DrawerMenuItem(
                            label = screen.title,
                            icon = screen.icon,
                            selected = currentRoute == screen.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                if (currentRoute != screen.route) navigateToIndex(index)
                            }
                        )
                    }

                    DrawerSectionLabel("CLIENTES")
                    DrawerMenuItem(
                        label = "Agregar cliente",
                        icon = Icons.Default.PersonAdd,
                        selected = currentRoute == "add_client",
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                navController.navigate("add_client")
                            }
                        }
                    )
                    DrawerMenuItem(
                        label = "Lista de clientes",
                        icon = Icons.Default.People,
                        selected = currentRoute == "clients",
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                navController.navigate("clients")
                            }
                        }
                    )

                    DrawerSectionLabel("NEGOCIO Y CUENTA")
                    DrawerMenuItem(
                        label = "Perfil y negocio",
                        icon = Icons.Default.Storefront,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                editingProfile = true
                            }
                        }
                    )
                    DrawerMenuItem(
                        label = "Cuenta",
                        icon = Icons.Default.AccountCircle,
                        selected = currentRoute == "account",
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                navController.navigate("account") { launchSingleTop = true }
                            }
                        }
                    )
                    DrawerMenuItem(
                        label = "Exportar respaldo",
                        icon = Icons.Default.Backup,
                        onClick = {
                            scope.launch { drawerState.close() }
                            backupLauncher.launch("spacesale_backup.json")
                        }
                    )

                    Spacer(modifier = Modifier.height(SpaceSaleSpacing.Lg))
                    Text(
                        text = "Creado por Space Labs",
                        style = MaterialTheme.typography.caption,
                        color = SpaceSaleColors.TextMuted,
                        modifier = Modifier.padding(horizontal = SpaceSaleSpacing.Md)
                    )
                }
            }
        ) {
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
                bottomBar = {
                    if (!editingProfile && tabs.any { it.route == currentRoute }) {
                        PrimaryBottomBar(
                            tabs = tabs,
                            currentRoute = currentRoute,
                            onNavigate = navigateToIndex
                        )
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
                                userName = profile?.userName
                                    ?: authenticatedEmail.substringBefore('@').ifBlank { "Usuario" },
                                businessName = authenticatedBusinessName,
                                logoPath = profile?.logoPath,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onProfileClick = { editingProfile = true }
                            )
                        }
                        composable(Screen.Inventory.route) {
                            InventoryScreen(navController = navController, onMenuClick = { scope.launch { drawerState.open() } })
                        }
                        composable(Screen.Sales.route) {
                            SalesScreen(
                                navController = navController,
                                businessName = authenticatedBusinessName,
                                onMenuClick = { scope.launch { drawerState.open() } }
                            )
                        }
                        composable(Screen.Fiados.route) {
                            FiadosScreen(navController = navController, onMenuClick = { scope.launch { drawerState.open() } })
                        }
                        composable("fiado/{clientId}") { backStack ->
                            val clientId = backStack.arguments?.getString("clientId")?.toLongOrNull()
                            if (clientId != null) {
                                ClientDetailScreen(clientId = clientId, navController = navController)
                            }
                        }
                        composable("add_product") { AddProductScreen(navController = navController) }
                        composable("add_client") { com.example.posapp.ui.screens.AddClientScreen(navController = navController) }
                        composable("clients") { com.example.posapp.ui.screens.ClientsScreen(navController = navController) }
                        composable("account") {
                            AccountScreen(
                                email = authenticatedEmail,
                                businessName = authenticatedBusinessName,
                                isSigningOut = isSigningOut,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onSignOut = onSignOut
                            )
                        }
                    }

                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(
    businessName: String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = SpaceSaleSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = SpaceSaleColors.CyanContainer,
            shape = RoundedCornerShape(SpaceSaleRadii.Medium),
            modifier = Modifier.size(SpaceSaleSizes.TouchTarget)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    tint = SpaceSaleColors.Cyan,
                    modifier = Modifier.size(SpaceSaleSizes.IconMedium)
                )
            }
        }
        Spacer(modifier = Modifier.width(SpaceSaleSpacing.Md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SpaceSale",
                style = MaterialTheme.typography.subtitle1,
                color = SpaceSaleColors.TextPrimary
            )
            Text(
                text = businessName.ifBlank { "Mi negocio" },
                style = MaterialTheme.typography.body2,
                color = SpaceSaleColors.TextSecondary,
                maxLines = 1
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(SpaceSaleSizes.TouchTarget)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cerrar menú",
                tint = SpaceSaleColors.TextSecondary
            )
        }
    }
}

@Composable
private fun DrawerSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.overline,
        color = SpaceSaleColors.TextMuted,
        modifier = Modifier.padding(
            start = SpaceSaleSpacing.Md,
            top = SpaceSaleSpacing.Md,
            bottom = SpaceSaleSpacing.Xs
        )
    )
}

@Composable
private fun DrawerMenuItem(
    label: String,
    icon: ImageVector,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val foreground = if (selected) SpaceSaleColors.Cyan else SpaceSaleColors.TextSecondary
    Surface(
        color = if (selected) SpaceSaleColors.CyanContainer else Color.Transparent,
        contentColor = foreground,
        shape = RoundedCornerShape(SpaceSaleRadii.Medium),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SpaceSaleSizes.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = label
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SpaceSaleSpacing.Md, vertical = SpaceSaleSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(SpaceSaleSizes.IconMedium)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.body1,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) SpaceSaleColors.TextPrimary else foreground,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = SpaceSaleColors.Cyan,
                    modifier = Modifier.size(SpaceSaleSizes.IconSmall)
                )
            }
        }
    }
}

@Composable
private fun PrimaryBottomBar(
    tabs: List<Screen>,
    currentRoute: String,
    onNavigate: (Int) -> Unit
) {
    Column {
        Divider(color = PablitoColors.Border, thickness = 1.dp)
        androidx.compose.material.BottomNavigation(
            backgroundColor = PablitoColors.Surface,
            contentColor = PablitoColors.TextPrimary,
            elevation = 0.dp
        ) {
            tabs.forEachIndexed { index, screen ->
                val selected = screen.route == currentRoute
                BottomNavigationItem(
                    selected = selected,
                    onClick = { if (!selected) onNavigate(index) },
                    icon = {
                        Icon(
                            screen.icon,
                            contentDescription = screen.title,
                            modifier = Modifier.size(PablitoSizes.IconMedium)
                        )
                    },
                    label = {
                        Text(
                            screen.title,
                            style = MaterialTheme.typography.caption,
                            maxLines = 1
                        )
                    },
                    selectedContentColor = SpaceSaleColors.VioletContent,
                    unselectedContentColor = PablitoColors.TextSecondary,
                    alwaysShowLabel = true
                )
            }
        }
    }
}
