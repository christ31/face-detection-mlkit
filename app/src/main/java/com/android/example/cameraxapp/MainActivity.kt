package com.android.example.cameraxapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import com.android.example.cameraxapp.databinding.ActivityMainBinding
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import android.provider.MediaStore

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.PopupWindow
import androidx.camera.core.Camera
import androidx.camera.view.PreviewView
import androidx.core.graphics.toRectF
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions.*

// What is unit?
// https://stackoverflow.com/questions/55953052/kotlin-void-vs-unit-vs-nothing#:~:text=The%20Unit%20type%20is%20what,(lowercase%20v)%20in%20Java.

typealias LumaListener = (Double) -> Unit

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var camera: Camera

    private lateinit var GFD  : GoogleFaceDetector

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
        popupbutton.setOnClickListener{showPopUp()}

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
        viewFinder.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        viewFinder.scaleX = -1F

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun showPopUp() {
        val viewPopup = layoutInflater.inflate(R.layout.activity_pop_up_window, null)
        val popup = PopupWindow(viewPopup, 200, 500)
        popup.also {
            it.showAtLocation(viewBinding.root, Gravity.CENTER, 0, 0)
        }
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
            GFD = GoogleFaceDetector(faceBounds)

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
//                        Log.d(TAG, "Average luminiosity: $luma")
                        viewBinding.videoCaptureButton.text = luma.toString()
                    })
                }
            Log.d(TAG, "Resolution : ${preview.resolutionInfo.toString()}")

            // Build the faceDetection Preview
            val widthFinder = viewBinding.viewFinder.width
            val heightFinder = viewBinding.viewFinder.height
            val imageFrameAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size( widthFinder, heightFinder ) )
//                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, GFD )
                }

            // Select front camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding. Check if something still binding in cameraProvider
                cameraProvider.unbindAll() // unbindAll() will close any camera that still used / open

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    this as LifecycleOwner, cameraSelector, preview, imageCapture, imageFrameAnalysis
                    // Now we have 3 use case for the camera: preview, imageCapture, imageAnalyzer
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

        // Set up image capture listener, which is triggered after photo has
        // been taken
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

    // Overide onDraw() to paint some UI above Camera Preview

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

// All use cases below
// https://developers.google.com/ml-kit/vision/face-detection/android
private class GoogleFaceDetector(
    private var faceBoundOverlay : FaceBoundOverlay
) : ImageAnalysis.Analyzer {

    /**
     * Initialize
     */
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
            // ...

    // STEP 3 : Get an instance of FaceDetector
            val detector = FaceDetection.getClient(realTimeOpts)

    // STEP 4 : Process the image
            val result = detector.process(image)
                .addOnSuccessListener { faces ->
                    // Task completed successfully
                    // ...
    // STEP 5 : Get information about detected faces
                    for (face in faces) {
                        val bounds = face.boundingBox
                        val rotY = face.headEulerAngleY // Head is rotated to the right rotY degrees
                        val rotZ = face.headEulerAngleZ // Head is tilted sideways rotZ degrees

                        if (face.trackingId != null) {
                            val id = face.trackingId
                        }
                        Log.d(TAG, "Found a face: ${face.toString()}")

                    }
                    // Map faces and send it to faceBoundOverlay to draw Bounding Box
                    val mappingFace = faces.map { it.boundingBox.toRectF() }
                    faceBoundOverlay.drawFaceBounds(mappingFace)
                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    // ...
                    Log.e(TAG, e.message.toString())
                }
                .addOnSuccessListener {
                    // IMPORTANT : If success, then close the image proxy
                    imageProxy.close()
                }
        }
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

// Creating RectOverlay to create a custom view
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Pass it a list of RectF (rectBounds)
        faceBounds.forEach {
            val temp = it.left
            it.left = it.right
            it.right = temp

            canvas.drawRoundRect(it, 16F, 16F, customPaint)

            Log.d(TAG, "Draw the bounding box of : ${it.toString()}")
        }
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