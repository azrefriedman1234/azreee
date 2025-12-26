package com.example.tglive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.io.File
import android.graphics.BitmapFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val td = TdClient(filesDir, BuildConfig.TG_API_ID, BuildConfig.TG_API_HASH)

        setContent {
            MaterialTheme {
                App(td = td, filesDir = filesDir)
            }
        }
        td.start()
    }
}

@Composable
private fun App(td: TdClient, filesDir: File) {
    val scope = rememberCoroutineScope()

    var auth by remember { mutableStateOf<TdApi.AuthorizationState?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }

    // target channel
    var targetChannelInput by remember { mutableStateOf("@") }
    var targetChannelId by remember { mutableStateOf<Long?>(null) }
    var targetStatus by remember { mutableStateOf("") }

    // feed
    val feed = remember { mutableStateListOf<UiMessage>() }
    var selected by remember { mutableStateOf<UiMessage?>(null) }

    // file cache: fileId -> local path
    val filePathById = remember { mutableStateMapOf<Int, String>() }

    DisposableEffect(Unit) {
        td.onAuthState = { s -> auth = s }
        td.onError = { e -> lastError = e }
        td.onNewMessage = { m ->
            val raw = (m.content as? TdApi.MessageText)?.text?.text ?: ""
            val hasMedia = m.content is TdApi.MessagePhoto || m.content is TdApi.MessageVideo

            scope.launch(Dispatchers.IO) {
                val he = Translate.toHebrewIfNeeded(raw)
                val ui = UiMessage(
                    chatId = m.chatId,
                    messageId = m.id,
                    date = m.date.toLong(),
                    rawText = raw,
                    textHe = he,
                    hasMedia = hasMedia,
                    content = m.content
                )
                launch(Dispatchers.Main) {
                    feed.add(0, ui)
                    if (feed.size > 250) feed.removeLast()
                }
            }
        }
        td.onFileReady = { f ->
            val p = f.local?.path
            if (!p.isNullOrBlank()) {
                filePathById[f.id] = p
            }
        }
        onDispose { }
    }

    val a = auth
    when (a) {
        is TdApi.AuthorizationStateWaitPhoneNumber -> LoginPhone(onSend = td::setPhone, lastError = lastError)
        is TdApi.AuthorizationStateWaitCode -> LoginCode(onSend = td::setCode, lastError = lastError)
        is TdApi.AuthorizationStateWaitPassword -> LoginPassword(onSend = td::setPassword, lastError = lastError)
        is TdApi.AuthorizationStateReady -> {
            Scaffold(
                topBar = {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Text("Telegram Live", style = MaterialTheme.typography.titleLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = targetChannelInput,
                                onValueChange = { targetChannelInput = it },
                                label = { Text("ערוץ יעד לשליחה (ציבורי) @...") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                targetStatus = "מחפש ערוץ..."
                                td.searchPublicChat(targetChannelInput) { id ->
                                    targetChannelId = id
                                    targetStatus = if (id != null) "נשמר (chatId=$id)" else "לא נמצא"
                                }
                            }) { Text("שמור") }
                        }
                        if (targetStatus.isNotBlank()) Text(targetStatus)
                        if (lastError != null) Text("שגיאה: $lastError", color = Color.Red)
                    }
                }
            ) { p ->
                Row(Modifier.padding(p).fillMaxSize()) {
                    // Feed
                    LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
                        items(feed) { m ->
                            ListItem(
                                headlineContent = { Text(m.textHe.ifBlank { "(אין טקסט)" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text("chat ${m.chatId} · msg ${m.messageId} · media ${if (m.hasMedia) "כן" else "לא"}") },
                                modifier = Modifier
                                    .clickable { selected = m }
                                    .padding(vertical = 2.dp)
                            )
                            Divider()
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    // Details
                    Box(Modifier.weight(1f).fillMaxHeight().padding(10.dp)) {
                        val s = selected
                        if (s == null) {
                            Text("בחר הודעה מהרשימה")
                        } else {
                            MessageDetails(
                                td = td,
                                filesDir = filesDir,
                                msg = s,
                                targetChannelId = targetChannelId,
                                filePathById = filePathById
                            )
                        }
                    }
                }
            }
        }
        else -> {
            Scaffold { p ->
                Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("מתחבר... ${(a?.javaClass?.simpleName ?: "")}")
                }
            }
        }
    }
}

@Composable
private fun LoginPhone(onSend: (String) -> Unit, lastError: String?) {
    var phone by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text("התחברות - טלפון") }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(phone, onValueChange = { phone = it }, label = { Text("+972...") })
            Button(onClick = { onSend(phone.trim()) }) { Text("שלח קוד") }
            if (lastError != null) Text("שגיאה: $lastError", color = Color.Red)
        }
    }
}

@Composable
private fun LoginCode(onSend: (String) -> Unit, lastError: String?) {
    var code by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text("התחברות - קוד") }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(code, onValueChange = { code = it }, label = { Text("קוד SMS") })
            Button(onClick = { onSend(code.trim()) }) { Text("אמת") }
            if (lastError != null) Text("שגיאה: $lastError", color = Color.Red)
        }
    }
}

@Composable
private fun LoginPassword(onSend: (String) -> Unit, lastError: String?) {
    var pass by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text("התחברות - 2FA") }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(pass, onValueChange = { pass = it }, label = { Text("סיסמה") })
            Button(onClick = { onSend(pass) }) { Text("התחבר") }
            if (lastError != null) Text("שגיאה: $lastError", color = Color.Red)
        }
    }
}

@Composable
private fun MessageDetails(
    td: TdClient,
    filesDir: File,
    msg: UiMessage,
    targetChannelId: Long?,
    filePathById: Map<Int, String>
) {
    val scope = rememberCoroutineScope()

    var rects by remember { mutableStateOf<List<NormRect>>(emptyList()) }
    var status by remember { mutableStateOf("") }

    // local paths
    var thumbPath by remember { mutableStateOf<String?>(null) }
    var mediaPath by remember { mutableStateOf<String?>(null) }
    var processedPath by remember { mutableStateOf<String?>(null) }

    val thumbFileId = remember(msg) {
        when (val c = msg.content) {
            is TdApi.MessagePhoto -> c.photo.sizes.lastOrNull()?.photo?.id
            is TdApi.MessageVideo -> c.video.thumbnail?.file?.id
            else -> null
        }
    }

    val mediaFileId = remember(msg) {
        when (val c = msg.content) {
            is TdApi.MessagePhoto -> c.photo.sizes.lastOrNull()?.photo?.id
            is TdApi.MessageVideo -> c.video.video?.id
            else -> null
        }
    }

    // update paths from file cache
    LaunchedEffect(filePathById, thumbFileId, mediaFileId) {
        if (thumbFileId != null) {
            val p = filePathById[thumbFileId]
            if (!p.isNullOrBlank()) thumbPath = p
        }
        if (mediaFileId != null) {
            val p = filePathById[mediaFileId]
            if (!p.isNullOrBlank()) mediaPath = p
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("טקסט:", style = MaterialTheme.typography.titleMedium)
        Text(msg.textHe.ifBlank { msg.rawText })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = thumbFileId != null, onClick = {
                status = "מוריד thumbnail..."
                td.downloadFile(thumbFileId!!, priority = 64)
            }) { Text("Thumbnail") }

            Button(enabled = mediaFileId != null, onClick = {
                status = "מוריד מדיה..."
                td.downloadFile(mediaFileId!!, priority = 64)
            }) { Text("מדיה") }
        }

        // Preview + rectangle picker
        val bmp = remember(thumbPath) {
            thumbPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
        }

        if (bmp != null) {
            RectPickerOverlay(
                onRectsChanged = { rects = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                Image(bmp.asImageBitmap(), contentDescription = "thumb", modifier = Modifier.fillMaxSize())
            }
            Text("מלבנים: ${rects.size}")
        } else {
            Text("אין thumbnail עדיין (לחץ 'Thumbnail')")
        }

        val isVideo = remember(msg) { msg.content is TdApi.MessageVideo }
        val isPhoto = remember(msg) { msg.content is TdApi.MessagePhoto }

        Button(
            enabled = mediaPath != null && rects.isNotEmpty() && (isVideo || isPhoto),
            onClick = {
                scope.launch(Dispatchers.IO) {
                    status = "מעבד טשטוש..."
                    val inp = mediaPath!!

                    val dim = MediaInfo.getDimensions(inp)
                    if (dim == null) {
                        status = "לא הצלחתי לקרוא רזולוציה (נסה שוב)"
                        return@launch
                    }

                    val out = if (isPhoto) {
                        File(filesDir, "blur_${System.currentTimeMillis()}.jpg").absolutePath
                    } else {
                        File(filesDir, "blur_${System.currentTimeMillis()}.mp4").absolutePath
                    }

                    val ok = if (isPhoto) {
                        BlurEngine.blurImage(inp, out, dim.w, dim.h, rects)
                    } else {
                        BlurEngine.blurVideo(inp, out, dim.w, dim.h, rects)
                    }

                    status = if (ok) "מוכן: $out" else "כשל עיבוד"
                    if (ok) processedPath = out
                }
            }
        ) { Text("החל טשטוש על המדיה") }

        Button(
            enabled = processedPath != null && targetChannelId != null,
            onClick = {
                td.sendProcessedToChannel(targetChannelId!!, processedPath!!, caption = "")
                status = "נשלח לערוץ ✅"
            }
        ) { Text("שלח לערוץ") }

        if (status.isNotBlank()) Text(status)
    }
}

@Composable
private fun RectPickerOverlay(
    onRectsChanged: (List<NormRect>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var start by remember { mutableStateOf<Offset?>(null) }
    var current by remember { mutableStateOf<Rect?>(null) }
    var rects by remember { mutableStateOf<List<Rect>>(emptyList()) }

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { pos ->
                    start = pos
                    current = Rect(pos, pos)
                },
                onDrag = { change, _ ->
                    val s = start ?: return@detectDragGestures
                    current = Rect(s, change.position)
                },
                onDragEnd = {
                    val c = current
                    if (c != null && c.width > 12f && c.height > 12f) {
                        rects = rects + c
                        // convert to normalized based on box size
                        val norm = rects.map {
                            NormRect(
                                x = (it.left / size.width).coerceIn(0f, 1f),
                                y = (it.top / size.height).coerceIn(0f, 1f),
                                w = (it.width / size.width).coerceIn(0f, 1f),
                                h = (it.height / size.height).coerceIn(0f, 1f)
                            )
                        }
                        onRectsChanged(norm)
                    }
                    start = null
                    current = null
                }
            )
        }
    ) {
        content()
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 3f, cap = StrokeCap.Round)
            for (r in rects) {
                drawRect(
                    color = Color.Red,
                    topLeft = Offset(r.left, r.top),
                    size = androidx.compose.ui.geometry.Size(r.width, r.height),
                    style = stroke
                )
            }
            val c = current
            if (c != null) {
                drawRect(
                    color = Color.Yellow,
                    topLeft = Offset(c.left, c.top),
                    size = androidx.compose.ui.geometry.Size(c.width, c.height),
                    style = stroke
                )
            }
        }
    }
}
