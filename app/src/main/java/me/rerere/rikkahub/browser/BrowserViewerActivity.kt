package me.rerere.rikkahub.browser

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.RikkahubTheme

/**
 * Mirrored viewer for the headless chat browser. Displays a second WebView that follows the
 * headless session's current URL (sharing the app-wide CookieManager), so you can watch the
 * agent navigate. It does not drive the agent, only mirrors it.
 */
class BrowserViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RikkahubTheme { ViewerScreen() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerScreen() {
    val context = LocalContext.current
    val url by HeadlessBrowserSession.urlFlow.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(url) {
        if (url.isNotBlank()) webView?.loadUrl(url)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = url.ifBlank { "Browser viewer" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(imageVector = HugeIcons.ArrowLeft01, contentDescription = "Back")
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        AndroidView(
            factory = { context -> WebView(context).also { webView = it } },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
