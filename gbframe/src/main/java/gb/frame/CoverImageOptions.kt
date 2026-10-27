package gb.frame

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf

/** Shared cover-source chooser. The app retains ownership of its local document picker. */
@Composable
fun CoverImageOptions(title: String, onLocalSearch: () -> Unit, onDismiss: () -> Unit, onApplied: () -> Unit) {
    var webSearch by remember(title) { mutableStateOf(false) }
    if (webSearch) {
        CoverWebSearch(title, onClose = { webSearch = false }, onApplied = onApplied)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cover Image") },
        text = {
            Column {
                TextButton(onClick = { webSearch = true }) { Text("Web Search") }
                TextButton(onClick = onLocalSearch) { Text("Local Search") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } },
    )
}
