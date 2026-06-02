package com.accu.ui.filemanager

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.VideoView
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalFileViewerScreen(
    filePath: String,
    onBack: () -> Unit,
) {
    val file = remember(filePath) { File(filePath) }
    val ext = remember(filePath) { file.extension.lowercase() }
    val fileType = remember(ext) { detectFileType(ext) }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            file.name,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${fileType.label} · ${formatBytes(file.length())}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = {},
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(fileType.icon, null, Modifier.size(18.dp))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (fileType) {
                FileViewType.IMAGE  -> ImageViewer(file)
                FileViewType.VIDEO  -> VideoViewer(file)
                FileViewType.AUDIO  -> AudioViewer(file)
                FileViewType.PDF    -> PdfViewer(file)
                FileViewType.TEXT   -> TextViewer(file)
                FileViewType.CODE   -> CodeViewer(file)
                FileViewType.HTML   -> HtmlViewer(file)
                FileViewType.ZIP    -> ZipViewer(file)
                FileViewType.APK    -> ApkViewer(file)
                FileViewType.BINARY -> BinaryViewer(file)
            }
        }
    }
}

// ── File Type Detection ───────────────────────────────────────────────────────

enum class FileViewType(val label: String, val icon: ImageVector) {
    IMAGE("Image",    Icons.Default.Image),
    VIDEO("Video",    Icons.Default.VideoFile),
    AUDIO("Audio",    Icons.Default.AudioFile),
    PDF("PDF",        Icons.Default.PictureAsPdf),
    TEXT("Text",      Icons.Default.TextSnippet),
    CODE("Code",      Icons.Default.Code),
    HTML("HTML",      Icons.Default.Html),
    ZIP("Archive",    Icons.Default.FolderZip),
    APK("APK",        Icons.Default.Android),
    BINARY("Binary",  Icons.Default.InsertDriveFile),
}

private fun detectFileType(ext: String): FileViewType = when (ext) {
    "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "ico", "tiff", "tif", "svg" -> FileViewType.IMAGE
    "mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "m4v", "ts", "wmv" -> FileViewType.VIDEO
    "mp3", "wav", "ogg", "flac", "aac", "m4a", "opus", "wma", "amr" -> FileViewType.AUDIO
    "pdf" -> FileViewType.PDF
    "txt", "md", "log", "csv", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties", "xml" -> FileViewType.TEXT
    "kt", "java", "py", "js", "ts", "jsx", "tsx", "c", "cpp", "h", "cs", "go", "rs", "swift",
    "dart", "rb", "php", "sh", "bash", "zsh", "fish", "sql", "json", "gradle", "kts" -> FileViewType.CODE
    "html", "htm", "xhtml" -> FileViewType.HTML
    "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "jar", "apks", "xapk" -> FileViewType.ZIP
    "apk" -> FileViewType.APK
    else -> FileViewType.BINARY
}

// ── Image Viewer ──────────────────────────────────────────────────────────────

@Composable
private fun ImageViewer(file: File) {
    val bitmap = remember(file.path) {
        runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }
    if (bitmap == null) {
        ErrorState("Cannot decode image", Icons.Default.BrokenImage)
        return
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var showInfo by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallFloatingActionButton(
                onClick = { showInfo = !showInfo },
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.8f),
            ) { Icon(Icons.Default.Info, null, Modifier.size(20.dp)) }
        }
        if (showInfo) {
            Surface(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(0.9f),
            ) {
                Column(Modifier.padding(16.dp)) {
                    InfoRow("Dimensions", "${bitmap.width} × ${bitmap.height} px")
                    InfoRow("File size",  formatBytes(file.length()))
                    InfoRow("Modified",   formatDate(file.lastModified()))
                }
            }
        }
    }
}

// ── Video Viewer ──────────────────────────────────────────────────────────────

@Composable
private fun VideoViewer(file: File) {
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayerReady by remember { mutableStateOf(false) }
    val videoViewRef = remember { mutableStateOf<VideoView?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoPath(file.absolutePath)
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        mediaPlayerReady = true
                    }
                    videoViewRef.value = this
                }
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        if (!mediaPlayerReady) {
            CircularProgressIndicator(color = Color.White)
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(0.5f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(
                onClick = {
                    val vv = videoViewRef.value ?: return@IconButton
                    if (isPlaying) { vv.pause(); isPlaying = false }
                    else { vv.start(); isPlaying = true }
                },
                enabled = mediaPlayerReady,
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null,
                    Modifier.size(48.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

// ── Audio Viewer ──────────────────────────────────────────────────────────────

@Composable
private fun AudioViewer(file: File) {
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableIntStateOf(0) }
    var prepared by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }
    val scope = rememberCoroutineScope()

    DisposableEffect(file.path) {
        try {
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.prepare()
            duration = mediaPlayer.duration
            prepared = true
        } catch (_: Exception) {}
        onDispose {
            mediaPlayer.stop()
            mediaPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = mediaPlayer.currentPosition.toFloat()
            kotlinx.coroutines.delay(500)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            Alignment.Center,
        ) {
            Icon(Icons.Default.MusicNote, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(24.dp))
        Text(file.nameWithoutExtension, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(file.extension.uppercase(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        Slider(
            value = if (duration > 0) position / duration else 0f,
            onValueChange = { ratio ->
                position = ratio * duration
                mediaPlayer.seekTo(position.toInt())
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = prepared,
        )
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(formatDuration(position.toInt()), style = MaterialTheme.typography.labelSmall)
            Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { mediaPlayer.seekTo(0); position = 0f }) {
                Icon(Icons.Default.SkipPrevious, null, Modifier.size(28.dp))
            }
            FloatingActionButton(
                onClick = {
                    if (!prepared) return@FloatingActionButton
                    if (isPlaying) { mediaPlayer.pause(); isPlaying = false }
                    else { mediaPlayer.start(); isPlaying = true }
                },
            ) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(28.dp))
            }
            IconButton(onClick = {
                mediaPlayer.seekTo(duration)
                mediaPlayer.stop()
                isPlaying = false
                position = 0f
            }) {
                Icon(Icons.Default.SkipNext, null, Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        InfoRow("Size",     formatBytes(file.length()))
        InfoRow("Modified", formatDate(file.lastModified()))
    }
}

// ── PDF Viewer ────────────────────────────────────────────────────────────────

@Composable
private fun PdfViewer(file: File) {
    val context = LocalContext.current
    var totalPages by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val renderer = remember(file.path) {
        runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd)
        }.getOrNull()
    }

    LaunchedEffect(renderer) {
        if (renderer != null) {
            totalPages = renderer.pageCount
            renderPage(renderer, 0) { bmp -> bitmap = bmp }
        } else {
            error = "Cannot render PDF"
        }
    }

    LaunchedEffect(currentPage) {
        renderer?.let { renderPage(it, currentPage) { bmp -> bitmap = bmp } }
    }

    DisposableEffect(Unit) {
        onDispose { renderer?.close() }
    }

    if (error != null) {
        ErrorState(error!!, Icons.Default.PictureAsPdf)
        return
    }

    Column(Modifier.fillMaxSize()) {
        bitmap?.let { bmp ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFFE8E8E8)),
                Alignment.Center,
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${currentPage + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        } ?: Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
            CircularProgressIndicator()
        }

        if (totalPages > 1) {
            Surface(tonalElevation = 4.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0,
                    ) { Icon(Icons.Default.ChevronLeft, null) }

                    Text(
                        "Page ${currentPage + 1} / $totalPages",
                        Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    IconButton(
                        onClick = { if (currentPage < totalPages - 1) currentPage++ },
                        enabled = currentPage < totalPages - 1,
                    ) { Icon(Icons.Default.ChevronRight, null) }
                }
            }
        }
    }
}

private fun renderPage(renderer: PdfRenderer, pageIndex: Int, onDone: (android.graphics.Bitmap) -> Unit) {
    if (pageIndex < 0 || pageIndex >= renderer.pageCount) return
    val page = renderer.openPage(pageIndex)
    val scale = 2
    val bmp = android.graphics.Bitmap.createBitmap(page.width * scale, page.height * scale, android.graphics.Bitmap.Config.ARGB_8888)
    bmp.eraseColor(android.graphics.Color.WHITE)
    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    page.close()
    onDone(bmp)
}

// ── Text Viewer ───────────────────────────────────────────────────────────────

@Composable
private fun TextViewer(file: File) {
    var content by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(file.path) {
        scope.launch(Dispatchers.IO) {
            content = runCatching {
                if (file.length() > 2_000_000L) file.inputStream().bufferedReader().use { it.readText(2_000_000) }
                else file.readText()
            }.getOrElse { "Error reading file: ${it.message}" }
            isLoading = false
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "${content.lines().size} lines · ${content.length} chars",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = content,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SelectionContainer(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer { content() }
}

// ── Code Viewer ───────────────────────────────────────────────────────────────

@Composable
private fun CodeViewer(file: File) {
    var content by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(file.path) {
        scope.launch(Dispatchers.IO) {
            content = runCatching {
                if (file.length() > 1_000_000L) file.inputStream().bufferedReader().use { it.readText(1_000_000) }
                else file.readText()
            }.getOrElse { "Error: ${it.message}" }
            isLoading = false
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Icon(Icons.Default.Code, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${file.extension.uppercase()} · ${content.lines().size} lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E2E))
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            val lines = content.lines()
            Row {
                Column(horizontalAlignment = Alignment.End) {
                    lines.forEachIndexed { i, _ ->
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF6272A4),
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                }
                Column {
                    lines.forEach { line ->
                        Text(
                            text = line.ifEmpty { " " },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFF8F8F2),
                        )
                    }
                }
            }
        }
    }
}

// ── HTML Viewer ───────────────────────────────────────────────────────────────

@Composable
private fun HtmlViewer(file: File) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = false
                loadUrl("file://${file.absolutePath}")
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

// ── ZIP Viewer ────────────────────────────────────────────────────────────────

@Composable
private fun ZipViewer(file: File) {
    var entries by remember { mutableStateOf<List<ZipEntryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var totalSize by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(file.path) {
        scope.launch(Dispatchers.IO) {
            try {
                val zip = ZipFile(file)
                val items = zip.entries().asSequence().map { e ->
                    ZipEntryItem(
                        name = e.name,
                        isDirectory = e.isDirectory,
                        size = e.size,
                        compressedSize = e.compressedSize,
                        crc = e.crc,
                    )
                }.sortedWith(compareBy({ !it.isDirectory }, { it.name })).toList()
                totalSize = items.sumOf { it.size.coerceAtLeast(0L) }
                entries = items
                zip.close()
            } catch (e: Exception) {
                error = e.message ?: "Cannot read archive"
            }
            isLoading = false
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (error != null) {
        ErrorState(error!!, Icons.Default.FolderZip)
        return
    }

    Column(Modifier.fillMaxSize()) {
        Surface(
            Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.FolderZip, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "${entries.size} entries · ${formatBytes(totalSize)} uncompressed",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Compressed: ${formatBytes(file.length())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { it.name }) { entry ->
                ListItem(
                    headlineContent = {
                        Text(
                            entry.name.substringAfterLast("/").ifEmpty { entry.name.substringBeforeLast("/").substringAfterLast("/") + "/" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    supportingContent = {
                        if (!entry.isDirectory) Text(
                            buildString {
                                append(formatBytes(entry.size))
                                if (entry.compressedSize > 0 && entry.size > 0) {
                                    val ratio = (1f - entry.compressedSize.toFloat() / entry.size) * 100
                                    if (ratio > 0) append(" · ${ratio.toInt()}% saved")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (entry.isDirectory) Icons.Default.Folder else iconForZipEntry(entry.name),
                            null,
                            tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (entry.name.contains("/") && !entry.isDirectory) {
                    Text(
                        entry.name.substringBeforeLast("/") + "/",
                        Modifier.padding(start = 72.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                    )
                }
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))
            }
        }
    }
}

private data class ZipEntryItem(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
    val crc: Long,
)

private fun iconForZipEntry(name: String): ImageVector {
    val ext = name.substringAfterLast(".").lowercase()
    return when {
        ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> Icons.Default.Image
        ext in listOf("mp4", "mkv", "avi", "mov", "webm") -> Icons.Default.VideoFile
        ext in listOf("mp3", "wav", "ogg", "flac", "aac") -> Icons.Default.AudioFile
        ext == "pdf" -> Icons.Default.PictureAsPdf
        ext in listOf("txt", "md", "log") -> Icons.Default.TextSnippet
        ext in listOf("kt", "java", "py", "js", "ts", "c", "cpp") -> Icons.Default.Code
        ext in listOf("zip", "rar", "7z", "tar", "gz") -> Icons.Default.FolderZip
        else -> Icons.Default.InsertDriveFile
    }
}

// ── APK Viewer ────────────────────────────────────────────────────────────────

@Composable
private fun ApkViewer(file: File) {
    val context = LocalContext.current
    val info = remember(file.path) {
        runCatching {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        }.getOrNull()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            Alignment.Center,
                        ) { Icon(Icons.Default.Android, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary) }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(info?.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: file.nameWithoutExtension, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(info?.packageName ?: "Unknown package", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Package Info", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    InfoRow("Package",       info?.packageName ?: "—")
                    InfoRow("Version Name",  info?.versionName ?: "—")
                    InfoRow("Version Code",  info?.longVersionCode?.toString() ?: "—")
                    InfoRow("Min SDK",       info?.applicationInfo?.minSdkVersion?.toString() ?: "—")
                    InfoRow("Target SDK",    info?.applicationInfo?.targetSdkVersion?.toString() ?: "—")
                    InfoRow("File size",     formatBytes(file.length()))
                    InfoRow("Modified",      formatDate(file.lastModified()))
                }
            }
        }
    }
}

// ── Binary Viewer ─────────────────────────────────────────────────────────────

@Composable
private fun BinaryViewer(file: File) {
    var hexContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(file.path) {
        scope.launch(Dispatchers.IO) {
            hexContent = runCatching {
                val bytes = file.inputStream().use { it.readBytes().take(4096).toByteArray() }
                buildString {
                    bytes.toList().chunked(16).forEachIndexed { lineIdx, lineBytes ->
                        append("%08X  ".format(lineIdx * 16))
                        lineBytes.forEach { b -> append("%02X ".format(b.toInt() and 0xFF)) }
                        repeat(16 - lineBytes.size) { append("   ") }
                        append(" |")
                        lineBytes.forEach { b -> append(if (b in 0x20..0x7E) b.toInt().toChar() else '.') }
                        append("|\n")
                    }
                    if (file.length() > 4096) append("\n… (showing first 4 KB of ${formatBytes(file.length())})")
                }
            }.getOrElse { "Cannot read binary: ${it.message}" }
            isLoading = false
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Surface(
            Modifier.fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                "Hex View · ${formatBytes(file.length())} total",
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(
                text = hexContent,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D1117))
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF58A6FF),
                lineHeight = 18.sp,
            )
        }
    }
}

// ── Shared Helpers ────────────────────────────────────────────────────────────

@Composable
private fun ErrorState(message: String, icon: ImageVector) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Text("Cannot open file", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun java.io.BufferedReader.readText(limit: Int): String {
    val sb = StringBuilder(minOf(limit, DEFAULT_BUFFER_SIZE))
    var count = 0
    val arr = CharArray(DEFAULT_BUFFER_SIZE)
    var n: Int
    while (this.read(arr).also { n = it } != -1) {
        if (count + n > limit) { sb.append(arr, 0, limit - count); break }
        sb.append(arr, 0, n)
        count += n
    }
    return sb.toString()
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0      -> "0 B"
    bytes < 1024    -> "$bytes B"
    bytes < 1_000_000 -> "${"%.1f".format(bytes / 1024f)} KB"
    bytes < 1_000_000_000 -> "${"%.2f".format(bytes / 1_000_000f)} MB"
    else            -> "${"%.2f".format(bytes / 1_000_000_000f)} GB"
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()).format(Date(ms))

private fun formatDuration(ms: Int): String {
    val total = ms / 1000
    val min = total / 60
    val sec = total % 60
    return "%d:%02d".format(min, sec)
}
