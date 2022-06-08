package com.android.example.cameraxapp

import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import android.util.AttributeSet
import android.util.Size
import android.widget.Toast
import android.provider.MediaStore
import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.media.tv.TvContract
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Camera
import androidx.camera.core.impl.utils.ContextUtil
import androidx.camera.view.PreviewView
import androidx.core.graphics.toRect
import androidx.core.graphics.toRectF
import androidx.lifecycle.LifecycleOwner
import com.android.example.cameraxapp.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.image.ops.Rot90Op

// What is unit?
// https://stackoverflow.com/questions/55953052/kotlin-void-vs-unit-vs-nothing#:~:text=The%20Unit%20type%20is%20what,(lowercase%20v)%20in%20Java.

typealias LumaListener = (Double) -> Unit
typealias LabelListener = (String) -> Unit

class MainActivity : AppCompatActivity() {
  private lateinit var viewBinding: ActivityMainBinding
  private var imageCapture: ImageCapture? = null
  private lateinit var cameraExecutor: ExecutorService
  private lateinit var camera: Camera
  private lateinit var GFD  : GoogleFaceDetector
  private lateinit var ITF : ImageAnalyzerTF

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    viewBinding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(viewBinding.root)

    // Keep the screen awake
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    // Initialize all binding variable
    val viewFinder: PreviewView = viewBinding.viewFinder
    val popupbutton: Button = viewBinding.popupButton
    val captureButton: Button = viewBinding.imageCaptureButton

    // For Button
    captureButton.setOnClickListener{takePhoto()}
//    popupbutton.setOnClickListener{showPopUp()}
    popupbutton.setOnClickListener { showPopUpMaterial() }

    // Request camera permissions
    if (allPermissionsGranted()) {
      startCamera()
    } else {
      ActivityCompat.requestPermissions(
        this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
      )
    }

    // https://developer.android.com/training/camerax/preview
    viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER

    // Mirror viewFinder by setting the implementationMode to COMPATIBLE to force PreviewView
    // ...to use TextureView
    /** Only flip if camera front is used, otherwise don't */
//    viewFinder.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
//    viewFinder.scaleX = -1F

    cameraExecutor = Executors.newSingleThreadExecutor()
  }

  /** Testing Stuff */
  private fun showPopUpMaterial() {
    val context = this
    MaterialAlertDialogBuilder(context)
      .setTitle("Title Pop Up Material")
      .setMessage("Ini test pesan")
      .setNeutralButton("Cancel"){ dialog, which ->}
      .setPositiveButton("Cancel"){ dialog, which ->}
      .show()
  }

  private fun startCamera(){
    // ProcessCameraProvider use to bind camera lifecycle to any LifeCycleOwner within
    // an application's process
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    val faceBounds = viewBinding.faceBoundsOverlay

    // cameraProviderFuture.addListener(Runnable{}, ContextCompat.getMainExecutor(this))
    cameraProviderFuture.addListener({
      // Used to bind the lifecycle of cameras to the lifecycle owner
      val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

      // Preview
      val preview = Preview.Builder()
        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
        .build()
        .also {
          it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider) // Bind to preview layout
          Log.d(TAG, "Resolution ===> ${it.resolutionInfo.toString()}")
        }

      // Build the imageCapture
      imageCapture = ImageCapture.Builder().build()

      // Build the imageAnalyzer
      val LumaimageAnalyzer = ImageAnalysis.Builder()
        .build()
        .also {
          it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
            viewBinding.videoCaptureButton.text = luma.toString()
          })
        }
      Log.d(TAG, "Resolution : ${preview.resolutionInfo.toString()}")

      // Build the faceDetection Preview
      val widthFinder = viewBinding.viewFinder.width
      val heightFinder = viewBinding.viewFinder.height

      Log.d(TAG, "Resolution Width:  ${widthFinder.toString()}")
      Log.d(TAG, "Resolution Height:  ${heightFinder.toString()}")


      val imageFrameAnalysis = ImageAnalysis.Builder()
        .setTargetResolution(Size( widthFinder, heightFinder ) )
//                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also {
          it.setAnalyzer(cameraExecutor, GoogleFaceDetector(faceBounds))
        }

      // Select front camera as a default
      val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
      val viewFinder: PreviewView = viewBinding.viewFinder
      // If camera is front, then mirror the viewFinder
      if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA){
        viewFinder.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        viewFinder.scaleX = -1F
      }

      /** Create ImageAnalysisTF */
      val imageFrameAnalyzerTF = ImageAnalysis.Builder()
        .setTargetResolution(Size( widthFinder, heightFinder ) )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()
        .also {
          it.setAnalyzer(cameraExecutor, ImageAnalyzerTF(this, faceBounds){
//            Changing text in textview produce error --> Only the original thread that created a view hierarchy can touch its views.
//            viewBinding.tvLabel.text = it
            viewBinding.videoCaptureButton.text = it
          })
        }

      try {
        // Unbind use cases before rebinding. Check if something still binding in cameraProvider
        cameraProvider.unbindAll() // unbindAll() will close any camera that still used/open

        // Bind use cases to camera
        camera = cameraProvider.bindToLifecycle(
          this as LifecycleOwner, cameraSelector, preview, imageCapture, imageFrameAnalyzerTF
          // Now we have 3 use case for the camera: preview, imageCapture, imageFAnalyzer
        )

        // https://developer.android.com/training/camerax/configuration#camera-output

      } catch (exc: Exception) {
        Log.e(TAG, "Use case binding failed", exc)
      }

    }, ContextCompat.getMainExecutor(this)) // ContextCompat.getMainExecutor will return
    // an executor that run on main thread
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == REQUEST_CODE_PERMISSIONS) {
      if (allPermissionsGranted()) {
        startCamera()
      } else {
        Toast.makeText(
          this,
          "Permissions not granted by the user.",
          Toast.LENGTH_SHORT
        ).show()
        finish()
      }
    }
  }

  private fun takePhoto() {
    // Get a stable reference of the modifiable image capture use case
    val imageCapture = imageCapture ?: return // If imageCapture is null,
    // then exit out of the function (Elvis Operator)
    // otherwise, return the value of imageCapture

    // imageCapture will always be null if user tap the photo button before image capture is set up
    // Create time stamped name and MediaStore entry.
    val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
      .format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME, name)
      put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
        // https://developer.android.com/reference/android/provider/MediaStore.MediaColumns#RELATIVE_PATH
      }
    }

    // Create output options object which contains file + metadata
    val outputOptions = ImageCapture.OutputFileOptions
      .Builder(
        contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
      )
      .build()

    // Set up image capture listener, which is triggered after photo has been taken
    imageCapture.takePicture(
      outputOptions,
      ContextCompat.getMainExecutor(this),
      object : ImageCapture.OnImageSavedCallback { // a callback for when the image is saved
        override fun onError(exc: ImageCaptureException) {
          Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
        }

        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
          val msg = "Photo capture succeeded: ${output.savedUri}"
          Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
          Log.d(TAG, msg)
        }
      }
    )
  }

  // Check all REQUIRED_PERMISSION that declared in the object companion,
  // check if all the permission is allowed by users
  private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(
      baseContext, it
    ) == PackageManager.PERMISSION_GRANTED
  }

  override fun onDestroy() {
    super.onDestroy()
    cameraExecutor.shutdown()
  }

  // Companion Object -> An object declaration inside a class
  companion object {
    private const val TAG = "CameraXApp"
    private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    private const val REQUEST_CODE_PERMISSIONS = 123 // You can put any numbers
    private val REQUIRED_PERMISSIONS =
      mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
      ).apply {
        // If build version is lower than equal P (Pie / 9)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
          add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
      }.toTypedArray()
  }
}

/** Create Use Cases that will be used in imageFrameAnalysis that will be used in CameraBindLifecycle */
// All use cases below
// https://developers.google.com/ml-kit/vision/face-detection/android
private class GoogleFaceDetector(
  private var faceBoundOverlay : FaceBoundOverlay
) : ImageAnalysis.Analyzer {

  private val TAG = "GoogleFaceDetector"

  // STEP 1 : Configure the face detector
  // High-accuracy landmark detection and face classification
  val highAccuracyOpts = Builder()
    .setPerformanceMode(PERFORMANCE_MODE_ACCURATE)
    .setLandmarkMode(LANDMARK_MODE_ALL)
    .setClassificationMode(CLASSIFICATION_MODE_ALL)
    .build()

  // Real-time contour detection
  val realTimeOpts = Builder()
    .setContourMode(CONTOUR_MODE_ALL)
    .build()

  val customOpts = Builder()
    .enableTracking()
    .build()

  // STEP 2 : Prepare the input image
  @SuppressLint("UnsafeOptInUsageError")
  override fun analyze(imageProxy: ImageProxy) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
      val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
      // Pass image to an ML Kit Vision API

      // STEP 3 : Get an instance of FaceDetector
      val detector = FaceDetection.getClient(realTimeOpts)

      // STEP 4 : Process the image
      val result = detector.process(image)
        .addOnSuccessListener { faces -> // Task completed successfully
          // STEP 5 : Get information about detected faces
          /**
          for (face in faces) {
            val bounds = face.boundingBox
            val rotY = face.headEulerAngleY // Head is rotated to the right rotY degrees
            val rotZ = face.headEulerAngleZ // Head is tilted sideways rotZ degrees

            if (face.trackingId != null) {
              val id = face.trackingId
            }
            Log.d(TAG, "Found a face: ${face.toString()}")
          }
          */
          // Map faces and send it to faceBoundOverlay to draw Bounding Box
          val mappingFace = faces.map { it.boundingBox.toRectF() }
          faceBoundOverlay.drawFaceBounds(mappingFace)
        }
        .addOnFailureListener { e -> // Task failed with an exception
          Log.e(TAG, e.message.toString())
        }
        .addOnSuccessListener { // IMPORTANT : If success, then close the image proxy
          imageProxy.close()
        }
    }
  }
}

/** Tensorflow image analysis goes here */
private class ImageAnalyzerTF(val context: Context, private var faceBoundOverlay : FaceBoundOverlay, private val listener:LabelListener) : ImageAnalysis.Analyzer {
  // https://www.tensorflow.org/lite/android/quickstart
  /** Initialize var for the settings used in TF */
  private lateinit var bitmapBuffer: Bitmap
  private var imageRotationDegrees: Int = 0
  private val tfImageBuffer = TensorImage(DataType.UINT8)

  private val tflite by lazy {
    Interpreter(
      FileUtil.loadMappedFile(context, MODEL_PATH),
      Interpreter.Options().addDelegate(nnApiDelegate))
  }

  private val nnApiDelegate by lazy  {
    NnApiDelegate()
  }

  private val tfInputSize by lazy {
    val inputIndex = 0
    val inputShape = tflite.getInputTensor(inputIndex).shape()
    Size(inputShape[2], inputShape[1]) // Order of axis is: {1, height, width, 3}
  }

  private val tfImageProcessor by lazy {
    val cropSize = minOf(bitmapBuffer.width, bitmapBuffer.height)
    ImageProcessor.Builder()
      .add(ResizeWithCropOrPadOp(cropSize, cropSize))
      .add(
        ResizeOp(
          tfInputSize.height, tfInputSize.width, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR)
      )
      .add(Rot90Op(-imageRotationDegrees / 90))
      .add(NormalizeOp(0f, 1f))
      .build()
  }

  private val detector by lazy {
    ObjectDetectionHelper(
      tflite,
      FileUtil.loadLabels(context, LABELS_PATH)
    )
  }

  override fun analyze(image: ImageProxy) {
    if (!::bitmapBuffer.isInitialized) {
      // The image rotation and RGB image buffer are initialized only once
      // the analyzer has started running
      imageRotationDegrees = image.imageInfo.rotationDegrees
      bitmapBuffer = Bitmap.createBitmap(
        image.width, image.height, Bitmap.Config.ARGB_8888)
    }

    // Log.d("DEBUG", bitmapBuffer.config.toString())

    // Copy out RGB bits to our shared buffer
    image.use {
      bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer)
    }

    // Process the image in Tensorflow
    val tfImage =  tfImageProcessor.process(tfImageBuffer.apply { load(bitmapBuffer) })

    // Perform the object detection for the current frame
    val predictions = detector.predict(tfImage)
    Log.d("TFLite", predictions.get(0).toString())

    listener(predictions.get(0).label)

    // Show bounding box from predictions
    val mappingObject = listOf(predictions.get(0).location)
    Log.d("TFLite BoundingBox", mappingObject.get(0).toString())
    faceBoundOverlay.drawFaceBounds(mappingObject)
  }

  companion object{
    // Define the settings for the model used in TF
    private const val ACCURACY_THRESHOLD = 0.5f
    private const val MODEL_PATH = "coco_ssd_mobilenet_v1_1.0_quant.tflite"
    private const val LABELS_PATH = "coco_ssd_mobilenet_v1_1.0_labels.txt"
  }
}

class ObjectDetectionHelper(private val tflite: Interpreter, private val labels: List<String>) {
  /** Abstraction object that wraps a prediction output in an easy to parse way */
  data class ObjectPrediction(val location: RectF, val label: String, val score: Float)

  private val locations = arrayOf(Array(OBJECT_COUNT) { FloatArray(4) })
  private val labelIndices =  arrayOf(FloatArray(OBJECT_COUNT))
  private val scores =  arrayOf(FloatArray(OBJECT_COUNT))

  private val outputBuffer = mapOf(
    0 to locations,
    1 to labelIndices,
    2 to scores,
    3 to FloatArray(1)
  )

  val predictions get() = (0 until OBJECT_COUNT).map {
    ObjectPrediction(

      // The locations are an array of [0, 1] floats for [top, left, bottom, right]
      location = locations[0][it].let {
        RectF(it[1], it[0], it[3], it[2])
      },

      // SSD Mobilenet V1 Model assumes class 0 is background class
      // in label file and class labels start from 1 to number_of_classes + 1,
      // while outputClasses correspond to class index from 0 to number_of_classes
      label = labels[1 + labelIndices[0][it].toInt()],

      // Score is a single value of [0, 1]
      score = scores[0][it]
    )
  }

  fun predict(image: TensorImage): List<ObjectPrediction> {
    tflite.runForMultipleInputsOutputs(arrayOf(image.buffer), outputBuffer)
    return predictions
  }

  companion object {
    const val OBJECT_COUNT = 10
  }
}


// Add an inner class to implement ImageAnalysis.Analyzer interface for luminosity of the image
private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {
  private fun ByteBuffer.toByteArray(): ByteArray {
    rewind()    // Rewind the buffer to zero
    val data = ByteArray(remaining())
    get(data)   // Copy the buffer into a byte array
    return data // Return the byte array
  }

  override fun analyze(image: ImageProxy) {
    val buffer = image.planes[0].buffer
    val data = buffer.toByteArray()
    val pixels = data.map { it.toInt() and 0xFF }
    val luma = pixels.average()
    listener(luma)
    image.close()
  }
}

/** Creating RectOverlay to create a custom view */
// https://stackoverflow.com/questions/63090795/how-to-draw-on-previewview

class FaceBoundOverlay constructor(context: Context?, attributeSet: AttributeSet?) :
  View(context, attributeSet) {

  val TAG = "FaceBoundOverlay"
  val faceBounds: MutableList<RectF> = mutableListOf()

  private val customPaint = Paint().also{
    it.style = Paint.Style.STROKE
    it.color = Color.RED
    it.strokeWidth = 10f
  }

  // Override onDraw() to paint some UI above Camera Preview
  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    // Pass it a list of RectF (rectBounds)
    faceBounds.forEach {
//      val temp = it.left
//      it.left = it.right
//      it.right = temp

      // Surface resolution 1440x1080
      val height = height
      val width = width

      it.left = it.left * width
      it.right = it.right * width
      it.top = it.top * height
      it.bottom = it.bottom * height

      canvas.drawRoundRect(it, 16F, 16F, customPaint)

      Log.d(TAG, "Draw the bounding box of : ${it.toString()} Surface Res = ${width} x ${height}")
    }
//    val valuerectF = RectF(20F, 10F, 50F, 50F)
//    canvas.drawRoundRect(valuerectF, 16F, 16F, customPaint)
  }

  fun drawFaceBounds(faceBounds: List<RectF>){
    this.faceBounds.clear()
    this.faceBounds.addAll(faceBounds)

    invalidate()
    // Invalid to re-draw the canvas
    // Method onDraw will be called with new data.
  }
}
data class Prediction(var bbox : Rect, var label : String)