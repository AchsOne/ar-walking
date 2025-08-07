package org.opencv.android

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView
import org.opencv.core.Mat

/**
 * OpenCV Camera Bridge View Stub
 */
abstract class CameraBridgeViewBase @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr) {
    
    interface CvCameraViewListener2 {
        fun onCameraViewStarted(width: Int, height: Int)
        fun onCameraViewStopped()
        fun onCameraFrame(inputFrame: CvCameraViewFrame?): Mat
    }
    
    interface CvCameraViewFrame {
        fun gray(): Mat
        fun rgba(): Mat
    }
    
    private var listener: CvCameraViewListener2? = null
    private var isEnabled = false
    
    fun setCvCameraViewListener(listener: CvCameraViewListener2) {
        this.listener = listener
    }
    
    fun enableView() {
        isEnabled = true
        listener?.onCameraViewStarted(640, 480)
        // Echte Kamera-Integration würde hier implementiert werden
    }
    
    fun disableView() {
        isEnabled = false
        listener?.onCameraViewStopped()
    }
}