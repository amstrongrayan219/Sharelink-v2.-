package com.example

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ShareLinkServer(
    private val context: Context,
    private val onFileReceived: (File) -> Unit
) : NanoHTTPD(8080) {

    init {
        try {
            start(SOCKET_READ_TIMEOUT, false)
            Log.d("ShareLinkServer", "NanoHTTPD Server running on port 8080")
        } catch (e: Exception) {
            Log.e("ShareLinkServer", "Could not start server: ", e)
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        
        Log.d("ShareLinkServer", "Received request: Method = $method, URI = $uri")
        
        if (uri == "/request-transfer" && method == Method.POST) {
            return try {
                val files = HashMap<String, String>()
                session.parseBody(files)
                Log.d("ShareLinkServer", "/request-transfer parsed successfully: $files")
                
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    "{\"accepted\":true}"
                )
            } catch (e: Exception) {
                Log.e("ShareLinkServer", "Error parsing /request-transfer", e)
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    "{\"accepted\":true}"
                )
            }
        }
        
        if (uri == "/upload" && method == Method.POST) {
            return try {
                val headers = session.headers
                val filename = headers["x-filename"] ?: headers["X-FILENAME"] ?: "received_file_${System.currentTimeMillis()}"
                
                val files = HashMap<String, String>()
                session.parseBody(files)
                
                val tempPath = files["content"] ?: files["postData"]
                
                val targetDir = context.getExternalFilesDir("ShareLink") ?: File(context.filesDir, "ShareLink")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                
                val targetFile = File(targetDir, filename)
                
                if (tempPath != null) {
                    val tempFile = File(tempPath)
                    if (tempFile.exists()) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        Log.d("ShareLinkServer", "Uploaded file saved to: ${targetFile.absolutePath}")
                    } else {
                        FileOutputStream(targetFile).use { out ->
                            out.write(tempPath.toByteArray(Charsets.UTF_8))
                        }
                    }
                } else {
                    val input = session.inputStream
                    FileOutputStream(targetFile).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                        }
                    }
                }
                
                onFileReceived(targetFile)
                
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    "{\"success\":true}"
                )
            } catch (e: Exception) {
                Log.e("ShareLinkServer", "Error parsing upload stream: ", e)
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "application/json",
                    "{\"success\":false,\"error\":\"${e.message}\"}"
                )
            }
        }
        
        return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            "text/plain",
            "Not Found"
        )
    }
}
