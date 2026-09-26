package com.meshconnect.pro.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meshconnect.pro.model.HighSpeedFileTransfer
import com.meshconnect.pro.model.TransferStatus
import com.meshconnect.pro.ui.theme.StatusHighSpeed

@Composable
fun HighSpeedTransferDialog(
    transfer: HighSpeedFileTransfer,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Dialog(onDismissRequest = {
        if (transfer.status == TransferStatus.COMPLETED || transfer.status == TransferStatus.FAILED) {
            onDismiss()
        }
    }) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // آیکن پرسرعت با انیمیشن نبض
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .scale(if (transfer.status == TransferStatus.TRANSFERRING) pulseScale else 1f)
                        .background(StatusHighSpeed.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "High Speed Transfer",
                        tint = StatusHighSpeed,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (transfer.isOutgoing) "ارسال مستقیم فوق سریع" else "دریافت مستقیم فوق سریع",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = transfer.fileName,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                val progress = if (transfer.fileSize > 0) {
                    (transfer.bytesTransferred.toFloat() / transfer.fileSize.toFloat()).coerceIn(0f, 1f)
                } else 0f

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = StatusHighSpeed,
                    trackColor = StatusHighSpeed.copy(alpha = 0.2f),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val transferredMb = transfer.bytesTransferred / (1024f * 1024f)
                    val totalMb = transfer.fileSize / (1024f * 1024f)
                    val speedMb = transfer.speedBytesPerSec / (1024f * 1024f)

                    Text(
                        text = "%.1f / %.1f MB".format(transferredMb, totalMb),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%.1f MB/s".format(speedMb),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusHighSpeed
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                when (transfer.status) {
                    TransferStatus.COMPLETED -> {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تکمیل شد ✔ بستن", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    TransferStatus.FAILED -> {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("خطا در انتقال! بستن", color = Color.White)
                        }
                    }
                    else -> {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("لغو انتقال")
                        }
                    }
                }
            }
        }
    }
}
