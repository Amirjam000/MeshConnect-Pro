package com.meshconnect.pro.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshconnect.pro.model.ChatMessage
import com.meshconnect.pro.ui.components.HighSpeedTransferDialog
import com.meshconnect.pro.ui.components.MessageBubble
import com.meshconnect.pro.ui.components.MessageOptionsDialog
import com.meshconnect.pro.ui.theme.StatusHighSpeed
import com.meshconnect.pro.ui.theme.StatusOnline
import com.meshconnect.pro.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicChatScreen(viewModel: MainViewModel) {
    val messages by viewModel.publicMessages.collectAsState()
    val pinnedMessage by viewModel.pinnedMessage.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val editingMessage by viewModel.editingMessage.collectAsState()
    val connectedPeers by viewModel.meshManager.connectedPeers.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val myName by viewModel.myName.collectAsState()
    val isTransmittingPtt by viewModel.audioStreamManager.isTransmitting.collectAsState()
    val pttAmplitude by viewModel.audioStreamManager.amplitude.collectAsState()
    val activeTransfer by viewModel.highSpeedEngine.activeTransfer.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var selectedMessageForOptions by remember { mutableStateOf<ChatMessage?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // بارگذاری متن هنگام ویرایش پیام
    LaunchedEffect(editingMessage) {
        if (editingMessage != null) {
            inputText = editingMessage!!.text
        }
    }

    // پیمایش خودکار به آخرین پیام
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // لانچر انتخاب فایل
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendFileFromUri(it) }
    }

    // انیمیشن چرخش آیکن تم (خورشید/ماه)
    val themeIconRotation by animateFloatAsState(
        targetValue = if (isDarkMode) 360f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "themeRotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "چنل عمومی مش",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // نقطه نبض آنلاین
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(StatusOnline, CircleShape)
                            )
                        }
                        Text(
                            text = "${connectedPeers.size} دستگاه متصل (همگام‌سازی خودکار)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // دکمه تغییر نام
                    IconButton(onClick = { showNameDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // دکمه متحرک تعویض تم (روز / شب)
                    IconButton(onClick = { viewModel.toggleDarkMode() }) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Toggle Theme",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.rotate(themeIconRotation)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // نوار پیام پین‌شده (Pinned Message Bar)
            AnimatedVisibility(visible = pinnedMessage != null) {
                pinnedMessage?.let { pin ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val index = messages.indexOfFirst { it.id == pin.id }
                                if (index != -1) {
                                    coroutineScope.launch { listState.animateScrollToItem(index) }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "پیام پین‌شده: ${pin.senderName}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = pin.text,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { viewModel.unpinMessage() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Unpin",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // لیست پیام‌ها
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onLongClick = { selectedMessageForOptions = message },
                        onVisible = { viewModel.markMessageAsSeen(message.id) },
                        onReactionClick = { emoji -> viewModel.toggleReaction(message.id, emoji) }
                    )
                }
            }

            // نوار نشانگر پخش واکی-تاکی زنده (Pulsing Radar Wave)
            AnimatedVisibility(visible = isTransmittingPtt) {
                val pulseScale by rememberInfiniteTransition(label = "pttWave").animateFloat(
                    initialValue = 1f,
                    targetValue = 1.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pttScale"
                )

                Surface(
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .scale(pulseScale)
                                .background(Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "مخابره زنده بی‌سیم... دکمه را رها کنید تا ارسال شود",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // نوار پیش‌نمایش پاسخ (Quoted Reply Preview)
            AnimatedVisibility(visible = replyingTo != null) {
                replyingTo?.let { reply ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Reply,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "پاسخ به ${reply.senderName}:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = reply.text,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.cancelReply() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Reply",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // نوار پیش‌نمایش ویرایش (Editing Bar)
            AnimatedVisibility(visible = editingMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "در حال ویرایش پیام...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            viewModel.cancelEditing()
                            inputText = ""
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Edit",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // نوار پایینی ورودی چت
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // دکمه ضمیمه فایل (>20MB انتقال مستقیم فوق سریع)
                    IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach File",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // ورودی متن پیام
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("پیام خود را بنویسید...", fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    if (inputText.trim().isNotEmpty()) {
                        // دکمه ارسال پیام متنی
                        IconButton(
                            onClick = {
                                viewModel.sendTextMessage(inputText)
                                inputText = ""
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        // دکمه تاکتیکال واکی-تاکی (Hold to Talk)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (isTransmittingPtt) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            viewModel.startWalkieTalkie()
                                            tryAwaitRelease()
                                            viewModel.stopWalkieTalkie()
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Walkie Talkie",
                                tint = if (isTransmittingPtt) Color.White else MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // دیالوگ گزینه‌های پیام (Copy, Edit, Reply, Pin, Delete, Info, Reactions)
        selectedMessageForOptions?.let { msg ->
            MessageOptionsDialog(
                message = msg,
                onDismiss = { selectedMessageForOptions = null },
                onReply = { viewModel.setReplyMessage(msg) },
                onEdit = { viewModel.startEditingMessage(msg) },
                onPin = { viewModel.pinMessage(msg) },
                onDelete = { forEveryone -> viewModel.deleteMessage(msg.id, forEveryone) },
                onReaction = { emoji -> viewModel.toggleReaction(msg.id, emoji) }
            )
        }

        // دیالوگ انتقال مستقیم فوق سریع (>20 MB)
        activeTransfer?.let { transfer ->
            HighSpeedTransferDialog(
                transfer = transfer,
                onCancel = { viewModel.highSpeedEngine.cancelTransfer() },
                onDismiss = { viewModel.highSpeedEngine.dismissTransfer() }
            )
        }

        // دیالوگ تغییر نام کاربری
        if (showNameDialog) {
            var tempName by remember { mutableStateOf(myName) }
            AlertDialog(
                onDismissRequest = { showNameDialog = false },
                title = { Text("نام نمایشی شما") },
                text = {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        singleLine = true,
                        label = { Text("نام شما در شبکه مش") }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.updateMyName(tempName)
                        showNameDialog = false
                    }) {
                        Text("ذخیره")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNameDialog = false }) {
                        Text("انصراف")
                    }
                }
            )
        }
    }
}
