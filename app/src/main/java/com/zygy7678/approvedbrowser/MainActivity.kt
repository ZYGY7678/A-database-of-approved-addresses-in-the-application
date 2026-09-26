package com.zygy7678.approvedbrowser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private data class BrowserPrefs(
    val dark: Boolean = false,
    val highContrast: Boolean = false,
    val compact: Boolean = false,
    val showUrls: Boolean = true,
    val showCategories: Boolean = true,
    val largeText: Boolean = false,
    val roundedCards: Boolean = true
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
        roundedCards = p.getBoolean("roundedCards", true)
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
            .apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PrefStore(this)
        setContent {
            var prefs by remember { mutableStateOf(store.load()) }
            ApprovedBrowserTheme(
                darkTheme = prefs.dark,
                highContrast = prefs.highContrast
            ) {
                ApprovedBrowserApp(
                    prefs = prefs,
                    onPrefsChange = { next -> prefs = next; store.save(next) }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApprovedBrowserApp(
    prefs: BrowserPrefs,
    onPrefsChange: (BrowserPrefs) -> Unit
) {
    val context = LocalContext.current
    val sites = remember { WhitelistRepository.load(context) }
    var url by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var home by remember { mutableStateOf(true) }
    var settings by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("הכול") }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val categories = remember(sites) { listOf("הכול") + sites.map { it.category }.distinct() }
    val filtered = remember(sites, query, category) {
        sites.filter {
            (category == "הכול" || it.category == category) &&
            (query.isBlank() || it.name.contains(query, true) || it.url.contains(query, true))
        }
    }

    if (settings) {
        SettingsScreen(
            prefs = prefs,
            onPrefsChange = onPrefsChange,
            onBack = { settings = false }
        )
        return
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("דפדפן מאושר", fontWeight = FontWeight.Bold)
                            Text("100 אתרים • טקסט בלבד", fontSize = 11.sp)
                        }
                    },
                    actions = {
                        IconButton({ settings = true }) { Icon(Icons.Default.Settings, "הגדרות") }
                        IconButton({ home = true }) { Icon(Icons.Default.Home, "בית") }
                        IconButton({ webView?.goBack() }) { Icon(Icons.Default.ArrowBack, "חזור") }
                        IconButton({ webView?.goForward() }) { Icon(Icons.Default.ArrowForward, "קדימה") }
                        IconButton({ webView?.reload() }) { Icon(Icons.Default.Refresh, "רענן") }
                    }
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("חפש או הזן כתובת מאושרת") }
                    )
                    IconButton({
                        val candidate = if (address.startsWith("http", true)) address else "https://$address"
                        if (WhitelistRepository.isAllowed(candidate, sites)) {
                            url = candidate
                            home = false
                            blocked = false
                        } else blocked = true
                    }) { Icon(Icons.Default.Search, "פתח") }
                }
                if (blocked) {
                    Text(
                        "הכתובת אינה ברשימה המאושרת",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
            }
        }
    ) { padding ->
        if (home) {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (prefs.compact) 5.dp else 9.dp)
            ) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("אתרים מאושרים", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "100 כתובות שימושיות שנבחרו ידנית. האפליקציה חוסמת תמונות, וידאו, אודיו וכתובות שאינן מאושרות.",
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = if (prefs.largeText) 17.sp else 14.sp
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        placeholder = { Text("חיפוש באתרי הרשימה") },
                        leadingIcon = { Icon(Icons.Default.Search, null) }
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = { category = "הכול" },
                            label = { Text("הכול") },
                            leadingIcon = { Icon(Icons.Default.Tune, null) }
                        )
                        Text(
                            "נמצאו ${filtered.size}",
                            modifier = Modifier.align(Alignment.CenterVertically),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                items(filtered) { site ->
                    val shape = if (prefs.roundedCards) RoundedCornerShape(18.dp) else RoundedCornerShape(4.dp)
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = shape,
                        onClick = {
                            url = site.url
                            address = site.url
                            home = false
                            blocked = false
                        }
                    ) {
                        Column(Modifier.padding(if (prefs.compact) 10.dp else 14.dp)) {
                            Text(site.name, fontSize = if (prefs.largeText) 19.sp else 16.sp, fontWeight = FontWeight.SemiBold)
                            if (prefs.showCategories) {
                                Text(site.category, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            if (prefs.showUrls) {
                                Text(site.url, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(padding),
                factory = { ctx ->
                    WebView(ctx).also { view ->
                        webView = view
                        configureApprovedWebView(view)
                        view.webViewClient = BrowserWebViewClient({ sites }) {
                            blocked = true
                            home = false
                        }
                        view.loadUrl(url)
                    }
                },
                update = { view ->
                    webView = view
                    if (view.url != url && url.isNotBlank()) view.loadUrl(url)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    prefs: BrowserPrefs,
    onPrefsChange: (BrowserPrefs) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("הגדרות", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "חזור") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SettingsHeader("עיצוב", "התאם את המראה, הצפיפות והקריאה של האפליקציה") }
            item { SettingSwitch("מצב כהה", "ממשק כהה ונוח יותר בחושך", prefs.dark) { onPrefsChange(prefs.copy(dark = it)) } }
            item { SettingSwitch("ניגודיות גבוהה", "טקסט ורקע עם ניגודיות חזקה יותר", prefs.highContrast) { onPrefsChange(prefs.copy(highContrast = it)) } }
            item { SettingSwitch("טקסט גדול", "מגדיל את הטקסט ברשימת האתרים", prefs.largeText) { onPrefsChange(prefs.copy(largeText = it)) } }
            item { SettingSwitch("כרטיסים מעוגלים", "עיצוב מודרני לכרטיסי האתרים", prefs.roundedCards) { onPrefsChange(prefs.copy(roundedCards = it)) } }
            item { SettingSwitch("תצוגה קומפקטית", "מציג יותר אתרים על המסך", prefs.compact) { onPrefsChange(prefs.copy(compact = it)) } }

            item { SettingsHeader("רשימת האתרים", "שליטה במה שמופיע במסך הבית") }
            item { SettingSwitch("הצג כתובות", "מציג את הכתובת המלאה מתחת לשם האתר", prefs.showUrls) { onPrefsChange(prefs.copy(showUrls = it)) } }
            item { SettingSwitch("הצג קטגוריות", "מציג את סוג האתר מתחת לשם", prefs.showCategories) { onPrefsChange(prefs.copy(showCategories = it)) } }

            item { SettingsHeader("גלישה ובטיחות", "הגנות שאינן ניתנות לכיבוי מהגדרות העיצוב") }
            item { SettingInfo("רק כתובות מאושרות", "ניווט לאתר שאינו ברשימה נחסם.") }
            item { SettingInfo("טקסט בלבד", "תמונות, GIF, SVG, וידאו ואודיו נחסמים.") }
            item { SettingInfo("בינה מלאכותית", "רק ChatGPT, Grok, Claude ו-Gemini נמצאים ברשימה.") }

            item { SettingsHeader("מידע על האפליקציה", "מצב הרשימה והגרסה") }
            item { SettingInfo("100 אתרים", "הרשימה כוללת 4 שירותי AI, 20 כניסות פורומים ו-76 אתרים ושירותים נוספים.") }
            item { SettingInfo("בדיקת התאמה", "הבחירה מבוססת על זהות האתר, סוג השירות והקשר שלו לציבור דתי/חרדי או לצורך ציבורי חיוני.") }
        }
    }
}

@Composable
private fun SettingsHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun SettingInfo(title: String, subtitle: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
