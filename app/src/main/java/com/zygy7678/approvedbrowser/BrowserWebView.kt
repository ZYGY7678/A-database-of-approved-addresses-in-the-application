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
        val path = requestUrl.path?.lowercase() ?: ""

        // Keep the existing protection against direct audio/video files.
        // Images, CSS, JavaScript, fonts, XHR/fetch and CDN resources are
        // otherwise allowed like a normal Chromium/WebView page.
        if (blockedMediaExtensions.any(path::endsWith)) {
            return emptyResponse()
        }

        // Do not filter images by hostname. Modern sites often serve images
        // from CDNs, image proxies, signed URLs or different subdomains.
        // Blocking those resources makes approved sites look broken.
        //
        // The security boundary remains the top-level navigation check above:
        // a resource may load for an approved page, but navigation to an
        // unapproved HTTP(S) page is still blocked.
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

        // Present a Chrome-like mobile user agent. Some sites otherwise detect
        // Android WebView ("wv") and serve a reduced or incompatible page.
        userAgentString = userAgentString
            .replace("; wv", "")
            .replace(" Version/4.0", "")
            .replace(Regex(" Chrome/[^ ]+"), " Chrome/140.0.0.0")


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
