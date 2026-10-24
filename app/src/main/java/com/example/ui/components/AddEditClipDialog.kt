package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ClipItem
import com.example.util.ContentTypeDetector

@Composable
fun AddEditClipDialog(
    initialClip: ClipItem? = null,
    onDismiss: () -> Unit,
    onSave: (content: String, label: String?) -> Unit
) {
    var content by remember { mutableStateOf(initialClip?.content ?: "") }
    var label by remember { mutableStateOf(initialClip?.label ?: "") }
    var isError by remember { mutableStateOf(false) }

    val detectedType by remember {
        derivedStateOf {
            if (content.isBlank()) null else ContentTypeDetector.detect(content)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialClip == null) "New Clip" else "Edit Clip",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                // Content text area
                OutlinedTextField(
                    value = content,
                    onValueChange = {
                        content = it
                        if (isError && it.isNotBlank()) isError = false
                    },
                    label = { Text("Clipboard Content *") },
                    placeholder = { Text("Paste or type text, link, code...") },
                    minLines = 4,
                    maxLines = 8,
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text("Content cannot be empty", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("${content.length} characters • ${if (content.isBlank()) 0 else content.trim().split(Regex("\\s+")).size} words")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clip_content_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Optional label field
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label / Tag (Optional)") },
                    placeholder = { Text("e.g. WiFi Password, Docs Link") },
                    leadingIcon = {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clip_label_input")
                )

                // Detected Type Preview Pill
                if (detectedType != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Auto-detected type: ${detectedType?.type?.name}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isBlank()) {
                        isError = true
                    } else {
                        onSave(content.trim(), label.trim().ifEmpty { null })
                    }
                },
                modifier = Modifier.testTag("save_clip_dialog_button")
            ) {
                Text(if (initialClip == null) "Add to History" else "Update Clip")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_clip_dialog_button")
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
