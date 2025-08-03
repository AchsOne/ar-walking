package org.opencv.features2d

import org.opencv.core.Mat
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfDMatch

/**
 * OpenCV Features2d Stub
 */
object Features2d {
    
    fun drawMatches(
        img1: Mat,
        keypoints1: MatOfKeyPoint,
        img2: Mat,
        keypoints2: MatOfKeyPoint,
        matches1to2: MatOfDMatch,
        outImg: Mat,
        matchColor: Any? = null,
        singlePointColor: Any? = null,
        matchesMask: Any? = null,
        flags: Int = 0
    ) {
        // Simuliere das Zeichnen von Matches
        outImg.width = img1.width + img2.width
        outImg.height = maxOf(img1.height, img2.height)
        outImg.channels = 3 // RGB
    }
    
    fun drawKeypoints(
        image: Mat,
        keypoints: MatOfKeyPoint,
        outImage: Mat,
        color: Any? = null,
        flags: Int = 0
    ) {
        // Simuliere das Zeichnen von Keypoints
        outImage.width = image.width
        outImage.height = image.height
        outImage.channels = image.channels
    }
}