package com.meshconnect.pro.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshconnect.pro.model.ChatMessage
import com.meshconnect.pro.model.MessageStatus
import com.meshconnect.pro.ui.theme.SeenAccent
import com.meshconnect.pro.ui.theme.StatusHighSpeed
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    onLongClick: () -> Unit,
    onVisible: () -> Unit,
    onReactionClick: (String) -> Unit
) {
    LaunchedEffect(message.id) {
        onVisible()
    }

    val isSelf = message.isFromMe
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = timeFormat.format(Date(message.timestamp))

    val bubbleShape = if (isSelf) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    val bubbleColor = if (isSelf) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isSelf) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { it / 2 }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start
        ) {
            // نام فرستنده در چت عمومی برای پیام‌های دیگران
            if (!isSelf) {
                Text(
                    text = message.senderName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .widthIn(min = 60.dp, max = 300.dp)
                    .clip(bubbleShape)
                    .background(bubbleColor)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongClick
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    // نمایش پیام نقل‌قول شده (Reply Preview)
                    if (message.replyTo != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelf) Color.Black.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text(
                                    text = message.replyTo.senderName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelf) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = message.replyTo.text,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // کارت ضمیمه فایل / انتقال فوق سریع
                    if (message.fileAttachment != null) {
                        val file = message.fileAttachment
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (file.isHighSpeedTransfer) StatusHighSpeed.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (file.isHighSpeedTransfer) Icons.Default.Bolt else Icons.Default.AttachFile,
                                    contentDescription = null,
                                    tint = if (file.isHighSpeedTransfer) StatusHighSpeed else textColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.fileName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textColor,
                                        maxLines = 1
                                    )
                                    val sizeMb = file.fileSize / (1024f * 1024f)
                                    Text(
                                        text = "%.1f MB ${if (file.isHighSpeedTransfer) "(اتصال مستقیم فوق سریع)" else ""}".format(sizeMb),
                                        fontSize = 10.sp,
                                        color = textColor.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    // متن پیام
                    Text(
                        text = message.text,
                        fontSize = 14.sp,
                        color = textColor,
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // فوتر حباب: زمان، برچسب ویرایش، تیک‌ها و وضعیت سین
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (message.isEdited) {
                            Text(
                                text = "ویرایش شده",
                                fontSize = 9.sp,
                                color = textColor.copy(alpha = 0.6f)
                            )
                        }

                        Text(
                            text = timeStr,
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )

                        // تیک وضعیت تحویل و سین برای پیام‌های ارسالی من
                        if (isSelf) {
                            when (message.status) {
                                MessageStatus.PENDING -> {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "Pending",
                                        tint = textColor.copy(alpha = 0.6f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                MessageStatus.SENT -> {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Sent",
                                        tint = textColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                MessageStatus.DELIVERED -> {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = "Delivered",
                                        tint = textColor.copy(alpha = 0.8f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                MessageStatus.SEEN -> {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = "Seen",
                                        tint = SeenAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    // نشان دادن تعداد افرادی که دیده‌اند
                                    if (message.seenBy.isNotEmpty()) {
                                        Text(
                                            text = "${message.seenBy.size}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SeenAccent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // نمایش نشانک‌های واکنش (Reaction Pills) زیر حباب
            if (message.reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.reactions.forEach { (emoji, users) ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 1.dp,
                            modifier = Modifier.clickable { onReactionClick(emoji) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = emoji, fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = users.size.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
