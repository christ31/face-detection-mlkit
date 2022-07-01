package com.android.example.SKRpresensi

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.graphics.toRectF
import com.android.example.SKRpresensi.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions


/** Create Use Cases that will be used in imageFrameAnalysis */
class GoogleFaceDetector(
  var context: Context,
  private var faceBoundOverlay: FaceBoundOverlay,
  viewBinding: ActivityMainBinding,
  var listener: LabelListener,
) : ImageAnalysis.Analyzer {

  private val TAG = "GoogleFaceDetector"
  private val model = TFModel(context, listener, viewBinding)

  // STEP 1 : Configure the face detector
  // High-accuracy landmark detection and face classification
  val highAccuracyOpts = FaceDetectorOptions.Builder()
    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
    .build()

  // Real-time contour detection
  val realTimeOpts = FaceDetectorOptions.Builder()
    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
    .build()

  val customOpts = FaceDetectorOptions.Builder()
    .enableTracking()
    .build()

  // STEP 2 : Prepare the input image
  @SuppressLint("UnsafeOptInUsageError")
  override fun analyze(imageProxy: ImageProxy) {
    val mediaImage = imageProxy.image

    if (mediaImage != null) {
      val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

      // STEP 3 : Get an instance of FaceDetector
      val detector = FaceDetection.getClient(realTimeOpts)

      // STEP 4 : Process the image
      val result = detector.process(image)
        .addOnSuccessListener { faces -> // Task completed successfully
          // STEP 5 : Get information about detected faces

          /** For now, we will focus on 1 faces recognition in 1 images */
          // Map faces and send it to faceBoundOverlay to draw Bounding Box
          val mappingFace = faces.map { it.boundingBox.toRectF() }
          // Log.d(TAG, "face = ${faces.size}")

          // Send GoogleML's imageProxy to FaceNet
          /** Call FaceNet Model from here */
          if(faces.isNotEmpty()){
            model.setFaceBound(faces[0].boundingBox)
            val prediction = model.analyze(imageProxy, faces.size)
            faceBoundOverlay.drawFaceBounds(mappingFace, prediction)
          } else {
            // Clear boundingbox
            faceBoundOverlay.clear()
            model.resetQuery()
          }
        }
        .addOnFailureListener { e -> // Task failed with an exception
          // Log.e(TAG, e.message.toString())
        }
        .addOnSuccessListener { /** IMPORTANT : If success, then close the image proxy */
          imageProxy.close()
        }
    }
  }
}
