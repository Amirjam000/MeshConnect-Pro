package com.meshconnect.pro.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meshconnect.pro.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageOptionsDialog(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onDelete: (forEveryone: Boolean) -> Unit,
    onReaction: (String) -> Unit
) {
    val context = LocalContext.current
    var showInfoDialog by remember { mutableStateOf(false) }

    val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")

    if (showInfoDialog) {
        MessageInfoDialog(message = message, onDismiss = { showInfoDialog = false; onDismiss() })
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // نوار واکنش سریع با اموجی‌ها
                Text("واکنش سریع (Reactions):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    items(emojis) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .clickable {
                                    onReaction(emoji)
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 22.sp)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // گزینه‌های منو
                OptionRow(icon = Icons.Default.Reply, title = "پاسخ دادن (Reply)") {
                    onReply()
                    onDismiss()
                }

                OptionRow(icon = Icons.Default.ContentCopy, title = "کپی متن (Copy)") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ChatMessage", message.text))
                    Toast.makeText(context, "متن کپی شد ✔", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }

                if (message.isFromMe) {
                    OptionRow(icon = Icons.Default.Edit, title = "ویرایش (Edit)") {
                        onEdit()
                        onDismiss()
                    }
                }

                OptionRow(
                    icon = if (message.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                    title = if (message.isPinned) "برداشتن پین (Unpin)" else "پین کردن در بالا (Pin)"
                ) {
                    onPin()
                    onDismiss()
                }

                OptionRow(icon = Icons.Default.Info, title = "اطلاعات پیام و سین‌ها (Message Info)") {
                    showInfoDialog = true
                }

                OptionRow(icon = Icons.Default.Delete, title = "حذف برای من (Delete for Me)", isDestructive = true) {
                    onDelete(false)
                    onDismiss()
                }

                if (message.isFromMe) {
                    OptionRow(icon = Icons.Default.DeleteForever, title = "حذف برای همه (Delete for Everyone)", isDestructive = true) {
                        onDelete(true)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    title: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun MessageInfoDialog(message: ChatMessage, onDismiss: () -> Unit) {
    val timeFormat = SimpleDateFormat("HH:mm:ss - yyyy/MM/dd", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("اطلاعات پیام (Message Info)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(12.dp))

                Text("فرستنده: ${message.senderName}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("زمان ارسال: ${timeFormat.format(Date(message.timestamp))}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("وضعیت تحویل: ${message.status}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)

                Spacer(modifier = Modifier.height(14.dp))
                Text("دیده‌شده توسط (Seen By):", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)

                if (message.seenBy.isEmpty()) {
                    Text("هنوز کسی این پیام را مشاهده نکرده است.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    message.seenBy.forEach { reader ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("👤 ${reader.userName}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text(timeFormat.format(Date(reader.timestamp)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("بستن", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
