package com.example

import android.app.Application
import android.util.Log
import java.io.File

class ShareLinkApp : Application() {
    companion object {
        var server: ShareLinkServer? = null
        private val fileListeners = mutableListOf<(File) -> Unit>()
        
        fun addFileListener(listener: (File) -> Unit) {
            synchronized(fileListeners) {
                fileListeners.add(listener)
            }
        }
        
        fun removeFileListener(listener: (File) -> Unit) {
            synchronized(fileListeners) {
                fileListeners.remove(listener)
            }
        }
        
        fun notifyFileReceived(file: File) {
            synchronized(fileListeners) {
                for (listener in fileListeners) {
                    try {
                        listener(file)
                    } catch (e: Exception) {
                        Log.e("ShareLinkApp", "Error notifying file listener: ", e)
                    }
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d("ShareLinkApp", "Initializing ShareLinkApp")
        // Start the NanoHTTPD server on port 8080
        try {
            server = ShareLinkServer(this) { file ->
                notifyFileReceived(file)
            }
            copyApkFromAssetsToStorage()
        } catch (e: Exception) {
            Log.e("ShareLinkApp", "Failed to start server: ", e)
        }
    }

    private fun copyApkFromAssetsToStorage() {
        try {
            val filename = "app-debug.apk"
            val targetDir = getExternalFilesDir("ShareLink") ?: File(filesDir, "ShareLink")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val targetFile = File(targetDir, filename)
            
            // Prefer copying from the actual running installed APK
            val liveApkFile = File(packageCodePath)
            if (liveApkFile.exists()) {
                liveApkFile.inputStream().use { inputStream ->
                    targetFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.d("ShareLinkApp", "Successfully copied APK from packageCodePath to: ${targetFile.absolutePath}")
            } else {
                assets.open(filename).use { inputStream ->
                    targetFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.d("ShareLinkApp", "Successfully copied APK from assets fallback to: ${targetFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("ShareLinkApp", "Error copying APK, first-run build step fallback in place: ", e)
        }
    }
}
