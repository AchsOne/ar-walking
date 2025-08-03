package org.opencv.android

import android.util.Log

/**
 * OpenCV Loader Stub für lokale Entwicklung
 */
object OpenCVLoader {
    private const val TAG = "OpenCVLoader"
    
    fun initDebug(): Boolean {
        Log.d(TAG, "OpenCV Debug Mode initialisiert (Stub)")
        return true
    }
    
    fun initAsync(version: String, context: android.content.Context, callback: LoaderCallbackInterface): Boolean {
        Log.d(TAG, "OpenCV Async initialisiert (Stub)")
        // Simuliere erfolgreiche Initialisierung
        callback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        return true
    }
}

interface LoaderCallbackInterface {
    companion object {
        const val SUCCESS = 0
        const val INIT_FAILED = -1
    }
    
    fun onManagerConnected(status: Int)
    fun onPackageInstall(operation: Int, callback: InstallCallbackInterface) {}
}

interface InstallCallbackInterface {
    fun install() {}
    fun cancel() {}
    fun wait_install() {}
}