package com.zygy7678.approvedbrowser

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ApprovedBrowserTheme { ApprovedBrowserApp() } }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovedBrowserApp() {
    val context = LocalContext.current
    val sites = remember { WhitelistRepository.load(context) }
    val allowedHosts = remember(sites) { sites.map { it.host }.toSet() }
    var url by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var home by remember { mutableStateOf(true) }
    var blocked by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("דפדפן מאושר") },
                    actions = {
                        IconButton({ home = true }) { Icon(Icons.Default.Home, "בית") }
                        IconButton({ webView?.goBack() }) { Icon(Icons.Default.ArrowBack, "חזור") }
                        IconButton({ webView?.goForward() }) { Icon(Icons.Default.ArrowForward, "קדימה") }
                        IconButton({ webView?.reload() }) { Icon(Icons.Default.Refresh, "רענן") }
                    }
                )
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("כתובת אתר מאושרת") }
                    )
                    IconButton({
                        val candidate = if (address.startsWith("http")) address else "https://$address"
                        if (WhitelistRepository.isAllowed(candidate, sites)) {
                            url = candidate
                            home = false
                            blocked = false
                        } else blocked = true
                    }) { Icon(Icons.Default.Search, "פתח") }
                }
                if (blocked) Text("הכתובת אינה מאושרת", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
    ) { padding ->
        if (home) {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
                item {
                    Text("אתרים מאושרים", style = MaterialTheme.typography.headlineSmall)
                    Text("500 כתובות • טקסט בלבד • ללא תמונות, וידאו ואודיו", modifier = Modifier.padding(vertical = 8.dp))
                }
                items(sites) { site ->
                    ElevatedButton(
                        onClick = { url = site.url; address = site.url; home = false; blocked = false },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) { Text(site.name) }
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
