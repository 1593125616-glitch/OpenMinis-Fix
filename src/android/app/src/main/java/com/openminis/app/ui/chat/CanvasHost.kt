package com.openminis.app.ui.chat

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.openminis.app.tools.CanvasSanitizer

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CanvasHost(raw: String, modifier: Modifier = Modifier) {
    val extracted = CanvasSanitizer.extract(raw)
    if (extracted == null) return
    val (title, html) = extracted
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 420.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
        update = { view ->
            view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        },
    )
}
