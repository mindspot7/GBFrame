package gb.frame

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Shared cover-source chooser. The app retains ownership of its local document picker. */
@Composable
fun CoverImageOptions(title: String, onLocalSearch: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cover Image") },
        text = {
            Column {
                TextButton(onClick = {
                    val query = Uri.encode(Covers.webSearchQuery(title))
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?tbm=isch&q=$query"))
                    runCatching { context.startActivity(intent) }
                }) { Text("Web Search") }
                TextButton(onClick = onLocalSearch) { Text("Local Search") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } },
    )
}
