package com.example.local_rag.ai

import android.content.Context
import com.example.local_rag.data.TextChunk
import com.example.local_rag.data.TextChunk_
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.ml.shubham0204.sentence_embeddings.SentenceEmbedding
import io.objectbox.Box
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalAiManager(
    private val context: Context,
    private val boxStore: BoxStore
) {
    private val chunkBox: Box<TextChunk> = boxStore.boxFor(TextChunk::class.java)
    
    private var llmInference: LlmInference? = null
    private val embedder = SentenceEmbedding()
    private var isEmbedderReady = false

    suspend fun initializeModels(
        llmModelPath: String,
        embedderModelPath: String, 
        tokenizerPath: String
    ) = withContext(Dispatchers.IO) {
        
        val embedderFile = File(embedderModelPath)
        val tokenizerFile = File(tokenizerPath)
        
        if (embedderFile.exists() && tokenizerFile.exists()) {
            embedder.init(
                modelFilepath = embedderModelPath,
                tokenizerBytes = tokenizerFile.readBytes(),
                useTokenTypeIds = true,
                outputTensorName = "sentence_embedding",
                normalizeEmbeddings = true,
                useFP16 = false,
                useXNNPack = false 
            )
            isEmbedderReady = true
        }

        val llmFile = File(llmModelPath)
        if (llmFile.exists()) {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(llmModelPath)
                .setMaxTokens(4096)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
                
            llmInference = LlmInference.createFromOptions(context, options)
        }
    }

    suspend fun indexTextbook(sourceName: String, rawText: String) = withContext(Dispatchers.IO) {
        require(isEmbedderReady) { "Embedder not initialized!" }
        
        // Chunk the text into safe 1000-character blocks (prevents LLM overload)
        val paragraphs = rawText.chunked(1000).filter { it.isNotBlank() }
        
        val chunksToSave = paragraphs.map { paragraph ->
            val vector = embedder.encode(paragraph)
            
            TextChunk(
                sourceName = sourceName,
                content = paragraph,
                embedding = vector
            )
        }
        
        chunkBox.put(chunksToSave)
    }

    suspend fun askScienceQuestion(question: String): Pair<String, String> = withContext(Dispatchers.IO) {
        if (!isEmbedderReady || llmInference == null) {
            return@withContext Pair("AI Models not fully initialized yet. Please place the model files in the app's files directory.", "System")
        }

        val questionVector = embedder.encode(question)

        val nearestNeighbors = chunkBox.query(
            TextChunk_.embedding.nearestNeighbors(questionVector, 3)
        ).build().findWithScores()

        val prompt: String
        val primarySource: String

        if (nearestNeighbors.isEmpty()) {
            primarySource = "General Knowledge"
            prompt = """
                You are a helpful chatbot.
                
                Question: $question
                
                Answer:
            """.trimIndent()
        } else {
            val contextBuilder = java.lang.StringBuilder()
            nearestNeighbors.forEach { match ->
                contextBuilder.append("- ").append(match.get().content).append("\n")
            }
            primarySource = nearestNeighbors.first().get().sourceName
            
            // Safety net: ensure context is never larger than ~3000 chars to avoid MediaPipe C++ crashes
            val safeContext = contextBuilder.toString().take(3000)

            prompt = """
                You are a helpful science tutor. Answer the question based ONLY on the provided Context.
                
                Context:
                $safeContext
                
                Question: $question
                
                Answer:
            """.trimIndent()
        }

        val finalAnswer = llmInference!!.generateResponse(prompt)

        return@withContext Pair(finalAnswer, primarySource)
    }

    fun close() {
        llmInference?.close()
    }
}
