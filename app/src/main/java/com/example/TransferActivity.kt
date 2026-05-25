package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

class TransferActivity : ComponentActivity() {
    private val rxFilesState = mutableStateListOf<ReceivedFile>()
    private var fileReceivedListener: ((File) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val peerName = intent.getStringExtra("device_name") ?: "PC Indéfini"
        val peerIp = intent.getStringExtra("device_ip") ?: "127.0.0.1"
        val peerPort = intent.getIntExtra("device_port", 8080)

        // Load files received so far
        refreshReceivedFilesList()

        // Set up reactive listener for files written by our background server
        fileReceivedListener = { _ ->
            lifecycleScope.launch(Dispatchers.Main) {
                refreshReceivedFilesList()
            }
        }
        ShareLinkApp.addFileListener(fileReceivedListener!!)

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    TransferScreen(
                        modifier = Modifier.padding(innerPadding),
                        peerName = peerName,
                        peerIp = peerIp,
                        peerPort = peerPort,
                        receivedFiles = rxFilesState,
                        onBack = { finish() },
                        onRefreshRx = { refreshReceivedFilesList() }
                    )
                }
            }
        }
    }

    private fun refreshReceivedFilesList() {
        val files = getReceivedFiles(this)
        rxFilesState.clear()
        rxFilesState.addAll(files)
    }

    override fun onDestroy() {
        super.onDestroy()
        fileReceivedListener?.let {
            ShareLinkApp.removeFileListener(it)
        }
    }
}

// Utility to list files inside /storage/emulated/0/Android/data/com.sharelink.app/files/ShareLink/
fun getReceivedFiles(context: Context): List<ReceivedFile> {
    val dir = context.getExternalFilesDir("ShareLink") ?: return emptyList()
    val files = dir.listFiles() ?: return emptyList()
    return files.filter { it.isFile }.map {
        ReceivedFile(
            name = it.name,
            size = it.length(),
            timestamp = it.lastModified(),
            path = it.absolutePath
        )
    }.sortedByDescending { it.timestamp }
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun formatTimestamp(timestamp: Long): String {
    return DateFormat.format("dd/MM/yyyy HH:mm", Date(timestamp)).toString()
}

fun getSelectedFileDetails(context: Context, uri: Uri): SelectedFile? {
    var name = "Fichier inconnu"
    var size = 0L
    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (nameIndex != -1) name = it.getString(nameIndex)
                if (sizeIndex != -1) size = it.getLong(sizeIndex)
            }
        }
    } catch (e: Exception) {
        Log.e("TransferActivity", "Error resolution Uri details", e)
    }
    return SelectedFile(uri, name, size)
}

fun openReceivedFile(context: Context, filePath: String) {
    try {
        val file = File(filePath)
        if (!file.exists()) return
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        
        val mime = context.contentResolver.getType(uri) ?: "*/*"
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Impossible d'ouvrir le fichier : ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun openFolder(context: Context) {
    val dir = context.getExternalFilesDir("ShareLink") ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.fromFile(dir), "*/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Sauvegardé : Android/data/com.sharelink.app/files/ShareLink/",
            Toast.LENGTH_LONG
        ).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    modifier: Modifier = Modifier,
    peerName: String,
    peerIp: String,
    peerPort: Int,
    receivedFiles: List<ReceivedFile>,
    onBack: () -> Unit,
    onRefreshRx: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val selectedFiles = remember { mutableStateListOf<SelectedFile>() }

    // Upload core progress statuses
    var isSending by remember { mutableStateOf(false) }
    var currentFileIndex by remember { mutableStateOf(0) }
    var currentSendingFile by remember { mutableStateOf<SelectedFile?>(null) }
    var uploadErrorMsg by remember { mutableStateOf<String?>(null) }

    // File picker launcher
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val list = uris.mapNotNull { getSelectedFileDetails(context, it) }
            selectedFiles.addAll(list)
        }
    }

    // Connect status point animator (pulse green)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // High polish Top Bar showing green connected message
        TopAppBar(
            title = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                        Text(
                            text = "Connecté à : $peerName",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        )
                    }
                    Text(
                        text = "$peerIp:$peerPort",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("back_button_transfer")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = Color.White
            )
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // SECTION 1: ENVOYER
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("send_section_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ENVOYER SES FICHIERS",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = PrimaryBlue
                            )
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = "Envoi",
                                tint = PrimaryBlue
                            )
                        }

                        // Buttons row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!isSending) fileLauncher.launch("*/*")
                                },
                                enabled = !isSending,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryBlue.copy(alpha = 0.15f),
                                    contentColor = PrimaryBlue
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("add_files_button"),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Ajouter", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ajouter fichiers", fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    if (selectedFiles.isNotEmpty() && !isSending) {
                                        isSending = true
                                        uploadErrorMsg = null
                                        coroutineScope.launch {
                                            val myDeviceName = context.getSharedPreferences("sharelink_prefs", Context.MODE_PRIVATE)
                                                .getString("device_name", "Téléphone") ?: "Téléphone"

                                            // Step 1: POST request-transfer
                                            val accepted = withContext(Dispatchers.IO) {
                                                requestTransfer(peerIp, peerPort, myDeviceName, selectedFiles)
                                            }

                                            if (accepted) {
                                                // Step 2: Upload sequentially
                                                var isAllSuccess = true
                                                for (i in selectedFiles.indices) {
                                                    val file = selectedFiles[i]
                                                    currentFileIndex = i
                                                    currentSendingFile = file
                                                    
                                                    val uploadSuccess = withContext(Dispatchers.IO) {
                                                        uploadFile(context, peerIp, peerPort, file)
                                                    }
                                                    if (!uploadSuccess) {
                                                        isAllSuccess = false
                                                        uploadErrorMsg = "Échec d'envoi de : ${file.name}"
                                                        break
                                                    }
                                                }
                                                
                                                if (isAllSuccess) {
                                                    Toast.makeText(context, "Tous les fichiers ont été envoyés !", Toast.LENGTH_SHORT).show()
                                                    selectedFiles.clear()
                                                }
                                            } else {
                                                uploadErrorMsg = "Le PC a refusé le transfert."
                                            }
                                            isSending = false
                                            currentSendingFile = null
                                        }
                                    }
                                },
                                enabled = selectedFiles.isNotEmpty() && !isSending,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryBlue,
                                    disabledContainerColor = PrimaryBlue.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("send_files_button"),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Envoyer", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Envoyer", fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }

                        // Progress Indicator Panel
                        if (isSending && currentSendingFile != null) {
                            val totalFiles = selectedFiles.size
                            val currentIdx = currentFileIndex + 1
                            val file = currentSendingFile!!
                            
                            val progressFloat = currentFileIndex.toFloat() / totalFiles.toFloat()

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Fichier $currentIdx / $totalFiles",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${((currentFileIndex.toFloat()/totalFiles.toFloat())*100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = PrimaryBlue
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { progressFloat },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .testTag("send_progress_bar"),
                                    color = PrimaryBlue,
                                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                )

                                Text(
                                    text = "${file.name} (${formatFileSize(file.size)})",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }

                        if (uploadErrorMsg != null) {
                            Text(
                                text = uploadErrorMsg!!,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // Selected Files Preview List
                        if (selectedFiles.isNotEmpty()) {
                            Text(
                                text = "Sélectionnés (${selectedFiles.size})",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                selectedFiles.forEachIndexed { index, file ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White
                                            )
                                            Text(
                                                text = formatFileSize(file.size),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }

                                        if (!isSending) {
                                            IconButton(
                                                onClick = { selectedFiles.removeAt(index) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Retirer",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (!isSending) {
                            // Empty queue visual state
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Aucun fichier sélectionné",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 2: RECEVOIR
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("receive_section_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "FICHIERS REÇUS",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = SuccessGreen
                            )
                            IconButton(onClick = onRefreshRx, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Rafraîchir",
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Open folder button
                        Button(
                            onClick = { openFolder(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SuccessGreen.copy(alpha = 0.15f),
                                contentColor = SuccessGreen
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_folder_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Dossier", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ouvrir dossier", fontWeight = FontWeight.SemiBold)
                        }

                        // List of files received
                        if (receivedFiles.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.DownloadForOffline,
                                        contentDescription = "Vide",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Aucun fichier reçu pour le moment.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                receivedFiles.forEach { rxf ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { openReceivedFile(context, rxf.path) }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.InsertDriveFile,
                                                contentDescription = "Fichier",
                                                tint = Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = rxf.name,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "${formatFileSize(rxf.size)} • ${formatTimestamp(rxf.timestamp)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = Icons.Default.OpenInNew,
                                            contentDescription = "Ouvrir",
                                            tint = SuccessGreen.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// REST call helpers
val jsonMediaType = "application/json; charset=utf-8".toMediaType()

suspend fun requestTransfer(ip: String, port: Int, myDeviceName: String, files: List<SelectedFile>): Boolean {
    val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    val filesArray = JSONArray()
    for (f in files) {
        filesArray.put(f.name)
    }

    val json = JSONObject()
    json.put("device_name", myDeviceName)
    json.put("files", filesArray)
    json.put("port", 8080)

    val body = json.toString().toRequestBody(jsonMediaType)
    val request = Request.Builder()
        .url("http://$ip:$port/request-transfer")
        .post(body)
        .build()

    return try {
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val resStr = response.body?.string() ?: ""
                Log.d("TransferActivity", "/request-transfer response: $resStr")
                val resObj = JSONObject(resStr)
                resObj.optBoolean("accepted", false)
            } else {
                false
            }
        }
    } catch (e: Exception) {
        Log.e("TransferActivity", "Error requesting transfer", e)
        false
    }
}

suspend fun uploadFile(context: Context, ip: String, port: Int, file: SelectedFile): Boolean {
    val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    val fileBody = object : RequestBody() {
        override fun contentType() = "application/octet-stream".toMediaType()
        override fun contentLength() = file.size
        override fun writeTo(sink: BufferedSink) {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                input.source().use { source ->
                    sink.writeAll(source)
                }
            }
        }
    }

    val request = Request.Builder()
        .url("http://$ip:$port/upload")
        .addHeader("x-filename", file.name)
        .post(fileBody)
        .build()

    return try {
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val resStr = response.body?.string() ?: ""
                Log.d("TransferActivity", "Upload file response: $resStr")
                val resObj = JSONObject(resStr)
                resObj.optBoolean("success", false) || resObj.optBoolean("accepted", false)
            } else {
                false
            }
        }
    } catch (e: Exception) {
        Log.e("TransferActivity", "Error uploading file", e)
        false
    }
}
