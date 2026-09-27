package com.zygy7678.approvedbrowser

import android.graphics.Color
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.net.URI

class BrowserWebViewClient(
    private val sitesProvider: () -> List<Site>,
    private val onBlockedNavigation: () -> Unit,
    private val onPageStateChanged: (String?, Boolean) -> Unit
) : WebViewClient() {

    private val blockedMediaExtensions = listOf(
        ".mp4", ".webm", ".mov", ".mkv", ".avi", ".m4v",
        ".mp3", ".wav", ".ogg", ".m4a"
    )

    private val imageExtensions = listOf(
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".avif", ".svg", ".ico"
    )

    private val imageEnabledHosts = setOf(
        "mitmachim.top",
        "prog.co.il"
    )

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        onPageStateChanged(url, true)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        onPageStateChanged(url, false)
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url?.toString() ?: return true

        // Only approved HTTP(S) pages may become the main document.
        if (
            (url.startsWith("https://", true) || url.startsWith("http://", true)) &&
            WhitelistRepository.isAllowed(url, sitesProvider())
        ) {
            return false
        }

        onBlockedNavigation()
        return true
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val requestUrl = request?.url ?: return null
        val url = requestUrl.toString().lowercase()
        val path = requestUrl.path?.lowercase() ?: ""
        val mainHost = runCatching {
            URI(view?.url ?: "").host?.lowercase()
        }.getOrNull().orEmpty()

        // Keep media blocked as before.
        if (blockedMediaExtensions.any(path::endsWith)) {
            return emptyResponse()
        }

        // Images are enabled only on the two sites explicitly approved by the user.
        // Accept headers catch many image URLs that have no .jpg/.png suffix.
        val accept = request?.requestHeaders
            ?.entries
            ?.firstOrNull { it.key.equals("Accept", ignoreCase = true) }
            ?.value
            ?.lowercase()
            .orEmpty()

        val looksLikeImage =
            imageExtensions.any(path::endsWith) ||
            accept.contains("image/")

        if (looksLikeImage && !isImageEnabledHost(mainHost)) {
            return emptyResponse()
        }

        // Important compatibility change:
        // sub-resources such as CSS, JavaScript, fonts and APIs may come from
        // a site's CDN or other supporting host. Blocking every non-whitelisted
        // sub-resource was the main reason many otherwise-approved sites broke.
        //
        // Top-level navigation is still restricted by shouldOverrideUrlLoading.
        return super.shouldInterceptRequest(view, request)
    }

    private fun isImageEnabledHost(host: String): Boolean {
        val normalized = host.removePrefix("www.")
        return imageEnabledHosts.any {
            normalized == it || normalized.endsWith(".$it")
        }
    }

    private fun emptyResponse() = WebResourceResponse(
        "text/plain",
        "UTF-8",
        ByteArrayInputStream(ByteArray(0))
    )
}

fun configureApprovedWebView(
    webView: WebView,
    disableJavascript: Boolean = false,
    blockPopups: Boolean = true
) {
    webView.setBackgroundColor(Color.WHITE)

    val settings = webView.settings
    settings.apply {
        // Normal Chromium/WebView browsing behaviour.
        javaScriptEnabled = !disableJavascript
        domStorageEnabled = true
        databaseEnabled = true

        loadsImagesAutomatically = true
        blockNetworkImage = false

        // Use the normal WebView/Chromium cache instead of forcing fresh loads.
        cacheMode = WebSettings.LOAD_DEFAULT

        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        // Keep local-file access closed.
        allowFileAccess = false
        allowContentAccess = false

        javaScriptCanOpenWindowsAutomatically = false
        mediaPlaybackRequiresUserGesture = true

        // Keep popups/new windows disabled unless the setting explicitly allows them.
        setSupportMultipleWindows(!blockPopups)

        safeBrowsingEnabled = true

        // More natural mobile-page sizing and zoom.
        builtInZoomControls = true
        displayZoomControls = false
        setSupportZoom(true)
        useWideViewPort = true
        loadWithOverviewMode = false
        textZoom = 100

        // Better compatibility with modern sites and forms.
        javaScriptCanOpenWindowsAutomatically = false
    }

    val cookies = CookieManager.getInstance()
    cookies.setAcceptCookie(true)
    cookies.setAcceptThirdPartyCookies(webView, true)

    // WebChromeClient is needed for normal browser-like JavaScript dialogs,
    // page titles/favicons and other Chromium UI callbacks.
    webView.webChromeClient = WebChromeClient()
}
