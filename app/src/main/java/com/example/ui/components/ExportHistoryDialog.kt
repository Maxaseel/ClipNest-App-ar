package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ClipItem
import com.example.util.DateFormatter

@Composable
fun ExportHistoryDialog(
    clips: List<ClipItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val plainTextExport = buildPlainTextExport(clips)
    val jsonExport = buildJsonExport(clips)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Export Clipboard History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Export ${clips.size} saved clipboard items to another app, email, or backup note.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Plain text preview card
                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Formatted Text (${clips.size} items)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = plainTextExport.take(280) + if (plainTextExport.length > 280) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                FilledTonalButton(
                    onClick = {
                        shareExport(context, plainTextExport, "ClipNest_History.txt")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_share_text_button")
                ) {
                    Icon(Icons.Default.TextFields, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Share as Formatted Text")
                }

                Spacer(modifier = Modifier.height(8.dp))

                FilledTonalButton(
                    onClick = {
                        shareExport(context, jsonExport, "ClipNest_History.json")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_share_json_button")
                ) {
                    Icon(Icons.Default.DataObject, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Share as JSON Backup")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_export_dialog")
            ) {
                Text("Done")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

private fun buildPlainTextExport(clips: List<ClipItem>): String {
    val sb = StringBuilder()
    sb.appendLine("=== ClipNest History Export (${clips.size} items) ===")
    sb.appendLine("Generated: ${DateFormatter.formatFullDateTime(System.currentTimeMillis())}\n")

    clips.forEachIndexed { index, clip ->
        val star = if (clip.isPinned) " [★ STARRED]" else ""
        val label = if (!clip.label.isNullOrBlank()) " (${clip.label})" else ""
        sb.appendLine("[#${index + 1}] [${clip.contentType}]$star$label - ${DateFormatter.formatFullDateTime(clip.lastUsedAt)}")
        sb.appendLine(clip.content)
        sb.appendLine("-".repeat(40))
    }
    return sb.toString()
}

private fun buildJsonExport(clips: List<ClipItem>): String {
    val sb = StringBuilder()
    sb.appendLine("[\n")
    clips.forEachIndexed { i, clip ->
        val escaped = clip.content
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        sb.append("  {\n")
        sb.append("    \"id\": ${clip.id},\n")
        sb.append("    \"content\": \"$escaped\",\n")
        sb.append("    \"contentType\": \"${clip.contentType}\",\n")
        sb.append("    \"isPinned\": ${clip.isPinned},\n")
        sb.append("    \"copyCount\": ${clip.copyCount},\n")
        sb.append("    \"label\": ${if (clip.label == null) "null" else "\"${clip.label}\""},\n")
        sb.append("    \"createdAt\": ${clip.createdAt},\n")
        sb.append("    \"lastUsedAt\": ${clip.lastUsedAt}\n")
        sb.append("  }${if (i < clips.size - 1) "," else ""}\n")
    }
    sb.append("]")
    return sb.toString()
}

private fun shareExport(context: Context, text: String, title: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, "Export ClipNest history"))
}
