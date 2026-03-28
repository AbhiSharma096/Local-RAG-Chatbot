package com.example.local_rag

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.local_rag.ai.LocalAiManager
import com.example.local_rag.ai.ModelDownloader
import com.example.local_rag.data.ObjectBox
import com.example.local_rag.ui.theme.Local_RagTheme
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var aiManager: LocalAiManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        ObjectBox.init(this)
        PDFBoxResourceLoader.init(applicationContext)
        
        aiManager = LocalAiManager(this, ObjectBox.store)
        
        enableEdgeToEdge()
        setContent {
            Local_RagTheme {
                ChatScreen(aiManager)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        aiManager.close()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(aiManager: LocalAiManager) {
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(true) }
    
    var showDownloadScreen by remember { mutableStateOf(false) }
    var downloadStatusText by remember { mutableStateOf("") }
    
    var expandedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val llmFilename = "gemma3-1b-it-int4.task"
    val onnxFilename = "model.onnx"
    val tokenizerFilename = "tokenizer.json"

    val llmPath = java.io.File(context.filesDir, llmFilename)
    val embedderPath = java.io.File(context.filesDir, onnxFilename)
    val tokenizerPath = java.io.File(context.filesDir, tokenizerFilename)

    LaunchedEffect(Unit) {
        if (!llmPath.exists() || !embedderPath.exists() || !tokenizerPath.exists()) {
            showDownloadScreen = true
            isTyping = false
        } else {
            messages = messages + ChatMessage("Initializing local AI models...", false, "System")
            try {
                aiManager.initializeModels(llmPath.absolutePath, embedderPath.absolutePath, tokenizerPath.absolutePath)
                messages = messages + ChatMessage("AI Models Loaded successfully! Click the attachment icon to upload a PDF.", false, "System")
            } catch (e: Exception) {
                messages = messages + ChatMessage("Failed to load models.\nError: ${e.message}", false, "Error")
            } finally {
                isTyping = false
            }
        }
    }

    if (showDownloadScreen) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("AI Models Required", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("This app runs entirely offline, but requires downloading the base AI models (~1.3 GB) on first launch. Please ensure you are on Wi-Fi.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            
            if (downloadStatusText.isEmpty()) {
                Button(onClick = {
                    coroutineScope.launch {
                        val modelsToDownload = listOf(
                            Pair("https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/tokenizer.json", tokenizerFilename),
                            Pair("https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx", onnxFilename),
                            Pair("https://huggingface.co/AfiOne/gemma3-1b-it-int4.task/resolve/main/gemma3-1b-it-int4.task", llmFilename)
                        )
                        
                        var hasError = false
                        for ((url, filename) in modelsToDownload) {
                            if (hasError) break
                            ModelDownloader.downloadFile(context, url, filename).collect { status ->
                                if (status.error != null) {
                                    downloadStatusText = "Error downloading $filename: ${status.error}"
                                    hasError = true
                                } else {
                                    downloadStatusText = "Downloading $filename... ${status.progressPercent}%"
                                }
                            }
                        }
                        
                        if (!hasError) {
                            downloadStatusText = "Downloads Complete! Initializing..."
                            try {
                                aiManager.initializeModels(llmPath.absolutePath, embedderPath.absolutePath, tokenizerPath.absolutePath)
                                messages = messages + ChatMessage("AI Models Downloaded and Loaded! You're ready to go.", false, "System")
                            } catch (e: Exception) {
                                messages = messages + ChatMessage("Initialization failed: ${e.message}", false, "Error")
                            }
                            showDownloadScreen = false
                        }
                    }
                }) {
                    Text("Download Models")
                }
            } else {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(downloadStatusText)
            }
        }
        return
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { selectedUri ->
                coroutineScope.launch {
                    val fileName = getFileName(context, selectedUri) ?: "Unknown PDF"
                    
                    messages = messages + ChatMessage("Indexing PDF: $fileName... This might take a minute depending on its size.", false, "System")
                    isTyping = true
                    
                    try {
                        processAndIndexPdf(context, selectedUri, aiManager)
                        messages = messages + ChatMessage("Successfully learned from $fileName! You can now ask questions about it.", false, "System")
                    } catch (e: Exception) {
                        messages = messages + ChatMessage("Failed to index PDF: ${e.message}", false, "Error")
                    } finally {
                        isTyping = false
                    }
                }
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Local Science Tutor", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        IconButton(onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }) {
                            Icon(Icons.Default.Add, contentDescription = "Upload PDF")
                        }
                    }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        MessageBubble(message = message, onClick = {
                            if (!message.isUser) expandedMessage = message
                        })
                    }
                    if (isTyping) {
                        item {
                            Text(
                                text = "Tutor is thinking...",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
    
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask a science question...") },
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (inputText.isNotBlank() && !isTyping) {
                                val userMsg = inputText
                                messages = messages + ChatMessage(userMsg, true)
                                inputText = ""
                                isTyping = true
                                
                                coroutineScope.launch {
                                    try {
                                        val (answer, source) = aiManager.askScienceQuestion(userMsg)
                                        messages = messages + ChatMessage(answer, false, source)
                                    } catch (e: Exception) {
                                        messages = messages + ChatMessage("Error generating answer: ${e.message}", false, "Error")
                                    } finally {
                                        isTyping = false
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Send")
                    }
                }
            }
        }
        
        if (expandedMessage != null) {
            ExpandedMessageView(
                message = expandedMessage!!,
                onBack = { expandedMessage = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedMessageView(message: ChatMessage, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Answer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Markdown(
                content = message.text,
                colors = markdownColor(text = MaterialTheme.colorScheme.onSurface)
            )
            
            if (message.source != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Source: ${message.source}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val source: String? = null
)

@Composable
fun MessageBubble(message: ChatMessage, onClick: () -> Unit = {}) {
    val backgroundColor = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (message.isUser) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .background(backgroundColor, shape)
                .clip(shape)
                .clickable(enabled = !message.isUser, onClick = onClick)
                .padding(12.dp)
                .widthIn(max = 280.dp)
        ) {
            val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            
            Markdown(
                content = message.text,
                colors = markdownColor(text = textColor)
            )
            
            if (message.source != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Source: ${message.source}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

suspend fun processAndIndexPdf(context: Context, pdfUri: Uri, aiManager: LocalAiManager) {
    withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val extractedText = stripper.getText(document)
            document.close()
            
            val fileName = getFileName(context, pdfUri) ?: "Unknown_Textbook.pdf"
            aiManager.indexTextbook(sourceName = fileName, rawText = extractedText)
        }
    }
}

fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result
}
