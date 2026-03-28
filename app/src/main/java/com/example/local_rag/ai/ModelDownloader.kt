package com.example.local_rag.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {
    
    data class DownloadStatus(
        val filename: String,
        val progressPercent: Int,
        val isFinished: Boolean = false,
        val error: String? = null
    )

    fun downloadFile(context: Context, urlString: String, filename: String): Flow<DownloadStatus> = flow {
        val destFile = File(context.filesDir, filename)
        
        if (destFile.exists() && destFile.length() > 1000) {
            emit(DownloadStatus(filename, 100, true))
            return@flow
        }

        try {
            emit(DownloadStatus(filename, 0))
            
            var url = URL(urlString)
            var connection = url.openConnection() as HttpURLConnection
            var redirect = false
            var redirectsCount = 0
            
            do {
                connection.instanceFollowRedirects = false
                connection.connect()
                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_OK && (status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_SEE_OTHER
                    || status == 307 || status == 308)) {
                    var newUrl = connection.getHeaderField("Location")
                    if (newUrl.startsWith("/")) {
                        newUrl = "${url.protocol}://${url.host}$newUrl"
                    }
                    connection.disconnect()
                    url = URL(newUrl)
                    connection = url.openConnection() as HttpURLConnection
                    redirect = true
                    redirectsCount++
                } else {
                    redirect = false
                }
            } while (redirect && redirectsCount < 5)

            val fileLength = connection.contentLength
            val input = connection.inputStream
            val output = destFile.outputStream()
            
            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int
            var lastProgress = 0
            
            while (input.read(data).also { count = it } != -1) {
                total += count
                output.write(data, 0, count)
                
                val currentProgress = ((total * 100) / fileLength).toInt()
                if (currentProgress > lastProgress) {
                    lastProgress = currentProgress
                    emit(DownloadStatus(filename, currentProgress))
                }
            }
            
            output.flush()
            output.close()
            input.close()
            
            emit(DownloadStatus(filename, 100, true))
        } catch (e: Exception) {
            if (destFile.exists()) destFile.delete()
            emit(DownloadStatus(filename, 0, false, e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)
}