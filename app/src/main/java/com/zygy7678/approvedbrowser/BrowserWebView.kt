package com.zygy7678.approvedbrowser

import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

class BrowserWebViewClient(
    private val sitesProvider: () -> List<Site>,
    private val onBlockedNavigation: () -> Unit,
    private val onPageStateChanged: (String?, Boolean) -> Unit
) : WebViewClient() {
    private val blockedExtensions = listOf(".jpg",".jpeg",".png",".gif",".webp",".bmp",".avif",".svg",".ico",".mp4",".webm",".mov",".mkv",".avi",".m4v",".mp3",".wav",".ogg",".m4a")
    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        onPageStateChanged(url, true)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        onPageStateChanged(url, false)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return true
        if (url.startsWith("https://") || url.startsWith("http://")) {
            if (WhitelistRepository.isAllowed(url, sitesProvider())) return false
        }
        onBlockedNavigation()
        return true
    }
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString()?.lowercase() ?: return null
        val path = url.substringBefore("?")
        if (blockedExtensions.any(path::endsWith)) return emptyResponse()
        if ((url.startsWith("http://") || url.startsWith("https://")) && !WhitelistRepository.isAllowed(url, sitesProvider())) return emptyResponse()
        return super.shouldInterceptRequest(view, request)
    }
    private fun emptyResponse() = WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
}

fun configureApprovedWebView(
    webView: WebView,
    disableJavascript: Boolean = false,
    blockPopups: Boolean = true
) {
    webView.setBackgroundColor(Color.WHITE)
    webView.settings.apply {
        javaScriptEnabled = !disableJavascript
        domStorageEnabled = true
        loadsImagesAutomatically = false
        blockNetworkImage = true
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        allowFileAccess = false
        allowContentAccess = false
        javaScriptCanOpenWindowsAutomatically = false
        mediaPlaybackRequiresUserGesture = true
        setSupportMultipleWindows(!blockPopups)
        safeBrowsingEnabled = true
        builtInZoomControls = true
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = true
        textZoom = 100
    }
}
