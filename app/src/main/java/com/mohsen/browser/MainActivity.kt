package com.mohsen.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private var webView: WebView? = null
    private lateinit var root: LinearLayout
    private lateinit var addressBar: EditText

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                closeAndWipe()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        configureGlobalWebViewPrivacy()
        buildUi()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onStop() {
        wipeBrowserData()
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        destroyWebView()
        wipeBrowserData()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val currentWebView = webView
        if (keyCode == KeyEvent.KEYCODE_BACK && currentWebView?.canGoBack() == true) {
            currentWebView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.rgb(245, 247, 250))
        }

        addressBar = EditText(this).apply {
            hint = "https://example.com"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            textSize = 15f
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, actionId, event ->
                val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
                if (actionId == EditorInfo.IME_ACTION_GO || enterPressed) {
                    loadTypedAddress()
                    true
                } else {
                    false
                }
            }
        }

        val goButton = Button(this).apply {
            text = "Go"
            setOnClickListener { loadTypedAddress() }
        }

        val wipeButton = Button(this).apply {
            text = "Clear"
            setOnClickListener {
                addressBar.text.clear()
                recreateCleanWebView()
                Toast.makeText(this@MainActivity, "Cleared", Toast.LENGTH_SHORT).show()
            }
        }

        toolbar.addView(addressBar, LinearLayout.LayoutParams(0, dp(48), 1f))
        toolbar.addView(goButton, LinearLayout.LayoutParams(dp(72), dp(48)))
        toolbar.addView(wipeButton, LinearLayout.LayoutParams(dp(88), dp(48)))
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        recreateCleanWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun recreateCleanWebView() {
        destroyWebView()
        wipeBrowserData()

        val newWebView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            overScrollMode = View.OVER_SCROLL_NEVER

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                databaseEnabled = false
                cacheMode = WebSettings.LOAD_NO_CACHE
                setGeolocationEnabled(false)
                allowFileAccess = false
                allowContentAccess = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                userAgentString = "$userAgentString browser-private"
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url ?: return true
                    return if (isAllowedWebUrl(url)) {
                        false
                    } else {
                        Toast.makeText(this@MainActivity, "Blocked URL", Toast.LENGTH_SHORT).show()
                        true
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    view?.clearHistory()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    callback?.invoke(origin, false, false)
                }
            }

            setDownloadListener(DownloadListener { _, _, _, _, _ ->
                Toast.makeText(this@MainActivity, "Downloads are disabled", Toast.LENGTH_SHORT).show()
            })
        }

        webView = newWebView
        root.addView(newWebView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
    }

    private fun loadTypedAddress() {
        val normalized = normalizeAddress(addressBar.text?.toString().orEmpty())
        val uri = Uri.parse(normalized)
        if (!isAllowedWebUrl(uri)) {
            Toast.makeText(this, "Only HTTPS/HTTP URLs are allowed", Toast.LENGTH_SHORT).show()
            return
        }
        addressBar.setText(normalized, TextView.BufferType.EDITABLE)
        webView?.loadUrl(normalized)
    }

    private fun normalizeAddress(rawAddress: String): String {
        val trimmed = rawAddress.trim()
        if (trimmed.isEmpty()) return "about:blank"
        return if (trimmed.startsWith("https://", true) || trimmed.startsWith("http://", true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun isAllowedWebUrl(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme == "https" || scheme == "http"
    }

    private fun closeAndWipe() {
        destroyWebView()
        wipeBrowserData()
        finishAndRemoveTask()
    }

    private fun destroyWebView() {
        webView?.let { view ->
            runCatching {
                view.stopLoading()
                view.loadUrl("about:blank")
                view.clearHistory()
                view.clearCache(true)
                view.clearFormData()
                view.removeAllViews()
                root.removeView(view)
                view.destroy()
            }
        }
        webView = null
    }

    private fun configureGlobalWebViewPrivacy() {
        CookieManager.getInstance().apply {
            setAcceptCookie(false)
            removeAllCookies(null)
            flush()
        }
        WebStorage.getInstance().deleteAllData()
    }

    private fun wipeBrowserData() {
        webView?.apply {
            clearHistory()
            clearCache(true)
            clearFormData()
        }
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            removeSessionCookies(null)
            flush()
        }
        WebStorage.getInstance().deleteAllData()
        cacheDir.deleteRecursively()
        codeCacheDir.deleteRecursively()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
