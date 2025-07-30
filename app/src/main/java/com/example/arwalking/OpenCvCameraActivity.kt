package com.example.arwalking

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import org.opencv.android.*
import org.opencv.core.*
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.Features2d
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint

class OpenCvCameraActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private lateinit var openCvCameraView: JavaCameraView
    private lateinit var referenceImage: Mat
    private lateinit var orb: ORB
    private lateinit var referenceDescriptors: Mat
    private lateinit var referenceKeypoints: MatOfKeyPoint

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OpenCVLoader.initDebug()

        setContentView(R.layout.activity_opencv_camera)

        openCvCameraView = findViewById(R.id.camera_view)
        openCvCameraView.visibility = SurfaceView.VISIBLE
        openCvCameraView.setCvCameraViewListener(this)
        openCvCameraView.enableView()

        loadReferenceImage()
    }

    private fun loadReferenceImage() {
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.object_reference)
        referenceImage = Mat()
        Utils.bitmapToMat(bitmap, referenceImage)
        Imgproc.cvtColor(referenceImage, referenceImage, Imgproc.COLOR_RGBA2GRAY)

        orb = ORB.create()
        referenceKeypoints = MatOfKeyPoint()
        referenceDescriptors = Mat()
        orb.detectAndCompute(referenceImage, Mat(), referenceKeypoints, referenceDescriptors)
    }

    override fun onCameraViewStarted(width: Int, height: Int) {}

    override fun onCameraViewStopped() {}

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        val frame = inputFrame!!.gray()
        val keypoints = MatOfKeyPoint()
        val descriptors = Mat()

        orb.detectAndCompute(frame, Mat(), keypoints, descriptors)

        val bf = BFMatcher.create(Core.NORM_HAMMING, true)
        val matches = MatOfDMatch()

        if (!descriptors.empty() && !referenceDescriptors.empty()) {
            bf.match(referenceDescriptors, descriptors, matches)
        }

        val output = Mat()
        Features2d.drawMatches(referenceImage, referenceKeypoints, frame, keypoints, matches, output)

        return output
    }

    override fun onDestroy() {
        super.onDestroy()
        openCvCameraView.disableView()
    }
}
