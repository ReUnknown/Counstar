package app.counstar.ui.watch

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.counstar.data.ReportReason
import app.counstar.data.YouTubeParser
import com.metrolist.innertube.WebApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Report a video: YouTube's own reasons, sent through YouTube's report form. The form's shape
 * changes often; if Counstar can't read it, it offers to report on YouTube instead.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReportDialog(videoId: String, params: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reasons by remember { mutableStateOf<List<ReportReason>?>(null) }
    var picked by remember { mutableStateOf<ReportReason?>(null) }
    var sending by remember { mutableStateOf(false) }
    LaunchedEffect(params) {
        reasons = if (params == null) emptyList() else withContext(Dispatchers.IO) {
            WebApi.reportForm(params).mapCatching(YouTubeParser::reportReasons).getOrDefault(emptyList())
        }
    }
    fun openOnYouTube() {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")))
        onDismiss()
    }
    val list = reasons
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report video") },
        text = {
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) }
                list.isEmpty() -> Text("Counstar couldn't load YouTube's report form for this video. You can report it on YouTube instead.")
                else -> Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    list.forEach { reason ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = picked == reason, onClick = { picked = reason }).padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(selected = picked == reason, onClick = null)
                            Text(reason.title)
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                list.isNullOrEmpty() && list != null -> TextButton(onClick = ::openOnYouTube) { Text("Open on YouTube") }
                list != null -> TextButton(
                    enabled = picked != null && !sending,
                    onClick = {
                        val reason = picked ?: return@TextButton
                        sending = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { WebApi.report(reason.params) }.isSuccess
                            Toast.makeText(context, if (ok) "Thanks for reporting" else "Couldn't send the report", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                ) { Text("Report") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
