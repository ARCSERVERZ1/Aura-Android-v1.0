package com.aura.family.ui.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.family.ui.diagnostics.DiagnosticsDialog
import com.aura.family.ui.theme.AlertCrimson
import com.aura.family.ui.theme.DeepNavy
import com.aura.family.ui.theme.SoftTeal
import com.aura.family.ui.theme.WarningAmber
import com.aura.family.ui.theme.WarningAmberContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToAppSettings: () -> Unit,
    onNavigateToLocationTriggers: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentLoadedUrl by remember { mutableStateOf("") }
    var lastRequestedHomeUrl by remember { mutableStateOf("") }
    var pageProgress by remember { mutableIntStateOf(100) }
    var showDiagnostics by remember { mutableStateOf(false) }

    // 5-second hold progress for opening Diagnostics from the drawer username
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var isHoldingName by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Flush WebView cookies on ON_PAUSE and ON_STOP to guarantee persistent login sessions
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                CookieManager.getInstance().flush()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            CookieManager.getInstance().flush()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Load or reload when constructed homeUrl (<baseUrl><homeEndpoint>) changes in App Settings
    LaunchedEffect(uiState.homeUrl, webViewRef) {
        val target = uiState.homeUrl
        val wv = webViewRef
        if (target.isNotBlank() && wv != null && target != lastRequestedHomeUrl) {
            lastRequestedHomeUrl = target
            wv.loadUrl(target)
        }
    }

    BackHandler {
        when {
            showDiagnostics -> showDiagnostics = false
            drawerState.isOpen -> {
                coroutineScope.launch { drawerState.close() }
            }
            webViewRef?.canGoBack() == true -> {
                webViewRef?.goBack()
            }
            else -> {
                activity?.moveTaskToBack(true)
            }
        }
    }

    val activeDomainSubtitle = remember(currentLoadedUrl, uiState.homeUrl) {
        val source = currentLoadedUrl.ifBlank { uiState.homeUrl }
        if (source.isBlank()) {
            "Portal URL not set"
        } else {
            runCatching {
                val uri = Uri.parse(source)
                val host = uri.host ?: source
                val path = uri.path.orEmpty()
                if (path.isNotBlank() && path != "/") "$host$path" else host
            }.getOrDefault(source)
        }
    }

    if (showDiagnostics) {
        DiagnosticsDialog(
            settings = uiState.settings,
            serviceDiagnostics = uiState.serviceDiagnostics,
            dualSimStatus = uiState.dualSimStatus,
            pendingItems = uiState.pendingItems,
            sentHistory = uiState.sentHistory,
            periodicWorkState = uiState.periodicWorkState,
            syncWorkState = uiState.syncWorkState,
            onFlushQueue = viewModel::flushPendingQueue,
            onClearQueue = viewModel::clearPendingQueue,
            onClearSentHistory = viewModel::clearSentHistory,
            onRestartService = { viewModel.restartBackgroundService(context) },
            onRefreshSignals = viewModel::refreshSignals,
            onDismiss = { showDiagnostics = false }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                // Drawer Header — Hold User Name for 5 seconds to open Diagnostics
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeepNavy)
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isHoldingName = true
                                        holdProgress = 0f
                                        val holdJob: Job = coroutineScope.launch {
                                            val totalMs = 5_000L
                                            val stepMs = 50L
                                            var elapsed = 0L
                                            while (elapsed < totalMs) {
                                                delay(stepMs)
                                                elapsed += stepMs
                                                holdProgress = (elapsed.toFloat() / totalMs).coerceIn(0f, 1f)
                                            }
                                            isHoldingName = false
                                            holdProgress = 0f
                                            drawerState.close()
                                            viewModel.refreshSignals()
                                            showDiagnostics = true
                                        }
                                        tryAwaitRelease()
                                        holdJob.cancel()
                                        isHoldingName = false
                                        holdProgress = 0f
                                    }
                                )
                            }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = uiState.userName.ifBlank { "Aura User" },
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isHoldingName) {
                                LinearProgressIndicator(
                                    progress = { holdProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp),
                                    color = SoftTeal,
                                    trackColor = Color(0xFF334155)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isPrivacyModeEnabled) {
                                Icons.Rounded.Shield
                            } else {
                                Icons.Rounded.MyLocation
                            },
                            contentDescription = null,
                            tint = if (uiState.isPrivacyModeEnabled) WarningAmber else SoftTeal,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (uiState.isPrivacyModeEnabled) "Privacy Mode" else "Active",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState.isPrivacyModeEnabled) {
                                Color(0xFFFDE68A)
                            } else {
                                Color(0xFF99F6E4)
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (uiState.pendingQueueCount > 0) {
                        Text(
                            text = "${uiState.pendingQueueCount} offline alert(s) queued",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Privacy Mode Switch inside Hamburger Drawer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.togglePrivacyMode(context, !uiState.isPrivacyModeEnabled)
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (uiState.isPrivacyModeEnabled) {
                                Icons.Rounded.Shield
                            } else {
                                Icons.Outlined.Shield
                            },
                            contentDescription = null,
                            tint = if (uiState.isPrivacyModeEnabled) {
                                WarningAmber
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Column {
                            Text(
                                text = "Privacy Mode",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (uiState.isPrivacyModeEnabled) {
                                    "Location & triggers paused"
                                } else {
                                    "Safety monitoring active"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = uiState.isPrivacyModeEnabled,
                        onCheckedChange = { checked ->
                            viewModel.togglePrivacyMode(context, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = WarningAmber
                        )
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = { Text("App Settings") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToAppSettings()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                    label = { Text("Location Triggers") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToLocationTriggers()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                ) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { coroutineScope.launch { drawerState.open() } },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Menu,
                                    contentDescription = "Open navigation menu",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = "Aura · $activeDomainSubtitle",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp)
                            )

                            if (uiState.isPrivacyModeEnabled) {
                                IconButton(
                                    onClick = { coroutineScope.launch { drawerState.open() } },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = "Privacy Mode Active",
                                        tint = WarningAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(38.dp))
                            }
                        }

                        AnimatedVisibility(visible = uiState.isPrivacyModeEnabled) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(WarningAmberContainer)
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Privacy Mode Active — Location Sharing Paused",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF92400E)
                                )
                            }
                        }

                        if (pageProgress in 1..99) {
                            LinearProgressIndicator(
                                progress = { pageProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp),
                                color = SoftTeal
                            )
                        } else {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shadowElevation = 2.dp
                ) {
                    Column {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .height(46.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Symbol 1: Home
                            IconButton(
                                onClick = {
                                    if (uiState.homeUrl.isNotBlank()) {
                                        webViewRef?.loadUrl(uiState.homeUrl)
                                    } else {
                                        onNavigateToAppSettings()
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Home,
                                    contentDescription = "Home",
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Symbol 2 (Center): Exclusive SOS Trigger
                            Surface(
                                onClick = { viewModel.sendSosAlert() },
                                enabled = !uiState.isSendingSos,
                                shape = CircleShape,
                                color = AlertCrimson,
                                contentColor = Color.White,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (uiState.isSendingSos) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    } else {
                                        Text(
                                            text = "SOS",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.4.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // Symbol 3: Back
                            IconButton(
                                onClick = {
                                    if (webViewRef?.canGoBack() == true) {
                                        webViewRef?.goBack()
                                    } else {
                                        activity?.moveTaskToBack(true)
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (uiState.homeUrl.isBlank()) {
                    UnconfiguredPortalView(
                        onConfigureClick = onNavigateToAppSettings
                    )
                } else {
                    PersistentAuraWebView(
                        initialUrl = uiState.homeUrl,
                        onWebViewCreated = { created ->
                            webViewRef = created
                            lastRequestedHomeUrl = uiState.homeUrl
                        },
                        onUrlChanged = { url -> currentLoadedUrl = url },
                        onProgressChanged = { progress -> pageProgress = progress }
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PersistentAuraWebView(
    initialUrl: String,
    onWebViewCreated: (WebView) -> Unit,
    onUrlChanged: (String) -> Unit,
    onProgressChanged: (Int) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    javaScriptCanOpenWindowsAutomatically = true
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url ?: return false
                        val scheme = uri.scheme?.lowercase()
                        if (scheme == "http" || scheme == "https") {
                            return false
                        }
                        return try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        CookieManager.getInstance().flush()
                        if (!url.isNullOrBlank()) {
                            onUrlChanged(url)
                        }
                    }
                }

                if (initialUrl.isNotBlank()) {
                    loadUrl(initialUrl)
                }
                onWebViewCreated(this)
            }
        }
    )
}

@Composable
private fun UnconfiguredPortalView(
    onConfigureClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.Public,
            contentDescription = null,
            tint = SoftTeal,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Family Portal URL Needed",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add your family website URL and home endpoint in App Settings to load it here with persistent sign-in.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onConfigureClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DeepNavy)
        ) {
            Text("Open App Settings")
        }
    }
}
