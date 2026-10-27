package gb.frame

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** User-driven image search. Back walks web history, then returns to the cover chooser. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun CoverWebSearch(title: String, onClose: () -> Unit, onApplied: () -> Unit) {
    var browser by remember { mutableStateOf<WebView?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val applied by rememberUpdatedState(onApplied)
    fun back() {
        if (saving) return
        val view = browser
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }
    DisposableEffect(Unit) {
        onDispose { browser?.apply { stopLoading(); destroy() } }
    }
    Dialog(
        onDismissRequest = { back() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { back() }, enabled = !saving) { Text("Back") }
                    TextButton(onClick = onClose, enabled = !saving) { Text("Close search") }
                }
                Text("Tap a cover to select it, or hold an image. Then choose Use image.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
                    TextButton(onClick = { error = null; browser?.reload() }) { Text("Retry") }
                }
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { context ->
                        WebView(context).apply {
                            browser = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.setSupportMultipleWindows(false)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val uri = request.url
                                    if (uri.scheme == "gbcover" && uri.host == "select") {
                                        uri.getQueryParameter("url")?.let { selected = it }
                                        return true
                                    }
                                    return uri.scheme !in listOf("https", "http")
                                }
                                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                    loading = true
                                    error = null
                                }
                                override fun onPageFinished(view: WebView, url: String?) {
                                    loading = false
                                    view.evaluateJavascript(IMAGE_SELECTION_SCRIPT, null)
                                }
                                override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: android.webkit.WebResourceError) {
                                    if (request.isForMainFrame) { loading = false; error = "Could not load this page. Check your connection and retry." }
                                }
                            }
                            setOnLongClickListener {
                                val hit = hitTestResult
                                if (hit.type == WebView.HitTestResult.IMAGE_TYPE || hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                                    hit.extra?.let { selected = it }
                                    true
                                } else false
                            }
                            loadUrl("https://www.google.com/search?tbm=isch&q=" + Uri.encode(Covers.webSearchQuery(title)))
                        }
                    },
                )
            }
        }
    }
    selected?.let { url ->
        var bytes by remember(url) { mutableStateOf<ByteArray?>(null) }
        var preview by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
        var failure by remember(url) { mutableStateOf<String?>(null) }
        LaunchedEffect(url) {
            try {
                val data = Covers.webImage(url)
                val bitmap = withContext(Dispatchers.Default) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 768) inSampleSize *= 2
                    }
                    requireNotNull(BitmapFactory.decodeByteArray(data, 0, data.size, options))
                }
                bytes = data; preview = bitmap
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { failure = "Could not read this image. Try another cover." }
        }
        AlertDialog(
            onDismissRequest = { if (!saving) selected = null },
            title = { Text("Use this cover?") },
            text = {
                Column {
                    preview?.let { Image(it.asImageBitmap(), "Selected cover", Modifier.fillMaxWidth().height(220.dp)) }
                    if (bytes == null && failure == null) CircularProgressIndicator()
                    failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (saving) CircularProgressIndicator()
                }
            },
            confirmButton = {
                TextButton(enabled = bytes != null && !saving, onClick = {
                    val data = bytes ?: return@TextButton
                    saving = true
                    scope.launch {
                        try { Covers.setCustomWeb(title, data); applied() }
                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { failure = "Could not save cover. Please try again." }
                        finally { saving = false }
                    }
                }) { Text("Use image") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { selected = null }) { Text("Cancel") } },
        )
    }
}

// Avoid a JavaScript interface: untrusted pages can only offer an image URL through a navigation intercepted above.
private val IMAGE_SELECTION_SCRIPT = """
    (function() {
      if (document.gbCoverSelectionInstalled) return;
      document.gbCoverSelectionInstalled = true;
      document.addEventListener('click', function(event) {
        var image = event.target.closest && event.target.closest('img');
        if (!image || image.width < 70 || image.height < 70) return;
        var source = image.currentSrc || image.src;
        var link = image.closest('a');
        if (link) { try { source = new URL(link.href).searchParams.get('imgurl') || source; } catch (_) {} }
        if (!source || !/^(https?:|data:image\/)/.test(source)) return;
        event.preventDefault(); event.stopImmediatePropagation();
        window.location.href = 'gbcover://select?url=' + encodeURIComponent(source);
      }, true);
    })();
""".trimIndent()
