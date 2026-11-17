package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.PreferencesManager
import com.example.data.TqmRepository
import com.example.model.UiState
import com.example.ui.AdminAnalyticsScreen
import com.example.ui.AuthScreen
import com.example.ui.ClientPortalAndPaymentsScreen
import com.example.ui.MapRoutesScreen
import com.example.ui.NotificationsSheet
import com.example.ui.OperationsScreen
import com.example.ui.ProfileSettingsScreen
import com.example.ui.TqmViewModel
import com.example.ui.TwoFactorGateScreen
import com.example.ui.signOut
import com.example.ui.theme.IbagueTqmTheme
import com.example.ui.theme.TqmEmerald
import com.example.util.NotificationDispatcher
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class AppDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    MAP("Rutas", Icons.Filled.Map, Icons.Outlined.Map, "nav_map"),
    OPERATIONS("Equipo", Icons.Filled.Engineering, Icons.Outlined.Engineering, "nav_operations"),
    PORTAL("Pagos", Icons.Filled.Payments, Icons.Outlined.Payments, "nav_portal"),
    ADMIN("Admin", Icons.Filled.Analytics, Icons.Outlined.Analytics, "nav_admin"),
    PROFILE("Perfil", Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationDispatcher.ensureChannels(this)
        setContent {
            AppNavigation()
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val context = LocalContext.current
    val prefs = remember(context) { PreferencesManager(context.applicationContext) }
    val localThemeMode by prefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = "DARK")

    IbagueTqmTheme(themeMode = localThemeMode) {
        val user = currentUser
        if (user == null) {
            AuthScreen(onAuthSuccess = {})
        } else {
            key(user.uid) {
                AuthenticatedWorkspace(
                    currentUserId = user.uid,
                    userDisplayName = user.displayName.orEmpty(),
                    userEmail = user.email.orEmpty()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedWorkspace(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: TqmViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val repo = TqmRepository(app)
                val prefs = PreferencesManager(app)
                TqmViewModel(
                    repository = repo,
                    prefs = prefs,
                    currentUserId = currentUserId,
                    initialDisplayName = userDisplayName,
                    initialEmail = userEmail
                )
            }
        }
    )

    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val techniciansState by viewModel.techniciansState.collectAsStateWithLifecycle()
    val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val ticketsState by viewModel.ticketsState.collectAsStateWithLifecycle()
    val backupsState by viewModel.backupsState.collectAsStateWithLifecycle()
    val themeMode by viewModel.localThemeMode.collectAsStateWithLifecycle()
    val compactDensity by viewModel.compactDensity.collectAsStateWithLifecycle()
    val twoFactorVerified by viewModel.twoFactorVerified.collectAsStateWithLifecycle()
    val generatedOtp by viewModel.generatedOtp.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val e2eFingerprint by viewModel.e2eFingerprint.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data
    val technicians = (techniciansState as? UiState.Success)?.data ?: emptyList()
    val orders = (ordersState as? UiState.Success)?.data ?: emptyList()
    val notifications = (notificationsState as? UiState.Success)?.data ?: emptyList()
    val tickets = (ticketsState as? UiState.Success)?.data ?: emptyList()
    val backups = (backupsState as? UiState.Success)?.data ?: emptyList()

    LaunchedEffect(profile) {
        viewModel.bypassTwoFactorIfDisabled(profile)
    }

    val performSignOut: () -> Unit = {
        signOut(credentialManager = credentialManager, scope = scope)
    }

    if (!twoFactorVerified && (profile == null || profile.twoFactorEnabled)) {
        TwoFactorGateScreen(
            userEmail = userEmail.ifBlank { "Usuario Google" },
            profile = profile,
            generatedOtp = generatedOtp,
            onRequestOtp = { viewModel.requestTwoFactorOtp(context) },
            onVerifyCode = { code -> viewModel.verifyTwoFactorCode(code, profile) },
            onSignOut = performSignOut
        )
        return
    }

    var currentDestination by remember { mutableStateOf(AppDestination.MAP) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    if (currentDestination != AppDestination.MAP) {
        BackHandler { currentDestination = AppDestination.MAP }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val unreadCount = notifications.count { !it.readFlag }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 720.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_launcher_emblem),
                                    contentDescription = "Emblema Ibagué T.Q.M.",
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Ibagué T.Q.M.",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(TqmEmerald)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Sync Nube + Offline",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.setThemeMode(if (themeMode == "DARK") "LIGHT" else "DARK")
                            }
                        ) {
                            Icon(
                                imageVector = if (themeMode == "DARK") Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "Cambiar tema"
                            )
                        }
                        IconButton(
                            onClick = { showNotificationsSheet = true },
                            modifier = Modifier.testTag("notifications_bell_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge { Text("$unreadCount") }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Notifications,
                                    contentDescription = "Notificaciones en tiempo real"
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        AppDestination.entries.forEach { dest ->
                            val selected = currentDestination == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentDestination = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Spacer(Modifier.size(8.dp))
                        AppDestination.entries.forEach { dest ->
                            val selected = currentDestination == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentDestination = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    val loadingAny = techniciansState is UiState.Loading || ordersState is UiState.Loading
                    val errorMsg = (techniciansState as? UiState.Error)?.message
                        ?: (ordersState as? UiState.Error)?.message

                    if (loadingAny && technicians.isEmpty() && orders.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator()
                                Text("Sincronizando datos con Firebase...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else if (errorMsg != null && technicians.isEmpty() && orders.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(text = errorMsg, color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        when (currentDestination) {
                            AppDestination.MAP -> MapRoutesScreen(
                                technicians = technicians,
                                orders = orders,
                                onAssignOrderToTech = { order, tech ->
                                    viewModel.assignOrder(context, order, tech, profile)
                                },
                                onUpdateTechStatus = { tech, status, zone ->
                                    viewModel.updateTechnicianStatus(tech, status, zone)
                                },
                                onPostMessage = viewModel::postMessage
                            )

                            AppDestination.OPERATIONS -> OperationsScreen(
                                technicians = technicians,
                                orders = orders,
                                onSaveTechnician = viewModel::saveTechnician,
                                onDeleteTechnician = viewModel::deleteTechnician,
                                onCreateOrder = { order, autoTech, done ->
                                    viewModel.createAndMaybeAssignOrder(context, order, autoTech, profile, done)
                                },
                                onAssignOrder = { order, tech ->
                                    viewModel.assignOrder(context, order, tech, profile)
                                },
                                onAdvanceOrderStatus = { order, next, notes ->
                                    viewModel.advanceOrderStatus(context, order, next, notes)
                                }
                            )

                            AppDestination.PORTAL -> ClientPortalAndPaymentsScreen(
                                technicians = technicians,
                                orders = orders,
                                supportTickets = tickets,
                                onRegisterPayment = { order, method, ref, status, rating ->
                                    viewModel.registerPayment(context, order, method, ref, status, rating, profile)
                                },
                                onSubmitTicket = viewModel::submitSupportTicket,
                                onPostMessage = viewModel::postMessage
                            )

                            AppDestination.ADMIN -> AdminAnalyticsScreen(
                                technicians = technicians,
                                orders = orders,
                                backups = backups,
                                onCreateBackup = { label ->
                                    viewModel.createCloudBackup(label, technicians, orders)
                                },
                                onPostMessage = viewModel::postMessage
                            )

                            AppDestination.PROFILE -> ProfileSettingsScreen(
                                profile = profile,
                                themeMode = themeMode,
                                compactDensity = compactDensity,
                                e2eFingerprint = e2eFingerprint,
                                onSetThemeMode = viewModel::setThemeMode,
                                onSetCompactDensity = viewModel::setCompactDensity,
                                onSaveProfile = viewModel::updateProfileSettings,
                                onTestWebhook = viewModel::testWebhook,
                                onSignOut = performSignOut
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNotificationsSheet) {
        NotificationsSheet(
            notifications = notifications,
            onMarkRead = viewModel::markNotificationRead,
            onDismiss = { showNotificationsSheet = false }
        )
    }
}
