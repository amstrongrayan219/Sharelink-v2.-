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
        } catch (e: Exception) {
            Log.e("ShareLinkApp", "Failed to start server: ", e)
        }
    }
}
