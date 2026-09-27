package com.zygy7678.approvedbrowser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class WeeklyLockWindow(
    val days: Set<Int>,
    val startMinutes: Int,
    val endMinutes: Int
)

private fun encodeWeeklyLockWindows(windows: List<WeeklyLockWindow>): String =
    windows.joinToString(";") { window ->
        window.days.sorted().joinToString(",") + "|" + window.startMinutes + "|" + window.endMinutes
    }

private fun decodeWeeklyLockWindows(value: String): List<WeeklyLockWindow> =
    value.split(";").mapNotNull { item ->
        val parts = item.split("|")
        if (parts.size != 3) return@mapNotNull null
        val days = parts[0].split(",").mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }.toSet()
        val start = parts[1].toIntOrNull()
        val end = parts[2].toIntOrNull()
        if (days.isEmpty() || start == null || end == null || start !in 0..1439 || end !in 0..1439) null
        else WeeklyLockWindow(days, start, end)
    }

private fun isWeeklyLockActive(
    windows: List<WeeklyLockWindow>,
    now: java.util.Calendar = java.util.Calendar.getInstance()
): Boolean {
    if (windows.isEmpty()) return false
    val day = now.get(java.util.Calendar.DAY_OF_WEEK)
    val minutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
    val previousDay = if (day == java.util.Calendar.SUNDAY) java.util.Calendar.SATURDAY else day - 1

    return windows.any { window ->
        if (window.startMinutes == window.endMinutes) {
            day in window.days
        } else if (window.startMinutes < window.endMinutes) {
            day in window.days && minutes >= window.startMinutes && minutes < window.endMinutes
        } else {
            (day in window.days && minutes >= window.startMinutes) ||
                (previousDay in window.days && minutes < window.endMinutes)
        }
    }
}

private fun formatLockMinutes(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)

private data class BrowserPrefs(
    val dark: Boolean = false,
    val highContrast: Boolean = false,
    val compact: Boolean = false,
    val showUrls: Boolean = true,
    val showCategories: Boolean = true,
    val largeText: Boolean = false,
    val roundedCards: Boolean = true,
    val route: BrowserRoute = BrowserRoute.ETROG,
    val showAppClock: Boolean = true,
    val timeFormat24: Boolean = true,
    val timeOffsetMinutes: Int = 0,
    val autoLockMinutes: Int = 0,
    val weeklyLockWindows: List<WeeklyLockWindow> = emptyList(),
    val clearOnExit: Boolean = false,
    val blockExternalApps: Boolean = true,
    val preventScreenshots: Boolean = true,
    val disableJavascript: Boolean = false,
    val blockPopups: Boolean = true,
    val lockedPackages: Set<String> = emptySet(),
    val protectedUninstallPackages: Set<String> = emptySet()
)

private class PrefStore(context: Context) {
    private val p = context.getSharedPreferences("browser_settings", Context.MODE_PRIVATE)

    fun load() = BrowserPrefs(
        dark = p.getBoolean("dark", false),
        highContrast = p.getBoolean("highContrast", false),
        compact = p.getBoolean("compact", false),
        showUrls = p.getBoolean("showUrls", true),
        showCategories = p.getBoolean("showCategories", true),
        largeText = p.getBoolean("largeText", false),
        roundedCards = p.getBoolean("roundedCards", true),
        showAppClock = p.getBoolean("showAppClock", true),
        timeFormat24 = p.getBoolean("timeFormat24", true),
        timeOffsetMinutes = p.getInt("timeOffsetMinutes", 0),
        autoLockMinutes = p.getInt("autoLockMinutes", 0),
        weeklyLockWindows = decodeWeeklyLockWindows(p.getString("weeklyLockWindows", "") ?: ""),
        clearOnExit = p.getBoolean("clearOnExit", false),
        blockExternalApps = p.getBoolean("blockExternalApps", true),
        preventScreenshots = p.getBoolean("preventScreenshots", true),
        disableJavascript = p.getBoolean("disableJavascript", false),
        blockPopups = p.getBoolean("blockPopups", true),
        lockedPackages = p.getStringSet("lockedPackages", emptySet()) ?: emptySet(),
        protectedUninstallPackages = p.getStringSet("protectedUninstallPackages", emptySet()) ?: emptySet(),
        route = runCatching {
            BrowserRoute.valueOf(p.getString("route", BrowserRoute.ETROG.name) ?: BrowserRoute.ETROG.name)
        }.getOrDefault(BrowserRoute.ETROG)
    )

    fun save(v: BrowserPrefs) {
        p.edit()
            .putBoolean("dark", v.dark)
            .putBoolean("highContrast", v.highContrast)
            .putBoolean("compact", v.compact)
            .putBoolean("showUrls", v.showUrls)
            .putBoolean("showCategories", v.showCategories)
            .putBoolean("largeText", v.largeText)
            .putBoolean("roundedCards", v.roundedCards)
            .putBoolean("showAppClock", v.showAppClock)
            .putBoolean("timeFormat24", v.timeFormat24)
            .putInt("timeOffsetMinutes", v.timeOffsetMinutes)
            .putInt("autoLockMinutes", v.autoLockMinutes)
            .putString("weeklyLockWindows", encodeWeeklyLockWindows(v.weeklyLockWindows))
            .putBoolean("clearOnExit", v.clearOnExit)
            .putBoolean("blockExternalApps", v.blockExternalApps)
            .putBoolean("preventScreenshots", v.preventScreenshots)
            .putBoolean("disableJavascript", v.disableJavascript)
            .putBoolean("blockPopups", v.blockPopups)
            .putStringSet("lockedPackages", v.lockedPackages)
            .putStringSet("protectedUninstallPackages", v.protectedUninstallPackages)
            .putString("route", v.route.name)
            .apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceManagement.enforcePolicies(this)
        val initialPrefs = PrefStore(this).load()
        DeviceManagement.applyManagedAppPolicies(this, initialPrefs.lockedPackages, initialPrefs.protectedUninstallPackages)

        val setupAccessCode = intent?.getStringExtra("setup_access_code_b64")
        if (!setupAccessCode.isNullOrBlank() && DeviceManagement.isDeviceOwner(this)) {
            runCatching {
                val decoded = String(
                    Base64.decode(setupAccessCode, Base64.DEFAULT),
                    Charsets.UTF_8
                )
                AccessCodeStore(this).provisionFromSetup(decoded)
            }
            intent.removeExtra("setup_access_code_b64")
        }

        val externalUrl = intent?.data?.toString()
        val store = PrefStore(this)

        setContent {
            var prefs by remember { mutableStateOf(store.load()) }

            ApprovedBrowserTheme(
                darkTheme = prefs.dark,
                highContrast = prefs.highContrast
            ) {
                ApprovedBrowserApp(
                    prefs = prefs,
                    initialExternalUrl = externalUrl,
                    onPrefsChange = {
                        prefs = it
                        store.save(it)
                    }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ApprovedBrowserApp(
    prefs: BrowserPrefs,
    initialExternalUrl: String?,
    onPrefsChange: (BrowserPrefs) -> Unit
) {
    val context = LocalContext.current
    val sites = remember { WhitelistRepository.load(context) }
    val accessStore = remember { AccessCodeStore(context) }
    val favoriteStore = remember { FavoriteStore(context) }

    val route = prefs.route
    val routeState = rememberUpdatedState(route)
    val availableSites = remember(sites, route) {
        sites.filter(route::allows)
    }
    val initialAllowedUrl = remember(initialExternalUrl, availableSites) {
        initialExternalUrl?.takeIf { WhitelistRepository.isAllowed(it, availableSites) }
    }

    var favorites by remember { mutableStateOf(favoriteStore.load()) }
    var url by remember { mutableStateOf(initialAllowedUrl ?: "") }
    var address by remember { mutableStateOf(initialAllowedUrl ?: initialExternalUrl.orEmpty()) }
    var home by remember { mutableStateOf(initialAllowedUrl == null) }
    var settings by remember { mutableStateOf(false) }
    var securityFolderOpen by remember { mutableStateOf(false) }
    var blocked by remember {
        mutableStateOf(initialExternalUrl != null && initialAllowedUrl == null)
    }
    var query by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(false) }
    var selectedSiteForAction by remember { mutableStateOf<Site?>(null) }
    var siteRequestDialog by remember { mutableStateOf(false) }
    var requestThanksDialog by remember { mutableStateOf(false) }

    var accessDialog by remember { mutableStateOf(false) }
    var sensitiveAccessDialog by remember { mutableStateOf(false) }
    var pendingSensitiveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var routeDialog by remember { mutableStateOf(false) }
    var changeCodeDialog by remember { mutableStateOf(false) }
    var deviceOwnerInstructionsDialog by remember { mutableStateOf(false) }
    var scheduleAccessDialog by remember { mutableStateOf(false) }
    var pendingWeeklyWindows by remember { mutableStateOf<List<WeeklyLockWindow>?>(null) }
    var appLocked by remember { mutableStateOf(isWeeklyLockActive(prefs.weeklyLockWindows)) }

    LaunchedEffect(prefs.weeklyLockWindows) {
        while (true) {
            appLocked = isWeeklyLockActive(prefs.weeklyLockWindows)
            delay(15_000L)
        }
    }
    val categories = remember(availableSites) {
        listOf("מועדפים", "הכול") + availableSites.map { it.category }.distinct()
    }
    val sitesByCategory = remember(availableSites) {
        availableSites.groupBy { it.category }
    }

    BackHandler(enabled = !home || settings) {
        when {
            securityFolderOpen -> securityFolderOpen = false
            settings -> settings = false
            webView?.canGoBack() == true -> webView?.goBack()
            else -> home = true
        }
    }

    Box(Modifier.fillMaxSize()) {
    if (settings) {
        if (securityFolderOpen) {
            SecuritySettingsScreen(
                prefs = prefs,
                route = route,
                onPrefsChange = onPrefsChange,
                onChangeRoute = { routeDialog = true },
                onChangeCode = { changeCodeDialog = true },
                onDeviceOwnerInstructions = { deviceOwnerInstructionsDialog = true },
                onWeeklyLockChange = { windows ->
                    onPrefsChange(prefs.copy(weeklyLockWindows = windows))
                },
                onManagedAppsChange = { locked, protected ->
                    onPrefsChange(prefs.copy(lockedPackages = locked, protectedUninstallPackages = protected))
                    DeviceManagement.applyManagedAppPolicies(context, locked, protected)
                },
                onBack = { securityFolderOpen = false }
            )
        } else {
            SettingsScreen(
                prefs = prefs,
                securityHasCode = accessStore.hasCode(),
                onPrefsChange = onPrefsChange,
                onOpenSecurity = { securityFolderOpen = true },
                onSiteRequest = { siteRequestDialog = true },
                onBack = { settings = false }
            )
        }
    } else {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text("דפדפן מאושר", fontWeight = FontWeight.Bold)
                                Text(
                                    route.title + " • " + route.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (prefs.showAppClock) {
                                    Text(
                                        formatAppTime(prefs.timeFormat24, prefs.timeOffsetMinutes),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            AssistChip(
                                onClick = { settings = true },
                                label = { Text(route.title) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = "מסלול מוגן"
                                    )
                                }
                            )
                            IconButton(onClick = { settings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "הגדרות")
                            }
                            IconButton(onClick = { home = true }) {
                                Icon(Icons.Default.Home, contentDescription = "בית")
                            }

                            val view = webView

                            IconButton(
                                onClick = { view?.goBack() },
                                enabled = view?.canGoBack() == true
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "חזור")
                            }
                            IconButton(
                                onClick = { view?.goForward() },
                                enabled = view?.canGoForward() == true
                            ) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "קדימה")
                            }
                            IconButton(
                                onClick = { view?.reload() },
                                enabled = view != null
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "רענן")
                            }
                        }
                    )

                    if (!home) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("חפש או הזן כתובת מאושרת") }
                            )

                            IconButton(
                                onClick = {
                                    val candidate = if (
                                        address.startsWith("http", true)
                                    ) {
                                        address
                                    } else {
                                        "https://" + address
                                    }

                                    if (WhitelistRepository.isAllowed(candidate, availableSites)) {
                                        url = candidate
                                        home = false
                                        blocked = false
                                    } else {
                                        blocked = true
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "פתח")
                            }
                        }

                        if (loading) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }

                        if (blocked) {
                            Text(
                                "הכתובת אינה זמינה במסלול " + route.title,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        ) { padding ->
            if (home) {
                key(route) {
                    val pagerState = rememberPagerState(pageCount = { categories.size })

                    HomeScreen(
                        prefs = prefs,
                        categories = categories,
                        availableSites = availableSites,
                        favorites = favorites,
                        query = query,
                        onQueryChange = { query = it },
                        pagerState = pagerState,
                        onSelectCategory = { /* Pager selection is handled by the tab click itself. */ },
                        onOpenSite = { site ->
                            url = site.url
                            address = site.url
                            home = false
                            blocked = false
                        },
                        onToggleFavorite = { site ->
                            favorites = favoriteStore.toggle(siteKey(site))
                        },
                        onLongPressSite = { site -> selectedSiteForAction = site }
                    )
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    factory = { ctx ->
                        WebView(ctx).also { view ->
                            webView = view
                            configureApprovedWebView(
                                view,
                                disableJavascript = prefs.disableJavascript,
                                blockPopups = prefs.blockPopups
                            )
                            if (prefs.preventScreenshots) {
                                (context as? ComponentActivity)?.window?.addFlags(
                                    WindowManager.LayoutParams.FLAG_SECURE
                                )
                            } else {
                                (context as? ComponentActivity)?.window?.clearFlags(
                                    WindowManager.LayoutParams.FLAG_SECURE
                                )
                            }

                            view.webViewClient = BrowserWebViewClient(
                                sitesProvider = {
                                    sites.filter { routeState.value.allows(it) }
                                },
                                onBlockedNavigation = { blocked = true },
                                onPageStateChanged = { currentUrl, isLoading ->
                                    if (!currentUrl.isNullOrBlank()) {
                                        address = currentUrl
                                    }
                                    loading = isLoading
                                }
                            )

                            if (url.isNotBlank()) {
                                view.loadUrl(url)
                            }
                        }
                    },
                    update = { view ->
                        webView = view
                    }
                )
            }
        }
    }

    if (sensitiveAccessDialog) {
        AccessCodeDialog(
            store = accessStore,
            onVerified = {
                sensitiveAccessDialog = false
                val action = pendingSensitiveAction
                pendingSensitiveAction = null
                action?.invoke()
            },
            onDismiss = { sensitiveAccessDialog = false; pendingSensitiveAction = null }
        )
    }

    if (accessDialog) {
        AccessCodeDialog(
            store = accessStore,
            onVerified = {
                accessDialog = false
                routeDialog = true
            },
            onDismiss = { accessDialog = false }
        )
    }

    if (routeDialog) {
        RouteSelectionDialog(
            current = route,
            onSelect = { next ->
                onPrefsChange(prefs.copy(route = next))
                routeDialog = false
                home = true
                query = ""
                address = ""
                url = ""
                blocked = false
                webView?.stopLoading()
                webView?.loadUrl("about:blank")
            },
            onDismiss = { routeDialog = false }
        )
    }

    if (scheduleAccessDialog) {
        AccessCodeDialog(
            store = accessStore,
            onVerified = {
                pendingWeeklyWindows?.let { windows ->
                    onPrefsChange(prefs.copy(weeklyLockWindows = windows))
                }
                pendingWeeklyWindows = null
                scheduleAccessDialog = false
            },
            onDismiss = {
                pendingWeeklyWindows = null
                scheduleAccessDialog = false
            }
        )
    }

    if (changeCodeDialog) {
        ChangeAccessCodeDialog(
            store = accessStore,
            allowNoExistingCode = true,
            onSaved = {
                changeCodeDialog = false
                pendingSensitiveAction?.invoke()
                pendingSensitiveAction = null
            },
            onDismiss = {
                changeCodeDialog = false
                pendingSensitiveAction = null
            }
        )
    }

    if (deviceOwnerInstructionsDialog) {
        DeviceOwnerInstructionsDialog(onDismiss = { deviceOwnerInstructionsDialog = false })
    }

    selectedSiteForAction?.let { site ->
        SiteActionDialog(
            site = site,
            onDismiss = { selectedSiteForAction = null },
            onReport = {
                selectedSiteForAction = null
                if (openDeveloperIssue(
                        context = context,
                        type = "תלונה על אתר מאושר",
                        title = site.name,
                        url = site.url,
                        reason = "המשתמש דיווח על בעיה באתר מאושר וביקש מהמפתח לבדוק את האתר."
                    )
                ) requestThanksDialog = true
            },
            onRemovalRequest = {
                selectedSiteForAction = null
                if (openDeveloperIssue(
                        context = context,
                        type = "בקשה להסרת אתר מאושר",
                        title = site.name,
                        url = site.url,
                        reason = "המשתמש מבקש לבדוק את האתר ולהסיר אותו מרשימת האתרים המאושרים."
                    )
                ) requestThanksDialog = true
            }
        )
    }

    if (siteRequestDialog) {
        SiteRequestDialog(
            onDismiss = { siteRequestDialog = false },
            onSubmit = { title, url, reason, contact ->
                siteRequestDialog = false
                if (openDeveloperIssue(
                        context = context,
                        type = "בקשת אישור אתר חדש",
                        title = title,
                        url = url,
                        reason = reason,
                        contact = contact
                    )
                ) requestThanksDialog = true
            }
        )
    }

    if (requestThanksDialog) {
        RequestThanksDialog(onDismiss = { requestThanksDialog = false })
    }

    if (appLocked) {
        AppLockDialog(
            store = accessStore,
            onUnlocked = {
                appLocked = false
            }
        )
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    prefs: BrowserPrefs,
    categories: List<String>,
    availableSites: List<Site>,
    favorites: Set<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    pagerState: PagerState,
    onSelectCategory: (Int) -> Unit,
    onOpenSite: (Site) -> Unit,
    onToggleFavorite: (Site) -> Unit,
    onLongPressSite: (Site) -> Unit
) {
    val scope = rememberCoroutineScope()
    val sitesByCategory = remember(availableSites) { availableSites.groupBy { it.category } }
    val searchResults by remember(availableSites, query) {
        derivedStateOf {
            val normalizedQuery = query.trim().lowercase()
            if (normalizedQuery.isBlank()) emptyList()
            else availableSites.filter {
                listOf(it.name, it.url, it.host, it.category)
                    .joinToString(" ")
                    .lowercase()
                    .contains(normalizedQuery)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "חיפוש") },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "נקה חיפוש")
                    }
                }
            },
            placeholder = { Text("חיפוש ברשימת האתרים — שם, כתובת, דומיין או קטגוריה") }
        )

        if (query.isNotBlank()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 20.dp, start = 8.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (prefs.compact) 6.dp else 9.dp)
            ) {
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("תוצאות חיפוש ברשימת האתרים", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                searchResults.size.toString() + " אתרים נמצאו",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (searchResults.isEmpty()) {
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(42.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("לא נמצאו אתרים ברשימה", fontWeight = FontWeight.SemiBold)
                                Text("נסה שם אתר, כתובת, דומיין או קטגוריה אחרת.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                items(items = searchResults, key = { "search-" + siteKey(it) }) { site ->
                    SiteCard(
                        site = site,
                        favorite = siteKey(site) in favorites,
                        showCategory = prefs.showCategories,
                        showUrl = prefs.showUrls,
                        largeText = prefs.largeText,
                        rounded = prefs.roundedCards,
                        onOpen = { onOpenSite(site) },
                        onToggleFavorite = { onToggleFavorite(site) },
                        onLongPress = { onLongPressSite(site) }
                    )
                }
            }
        } else {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 10.dp,
                divider = {}
            ) {
                categories.forEachIndexed { index, name ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            onSelectCategory(index)
                            if (pagerState.currentPage != index) scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = {
                            Text(
                                if (name == "מועדפים") "★ מועדפים" else name,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp),
                beyondViewportPageCount = 0
            ) { page ->
                val pageName = categories[page]
                val pageSites = when (pageName) {
                    "מועדפים" -> availableSites.filter { siteKey(it) in favorites }
                    "הכול" -> availableSites
                    else -> sitesByCategory[pageName].orEmpty()
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (prefs.compact) 6.dp else 9.dp)
                ) {
                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (pageName == "מועדפים") Icons.Default.Star else Icons.Default.Menu, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(pageName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text(
                                            if (pageName == "מועדפים") pageSites.size.toString() + " אתרים שמורים"
                                            else pageSites.size.toString() + " אתרים",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    FilterChip(
                                        selected = true,
                                        onClick = {},
                                        label = { Text("מאושר") },
                                        leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                                    )
                                }
                            }
                        }
                    }
                    if (pageSites.isEmpty()) {
                        item {
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        if (pageName == "מועדפים") Icons.Default.StarBorder else Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(42.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        if (pageName == "מועדפים") "עדיין אין מועדפים" else "לא נמצאו אתרים",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (pageName == "מועדפים") "לחץ על הכוכב ליד אתר כדי לשמור אותו כאן."
                                        else "נסה חיפוש אחר.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    items(items = pageSites, key = { siteKey(it) }) { site ->
                        SiteCard(
                            site = site,
                            favorite = siteKey(site) in favorites,
                            showCategory = prefs.showCategories,
                            showUrl = prefs.showUrls,
                            largeText = prefs.largeText,
                            rounded = prefs.roundedCards,
                            onOpen = { onOpenSite(site) },
                            onToggleFavorite = { onToggleFavorite(site) },
                            onLongPress = { onLongPressSite(site) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteCard(
    site: Site,
    favorite: Boolean,
    showCategory: Boolean,
    showUrl: Boolean,
    largeText: Boolean,
    rounded: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLongPress: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongPress
            ),
        shape = if (rounded) {
            RoundedCornerShape(18.dp)
        } else {
            RoundedCornerShape(6.dp)
        }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            ) {
                Text(
                    site.name,
                    fontSize = if (largeText) 19.sp else 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (showCategory) {
                    Text(
                        site.category,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (showUrl) {
                    Text(
                        site.url,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (favorite) {
                        "הסר ממועדפים"
                    } else {
                        "הוסף למועדפים"
                    }
                )
            }
        }
    }
}

@Composable
private fun SiteActionDialog(
    site: Site,
    onDismiss: () -> Unit,
    onReport: () -> Unit,
    onRemovalRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("פעולות עבור ${site.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(site.url, style = MaterialTheme.typography.bodySmall)
                Text("בחר מה לשלוח למפתח לגבי האתר הזה:")
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onReport, modifier = Modifier.fillMaxWidth()) {
                    Text("תלונה למפתח")
                }
                OutlinedButton(onClick = onRemovalRequest, modifier = Modifier.fillMaxWidth()) {
                    Text("בקשה להסרת האתר")
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("ביטול")
                }
            }
        }
    )
}

@Composable
private fun SiteRequestDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var reason by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("בקשת אישור אתר") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("הזן אתר שאינו מופיע ברשימת האתרים המאושרים. הבקשה תיפתח למפתח עם הפרטים שמילאת.")
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("שם האתר") }
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("כתובת האתר") }
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("למה האתר נדרש?") }
                )
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("פרטי קשר להודעה מהמפתח (אופציונלי)") },
                    placeholder = { Text("לדוגמה: שם משתמש ב־GitHub או כתובת קשר") }
                )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    title.trim().isBlank() -> error = "הזן שם אתר"
                    !url.trim().startsWith("http://") && !url.trim().startsWith("https://") -> error = "הכתובת חייבת להתחיל ב־http:// או https://"
                    else -> onSubmit(title.trim(), url.trim(), reason.trim(), contact.trim())
                }
            }) { Text("שלח למפתח") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

private fun openDeveloperIssue(
    context: Context,
    type: String,
    title: String,
    url: String,
    reason: String,
    contact: String = ""
): Boolean {
    val issueTitle = "[$type] $title"
    val body = """
בקשה שנשלחה מתוך דפדפן מאושר

סוג: $type
שם האתר: $title
כתובת: $url

פרטים:
$reason

פרטי קשר להודעה מהמפתח:
${contact.ifBlank { "לא נמסרו" }}

נא לבדוק את האתר והבקשה.
""".trimIndent()
    val issueUrl = "https://github.com/ZYGY7678/A-database-of-approved-addresses-in-the-application/issues/new" +
        "?title=${Uri.encode(issueTitle)}&body=${Uri.encode(body)}"
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl)))
        true
    }.getOrElse { false }
}

@Composable
private fun RequestThanksDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("תודה רבה") },
        text = { Text("תודה רבה! נראה את פנייתכם בהקדם האפשרי.") },
        confirmButton = { Button(onClick = onDismiss) { Text("סגור") } }
    )
}

@Composable
private fun AccessCodeDialog(
    store: AccessCodeStore,
    onVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("שינוי מסלול מוגן")
            }
        },
        text = {
            Column {
                Text("הזן קוד גישה כדי לפתוח את בחירת המסלול.")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.filter(Char::isDigit).take(12)
                        error = false
                    },
                    singleLine = true,
                    isError = error,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("קוד גישה") },
                    supportingText = {
                        if (error) Text("קוד שגוי")
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (store.verify(code)) {
                        onVerified()
                    } else {
                        error = true
                    }
                }
            ) {
                Text("המשך")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}

@Composable
private fun RouteSelectionDialog(
    current: BrowserRoute,
    onSelect: (BrowserRoute) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("בחירת מסלול") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BrowserRoute.entries.forEach { route ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelect(route) }
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = current == route,
                                onClick = { onSelect(route) }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(route.title, fontWeight = FontWeight.Bold)
                                Text(
                                    route.description,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("סגור")
            }
        }
    )
}

@Composable
private fun ChangeAccessCodeDialog(
    store: AccessCodeStore,
    allowNoExistingCode: Boolean = false,
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    var oldCode by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val hasExistingCode = store.hasCode()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasExistingCode) "שינוי קוד מנהל" else "הגדרת קוד מנהל") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (hasExistingCode) "קוד המנהל מגן על כל הגדרות האבטחה שבתיקיית האבטחה."
                    else "לא מוגדר כרגע קוד גישה. הגדר קוד אחד שיגן על כל הגדרות האבטחה."
                )
                if (hasExistingCode) {
                    OutlinedTextField(
                        value = oldCode,
                        onValueChange = { oldCode = it.filter(Char::isDigit).take(12) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("קוד נוכחי") }
                    )
                }
                OutlinedTextField(
                    value = newCode,
                    onValueChange = {
                        newCode = it.filter(Char::isDigit).take(12)
                        error = ""
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("קוד חדש, לפחות 4 ספרות") },
                    isError = error.isNotBlank(),
                    supportingText = { if (error.isNotBlank()) Text(error) }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    hasExistingCode && !store.verify(oldCode) -> error = "הקוד הנוכחי שגוי"
                    !allowNoExistingCode && !hasExistingCode -> error = "יש להגדיר קוד מנהל"
                    newCode.length < 4 -> error = "הקוד החדש קצר מדי"
                    !store.changeCode(newCode) -> error = "לא ניתן לשמור את הקוד"
                    else -> onSaved()
                }
            }) {
                Text(if (hasExistingCode) "שמור" else "הגדר קוד")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    prefs: BrowserPrefs,
    securityHasCode: Boolean,
    onPrefsChange: (BrowserPrefs) -> Unit,
    onOpenSecurity: () -> Unit,
    onSiteRequest: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val deviceOwner = DeviceManagement.isDeviceOwner(context)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("הגדרות", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "חזור")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                SettingsHeader(
                    "אבטחה ומאבטח",
                    "כל הגדרות ההגנה, הסינון, המסלול וקוד המנהל נמצאות בתוך תיקייה מוגנת אחת."
                )
            }
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenSecurity) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("🔐 תיקיית אבטחה ומאבטח", fontWeight = FontWeight.Bold)
                            Text(
                                if (securityHasCode) {
                                    "מוגנת בקוד מנהל • כל הגדרות האבטחה נמצאות כאן"
                                } else {
                                    "אין קוד מנהל עדיין • בכניסה הראשונה תתבקש להגדיר קוד"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onOpenSecurity) { Text("כניסה") }
                    }
                }
            }

            item { SettingsHeader("זמן ותאריך", "שעון פנימי לתצוגה באפליקציה") }
            item { SettingSwitch("הצג שעון", "מציג את השעה בסרגל העליון.", prefs.showAppClock) { onPrefsChange(prefs.copy(showAppClock = it)) } }
            item { SettingSwitch("פורמט 24 שעות", "18:30 במקום 6:30 PM.", prefs.timeFormat24) { onPrefsChange(prefs.copy(timeFormat24 = it)) } }
            item {
                SettingInfo(
                    "כוונון זמן",
                    if (prefs.timeOffsetMinutes == 0) "מסונכרן לזמן המכשיר" else "הסטה של " + prefs.timeOffsetMinutes + " דקות"
                )
            }

            item { SettingsHeader("עיצוב", "התאם את המראה, הצפיפות והקריאה") }
            item { SettingSwitch("מצב כהה", "ממשק כהה", prefs.dark) { onPrefsChange(prefs.copy(dark = it)) } }
            item { SettingSwitch("ניגודיות גבוהה", "טקסט ורקע עם ניגודיות חזקה", prefs.highContrast) { onPrefsChange(prefs.copy(highContrast = it)) } }
            item { SettingSwitch("טקסט גדול", "מגדיל את הטקסט ברשימת האתרים", prefs.largeText) { onPrefsChange(prefs.copy(largeText = it)) } }
            item { SettingSwitch("כרטיסים מעוגלים", "עיצוב מודרני לכרטיסי האתרים", prefs.roundedCards) { onPrefsChange(prefs.copy(roundedCards = it)) } }
            item { SettingSwitch("תצוגה קומפקטית", "מציג יותר אתרים על המסך", prefs.compact) { onPrefsChange(prefs.copy(compact = it)) } }

            item { SettingsHeader("בקשות למפתח", "שלח בקשה לאתר שאינו מאושר או דיווח על אתר") }
            item {
                OutlinedButton(onClick = onSiteRequest, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("בקשת אישור לאתר שלא ברשימה")
                }
            }

            item { SettingsHeader("רשימת האתרים", "שליטה במה שמוצג במסך הבית") }
            item { SettingSwitch("הצג כתובות", "מציג את הכתובת המלאה", prefs.showUrls) { onPrefsChange(prefs.copy(showUrls = it)) } }
            item { SettingSwitch("הצג קטגוריות", "מציג את קטגוריית האתר", prefs.showCategories) { onPrefsChange(prefs.copy(showCategories = it)) } }

            item { SettingsHeader("גלישה ובטיחות", "מצב ההגנות הפעילות") }
            item { SettingInfo("גישה לפי המסלול", "הדפדפן מאמת את הכתובת גם בזמן ניווט פנימי.") }
            item { SettingInfo("חסימת מדיה", "תמונות, וידאו ואודיו נחסמים לפי מנגנון ההגנה הקיים.") }
            item { SettingInfo("מועדפים", "המועדפים נשמרים מקומית במכשיר.") }

            item { SettingsHeader("אודות", "מידע על האפליקציה") }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("דפדפן מאושר", fontWeight = FontWeight.Bold)
                        Text("פותח באהבה ע"י חייא שיאומי ממתמחים טופ", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecuritySettingsScreen(
    prefs: BrowserPrefs,
    route: BrowserRoute,
    onPrefsChange: (BrowserPrefs) -> Unit,
    onChangeRoute: () -> Unit,
    onChangeCode: () -> Unit,
    onDeviceOwnerInstructions: () -> Unit,
    onWeeklyLockChange: (List<WeeklyLockWindow>) -> Unit,
    onManagedAppsChange: (Set<String>, Set<String>) -> Unit,
    onBack: () -> Unit
) {
    val deviceOwner = DeviceManagement.isDeviceOwner(LocalContext.current)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔐 אבטחה ומאבטח", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "חזור")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                SettingsHeader(
                    "הגנה מרכזית",
                    if (deviceOwner) "המכשיר מוגדר כבעל־מכשיר — הגדרות האבטחה זמינות לשינוי." else "תצוגה מקדימה בלבד. ההגדרות נעולות עד שהאפליקציה תוגדר כבעלת המכשיר."
                )
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(route.title + " — " + route.description, fontWeight = FontWeight.Bold)
                            Text("מסלול הסינון הפעיל", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = onChangeRoute, enabled = deviceOwner) { Text(if (deviceOwner) "שינוי" else "נעול") }
                    }
                }
            }
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth(), onClick = onChangeCode, enabled = deviceOwner) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("קוד מנהל", fontWeight = FontWeight.SemiBold)
                            Text("קוד אחד שמגן על תיקיית האבטחה וכל ההגדרות שבתוכה", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item { SettingsHeader("הגנת המכשיר", "הגנה ברמת Android") }
            item {
                val owner = DeviceManagement.isDeviceOwner(LocalContext.current)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingInfo(
                        "מצב בעל המכשיר",
                        if (owner) "האפליקציה מוגדרת כבעלת המכשיר ואינה ניתנת להסרה רגילה"
                        else "טרם הוגדרה כבעלת המכשיר"
                    )
                    OutlinedButton(onClick = onDeviceOwnerInstructions, modifier = Modifier.fillMaxWidth()) {
                        Text("הוראות הפעלה והגדרת בעל המכשיר")
                    }
                }
            }

            item { SettingsHeader("הגנת הגלישה", "כל ההגנות כאן כפופות לקוד המנהל של תיקיית האבטחה") }
            item { SettingSwitch("חסימת פתיחה באפליקציות חיצוניות", "מונע מעבר מאושר לאפליקציות אחרות.", prefs.blockExternalApps, enabled = deviceOwner) { onPrefsChange(prefs.copy(blockExternalApps = it)) } }
            item { SettingSwitch("חסימת חלונות קופצים", "מונע פתיחת חלונות חדשים מתוך האתר.", prefs.blockPopups, enabled = deviceOwner) { onPrefsChange(prefs.copy(blockPopups = it)) } }
            item { SettingSwitch("השבתת JavaScript", "הגנה מחמירה יותר; חלק מהאתרים עלולים לא לעבוד.", prefs.disableJavascript, enabled = deviceOwner) { onPrefsChange(prefs.copy(disableJavascript = it)) } }
            item { SettingSwitch("מניעת צילומי מסך", "מפעיל FLAG_SECURE של Android.", prefs.preventScreenshots, enabled = deviceOwner) { onPrefsChange(prefs.copy(preventScreenshots = it)) } }
            item { SettingSwitch("ניקוי בעת יציאה", "נקה את מצב הגלישה בעת יציאה.", prefs.clearOnExit, enabled = deviceOwner) { onPrefsChange(prefs.copy(clearOnExit = it)) } }
            item { WeeklyLockScheduleSetting(prefs.weeklyLockWindows, onWeeklyLockChange, enabled = deviceOwner) }

            item { SettingsHeader("הגנת אפליקציות", "בחירת אפליקציות אחרות לנעילה ולמניעת הסרה") }
            item {
                ManagedAppsSetting(
                    lockedPackages = prefs.lockedPackages,
                    protectedUninstallPackages = prefs.protectedUninstallPackages,
                    enabled = deviceOwner,
                    onChange = onManagedAppsChange
                )
            }
            item { SettingInfo("הערה", "ההגנות בתיקייה הזו ממשיכות לפעול גם אחרי יציאה מהמסך.") }
        }
    }
}

private fun formatAppTime(format24: Boolean, offsetMinutes: Int): String {
    val calendar = java.util.Calendar.getInstance().apply {
        timeInMillis = System.currentTimeMillis() + offsetMinutes * 60_000L
    }
    val hour24 = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    val minute = calendar.get(java.util.Calendar.MINUTE)
    return if (format24) {
        "%02d:%02d".format(hour24, minute)
    } else {
        val hour = if (hour24 % 12 == 0) 12 else hour24 % 12
        val suffix = if (hour24 < 12) "AM" else "PM"
        "%d:%02d %s".format(hour, minute, suffix)
    }
}

@Composable
private fun SettingsHeader(title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ManagedAppsSetting(
    lockedPackages: Set<String>,
    protectedUninstallPackages: Set<String>,
    enabled: Boolean,
    onChange: (Set<String>, Set<String>) -> Unit
) {
    val context = LocalContext.current
    var dialogOpen by remember { mutableStateOf(false) }
    val apps = remember {
        context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_ALL
        ).mapNotNull { info ->
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName) null
            else ManagedApp(pkg, info.loadLabel(context.packageManager).toString())
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("נעילת אפליקציות אחרות", fontWeight = FontWeight.SemiBold)
            Text("בחר אפליקציות שהמכשיר ימנע מהן לפעול, ואפליקציות שיחסמו להסרה.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("נעולות: ${lockedPackages.size} • מוגנות מהסרה: ${protectedUninstallPackages.size}", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { dialogOpen = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (enabled) "ניהול אפליקציות" else "נעול עד הגדרת בעל המכשיר")
            }
        }
    }

    if (dialogOpen) {
        var localLocked by remember(lockedPackages) { mutableStateOf(lockedPackages) }
        var localProtected by remember(protectedUninstallPackages) { mutableStateOf(protectedUninstallPackages) }
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text("ניהול אפליקציות") },
            text = {
                Box(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(apps) { app ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(app.label, fontWeight = FontWeight.SemiBold)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("נעילה")
                                        Switch(checked = app.packageName in localLocked, onCheckedChange = { checked ->
                                            localLocked = if (checked) localLocked + app.packageName else localLocked - app.packageName
                                        })
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("מניעת הסרה")
                                        Switch(checked = app.packageName in localProtected, onCheckedChange = { checked ->
                                            localProtected = if (checked) localProtected + app.packageName else localProtected - app.packageName
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onChange(localLocked, localProtected); dialogOpen = false }) { Text("שמור") }
            },
            dismissButton = { TextButton(onClick = { dialogOpen = false }) { Text("ביטול") } }
        )
    }
}

private data class ManagedApp(val packageName: String, val label: String)

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun WeeklyLockScheduleSetting(
    windows: List<WeeklyLockWindow>,
    onChange: (List<WeeklyLockWindow>) -> Unit,
    enabled: Boolean = true
) {
    var dialog by remember { mutableStateOf(false) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("נעילה לפי לוח זמנים", fontWeight = FontWeight.SemiBold)
            if (!enabled) Text("נעול עד הגדרת בעל המכשיר", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (windows.isEmpty()) "כבוי — האפליקציה אינה נעולה לפי שעות"
                else "${windows.size} טווחי נעילה מוגדרים לשבוע",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            windows.forEach { window ->
                val daysText = window.days.sorted().joinToString(" ") { dayName(it) }
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(daysText, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${formatLockMinutes(window.startMinutes)} – ${formatLockMinutes(window.endMinutes)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = {
                            onChange(windows.filterNot { it == window })
                        }) { Text("הסר") }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { dialog = true }) { Text("הוסף טווח") }
                if (windows.isNotEmpty()) {
                    OutlinedButton(onClick = { onChange(emptyList()) }) { Text("נקה הכול") }
                }
            }
        }
    }

    if (dialog) {
        WeeklyLockWindowDialog(
            onDismiss = { dialog = false },
            onAdd = {
                onChange(windows + it)
                dialog = false
            }
        )
    }
}

private fun dayName(day: Int): String = when (day) {
    java.util.Calendar.SUNDAY -> "א׳"
    java.util.Calendar.MONDAY -> "ב׳"
    java.util.Calendar.TUESDAY -> "ג׳"
    java.util.Calendar.WEDNESDAY -> "ד׳"
    java.util.Calendar.THURSDAY -> "ה׳"
    java.util.Calendar.FRIDAY -> "ו׳"
    java.util.Calendar.SATURDAY -> "ש׳"
    else -> "?"
}

@Composable
private fun WeeklyLockWindowDialog(
    onDismiss: () -> Unit,
    onAdd: (WeeklyLockWindow) -> Unit
) {
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }
    var start by remember { mutableStateOf("22:00") }
    var end by remember { mutableStateOf("07:00") }
    var error by remember { mutableStateOf("") }

    fun parseTime(value: String): Int? {
        val parts = value.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הוספת זמן נעילה") },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 320.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 2.dp, end = 4.dp)
                ) {
                    item {
                        Text(
                            "בחר ימים ושעות לנעילה",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    item { Text("בחר את הימים שבהם הטווח יחול:") }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items((java.util.Calendar.SUNDAY..java.util.Calendar.SATURDAY).toList()) { day ->
                                FilterChip(
                                    selected = day in selectedDays,
                                    onClick = {
                                        selectedDays = if (day in selectedDays) {
                                            selectedDays - day
                                        } else {
                                            selectedDays + day
                                        }
                                    },
                                    label = { Text(dayName(day)) }
                                )
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = start,
                            onValueChange = { start = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("שעת התחלה — HH:MM") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = end,
                            onValueChange = { end = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("שעת סיום — HH:MM") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    item {
                        Text(
                            "אפשר גם 22:00–07:00 — הנעילה תמשיך אוטומטית אחרי חצות. אם המסך קטן, ניתן לגלול בתוך החלון.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (error.isNotBlank()) {
                        item { Text(error, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val startMinutes = parseTime(start)
                val endMinutes = parseTime(end)
                when {
                    selectedDays.isEmpty() -> error = "בחר לפחות יום אחד"
                    startMinutes == null || endMinutes == null -> error = "הזן שעות בפורמט HH:MM"
                    else -> onAdd(WeeklyLockWindow(selectedDays, startMinutes, endMinutes))
                }
            }) { Text("הוסף") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

@Composable
private fun AppLockDialog(
    store: AccessCodeStore,
    onUnlocked: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("האפליקציה ננעלה") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("האפליקציה נעולה כעת לפי לוח הזמנים. הזן את קוד הגישה כדי לפתוח את האפליקציה ולהמשיך.")
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(12); error = false },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("קוד גישה") },
                    isError = error
                )
                if (error) Text("קוד שגוי", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (store.verify(code)) onUnlocked() else error = true
            }) { Text("הזן קוד ופתח את האפליקציה") }
        }
    )
}

@Composable
private fun DeviceOwnerInstructionsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הפעלת האפליקציה ובעל המכשיר") },
        text = {
            Box(Modifier.heightIn(max = 430.dp)) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 4.dp)
                ) {
                    item { Text("הכנה לפני ההגדרה", fontWeight = FontWeight.Bold) }
                    item { Text("• התקן את קובץ ה־APK של דפדפן מאושר במכשיר.") }
                    item { Text("• פתח את הגדרות Android → חשבונות (או סיסמאות וחשבונות).") }
                    item { Text("• הסר מהמכשיר את חשבונות Google הקיימים לפני ניסיון ההגדרה. במכשירים רבים Android לא מאפשר להגדיר Device Owner כאשר כבר קיים חשבון משתמש מנוהל או חשבון Google.") }
                    item { Text("• ודא שהמכשיר אינו מנוהל כבר על ידי אפליקציית ניהול אחרת.") }
                    item { Text("הפעלת אפשרויות למפתחים", fontWeight = FontWeight.Bold) }
                    item { Text("1. פתח בטלפון את הגדרות Android → אודות הטלפון.") }
                    item { Text("2. חפש את „מספר Build“ / „מספר גרסה“. בחלק מהמכשירים הוא נמצא תחת „פרטי תוכנה“ או „מידע על התוכנה“.") }
                    item { Text("3. לחץ על „מספר Build“ 7 פעמים ברצף. אם Android מבקש קוד נעילה, הזן אותו.") }
                    item { Text("4. חזור למסך הראשי של ההגדרות. כעת אמור להופיע „אפשרויות למפתחים“ — בדרך כלל תחת „מערכת“, „הגדרות נוספות“ או „אפשרויות מערכת“.") }
                    item { Text("5. היכנס ל„אפשרויות למפתחים“ והפעל את המתג הראשי של האפשרויות למפתחים.") }
                    item { Text("הפעלת ניפוי USB", fontWeight = FontWeight.Bold) }
                    item { Text("6. בתוך „אפשרויות למפתחים“ חפש „ניפוי USB“ או „USB debugging“ והפעל אותו.") }
                    item { Text("7. חבר את הטלפון למחשב. אם מופיעה הודעה „לאפשר ניפוי USB?“, אשר אותה. מומלץ לסמן „אפשר תמיד ממחשב זה“ רק אם זה המחשב שלך.") }
                    item { Text("הגדרה מהמחשב", fontWeight = FontWeight.Bold) }
                    item { Text("1. חבר את הטלפון למחשב באמצעות USB ואשר בטלפון את חלון הרשאת ניפוי ה־USB אם הוא מופיע.") }
                    item { Text("2. הפעל את כלי Approved Browser ADB במחשב.") }
                    item { Text("3. ודא שהכלי מציג שהמכשיר מחובר.") }
                    item { Text("4. בחר קוד גישה חדש בן 4–12 ספרות והקלד אותו גם בשדה האימות.") }
                    item { Text("5. לחץ על „הגדר בעל מכשיר + קוד גישה“. הכלי יבדוק שהאפליקציה מותקנת ושאין בעל מכשיר אחר.") }
                    item { Text("6. אשר את הפעולה. הכלי יריץ את פקודת ADB ויגדיר את דפדפן מאושר כבעל המכשיר.") }
                    item { Text("7. לאחר מכן הכלי יעביר את קוד הגישה לאפליקציה. האפליקציה תשמור רק גיבוב SHA-256 של הקוד.") }
                    item { Text("אם ההגדרה נכשלת", fontWeight = FontWeight.Bold) }
                    item { Text("• בדוק שחשבון Google הוסר מהמכשיר.") }
                    item { Text("• בדוק שאין כבר Device Owner או אפליקציית ניהול אחרת.") }
                    item { Text("• בדוק שה־USB debugging מאושר ושהמכשיר מופיע כ־connected בכלי.") }
                    item { Text("• אם המכשיר כבר הוגדר בעבר כמכשיר מנוהל, ייתכן שיהיה צורך באיפוס למצב מתאים לפני הגדרת בעל מכשיר חדש.") }
                    item {
                        Text(
                            "חשוב: הגדרת Device Owner היא הגדרה ברמת Android ויכולה לשנות את יכולת המשתמש להסיר את האפליקציה ולנהל את המכשיר. בצע אותה רק במכשיר שנועד לכך.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } }
    )
}

@Composable
private fun SettingInfo(title: String, subtitle: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
