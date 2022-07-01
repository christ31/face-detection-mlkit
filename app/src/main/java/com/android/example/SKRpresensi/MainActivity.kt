package com.android.example.SKRpresensi

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRectF
import androidx.lifecycle.LifecycleOwner
import com.android.example.SKRpresensi.databinding.ActivityBottomSheetBinding
import com.android.example.SKRpresensi.databinding.ActivityMainBinding
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions.*
import es.dmoral.toasty.Toasty
import org.json.JSONException
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import java.io.*
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.HashMap
import kotlin.math.sqrt


typealias LabelListener = (String) -> Unit

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {
  private lateinit var viewBinding: ActivityMainBinding

  private var imageCapture: ImageCapture? = null
  private lateinit var cameraExecutor: ExecutorService
  private lateinit var camera: Camera
  private var registerState: Boolean = false

  private var listRuang = arrayOfNulls<String>(5)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    viewBinding = ActivityMainBinding.inflate(layoutInflater)

    setContentView(viewBinding.root)

    // Keep the screen awake
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    // Initialize all binding variable
    val viewFinder: PreviewView = viewBinding.viewFinder
    val popupButton: Button = viewBinding.bottomSheet.popupButton
    val captureButton: Button = viewBinding.bottomSheet.imageCaptureButton
    val kotakNama: EditText = viewBinding.bottomSheet.editTextNama
    val faceCapture: Button = viewBinding.bottomSheet.captureButton
    val report: Button = viewBinding.bottomSheet.btnReport
    val update: Button = viewBinding.bottomSheet.btnUpdate
    val get: Button = viewBinding.bottomSheet.btnGet
    val dropdownRuang: Spinner = viewBinding.bottomSheet.spnRuang

    // For Button
    captureButton.setOnClickListener{takePhoto()}
    popupButton.setOnClickListener{showPopUpMaterial()}
    faceCapture.setOnClickListener{registerFace()}

    // Check for connection to DB
    getRuang()

    // If conn = true, then spinner
    var listofitems = arrayOf("Item 1", "Item 2", "Item 3", "Item 3", "Item 3")
    dropdownRuang.onItemSelectedListener = this

    val arrayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listofitems)
    arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    dropdownRuang.adapter = arrayAdapter

    // Request camera permissions
    if (allPermissionsGranted()) {
      startCamera()
    } else {
      ActivityCompat.requestPermissions(
        this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
      )
    }

    viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER

    // Mirror viewFinder by setting the implementationMode to COMPATIBLE to force PreviewView..
    // ..to use TextureView

    /** Only flip if camera front is used, otherwise don't */
    // viewFinder.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    // viewFinder.scaleX = -1F

    cameraExecutor = Executors.newSingleThreadExecutor()
  }

  private fun startCamera(){
    // ProcessCameraProvider use to bind camera lifecycle to any LifeCycleOwner within..
    // ..an application's process
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
        }

      // Build the imageCapture
      imageCapture = ImageCapture.Builder().build()

      // Build the faceDetection Preview
      val widthFinder = viewBinding.viewFinder.width
      val heightFinder = viewBinding.viewFinder.height

      Log.d(TAG, "Resolution viewFinder: $widthFinder x $heightFinder")

      // Select front camera as a default
      val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
      val viewFinder: PreviewView = viewBinding.viewFinder

      // If camera is front, then mirror the viewFinder
      if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA){
        viewFinder.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        viewFinder.scaleX = -1F
      }

      /** Create Google ML Kit ImageFrameAnalysis */
      val imageFrameAnalyzerML = ImageAnalysis.Builder()
        .setTargetResolution(Size( widthFinder, heightFinder ) )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also {
          it.setAnalyzer(cameraExecutor, GoogleFaceDetector(this, faceBounds, viewBinding){

          })
        }

      try {
        // Unbind use cases before rebinding. Check if something still binding in cameraProvider
        cameraProvider.unbindAll() // unbindAll() will close any camera that still used/open

        // Bind use cases to camera
        camera = cameraProvider.bindToLifecycle(
          this as LifecycleOwner, cameraSelector, preview, imageCapture, imageFrameAnalyzerML
          // Now we have 3 use case for the camera: preview, imageCapture, imageFrameAnalyzerML
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

  /** TF Lite */
  // If button is pressed, send true to TFModel and begin registering faces to DB
  private fun registerFace(){
    this.registerState = true
  }

  /** Spinner */
  override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
    Toasty.info(this@MainActivity, "${listRuang[p2]} is Selected", Toast.LENGTH_SHORT).show()
  }

  override fun onNothingSelected(p0: AdapterView<*>?) {
    TODO("Not yet implemented")
  }

  private fun getRuang(){
    val url = "http://192.168.100.7/connectToMySQL/getRuang.php"
    val queue = Volley.newRequestQueue(this)
    val request = requestGetRuang(url)
    queue.add(request) // add a request to the dispatch queue
  }

  private fun reloadSpinner(){
    // If conn = true, then spinner
    Log.e("RELOAD SPINNER", "listruang: ${listRuang[0]}${listRuang[1]}${listRuang[2]}${listRuang[3]}${listRuang[4]}")
    var arrayAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, listRuang)
    arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    viewBinding.bottomSheet.spnRuang.adapter = arrayAdapter
  }

  private fun requestGetRuang(url: String): JsonArrayRequest {
    val request = JsonArrayRequest(
      Request.Method.GET,
      url,
      null,
      {
        Log.e("VOLLEY", it.toString())
        Toasty.success(this@MainActivity, "${it.length()} Ruang Loaded", Toast.LENGTH_LONG).show()
        listRuang = arrayOfNulls(it.length())
        for (i in 0 until it.length()){
          listRuang[i] = it.getString(i)
        }
        Log.e("LIST RUANG", listRuang.toString())
        reloadSpinner()
      },
      {
        Log.e("VOLLEY", it.toString())
        Toasty.error(this@MainActivity, "Fail to get response = $it", Toast.LENGTH_LONG).show()
      }
    )
    return request
  }

  /** Testing Stuff */
  private fun showPopUpMaterial() {
    val context = this
    MaterialAlertDialogBuilder(context)
      .setTitle("Title Pop Up Material")
      .setMessage("Ini test pesan")
      .setNeutralButton("Cancel"){ dialog, which ->}
      .setPositiveButton("Ok"){ dialog, which ->}
      .show()
  }

  private fun takePhoto() {
    // Get a stable reference of the modifiable image capture use case
    val imageCapture = imageCapture ?: return
    // If imageCapture is null,
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

  /** Companion Object */
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

/** Create Use Cases that will be used in imageFrameAnalysis */
private class GoogleFaceDetector(
  var context: Context,
  private var faceBoundOverlay: FaceBoundOverlay,
  viewBinding: ActivityMainBinding,
  var listener: LabelListener,
) : ImageAnalysis.Analyzer {

  private val TAG = "GoogleFaceDetector"
  private val model = TFModel(context, listener, viewBinding)

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

/** --- How TensorFlow Lite Works in Android ---
1. Create TF interpreter => Used to load settings like optimization and model used
2. Create TF ImageProcessor => Used to process input before predicting. Resizing, Rotating, etc
3. Create Detector => To predict the image with the provided label
4. Run Detector
------------------------------------------------ */

/** Tensorflow image analysis goes here */
class TFModel(val context: Context,
                      private val listener: LabelListener,
                      val viewBinding: ActivityMainBinding
                      ) {
  /** Initialize var/val for the settings used in TF */
  private lateinit var bitmapBuffer: Bitmap
  private var imageRotationDegrees: Int = 0
  private var faceBounds = Rect()

  private var tfLiteInterp: Interpreter
  private var tfImageProcessor: ImageProcessor

  // Save personName and Distance in HashMap
  private var nameDistanceHash: HashMap<String, Array<FloatArray>> = HashMap()
  private var index: Int = 0
  private var indexReg: Int = 0
  private var indexUpdate: Int = 0

  private var outputs = Array(5) {FloatArray(128)} // We will store 10 images for 1 person
  private var registerState = false
  private var numFaceStored = 5
  private var hasilPrediction: Array<Any> = arrayOf("Unknown", 999F, 0)
  private var result: HashMap<String, String> = HashMap()
  private var query: HashMap<String, String> = HashMap()

  init {
    result["detected"] = "No"
    result["status"] = "Kosong"
    result["name"] = "Unknown"
    result["score"] = "999"
    result["id"] = "-1"

    // Setting up interpreter
    tfLiteInterp =
      Interpreter(
        FileUtil.loadMappedFile(context, MODEL_PATH),
        Interpreter.Options().addDelegate(NnApiDelegate()) // Boost performance using the available GPU, DSP, and or NPU => Android NNAPI frameworks
      )

    // Get inputSize that used by the TFLite Model. In this case, FaceNet using 160x160 while..
    // ..coco_ssd_mobilenet_v1_1.0_quant using 300x300
    val inputIndex = 0
    val inputShape = tfLiteInterp.getInputTensor(inputIndex).shape()
    val tfInputSize = Size(inputShape[2], inputShape[1]) // Order of axis is: {1, height, width, 3}

    // Create ImageProcessor to process the image before predicting
    tfImageProcessor =
      ImageProcessor.Builder()
        .add(
          ResizeOp(
            tfInputSize.height, tfInputSize.width, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR)
        )
        .add(Rot90Op(-imageRotationDegrees / 90))
        .add(NormalizeOp(160f, 160f)) // Default (0, 1) No computation will happen. Normalizes a TensorBuffer with given mean and stddev: output = (input - mean) / stddev.
        .build()

    try{
      val filename = "/FaceEmbeddings.c31"
      val filename2 = "/FaceEmbeddings V5 Zoom in.c31"
      val filename3 = "/FaceEmbeddings V5 No Zoom.c31"
      nameDistanceHash = loadHashInternal(context, filename2)
    } catch(e:IOException){
      Log.e(TAG, e.toString())
    } finally {
      Log.d(TAG, "File loaded successfully")
    }

    nameDistanceHash.forEach{ it ->
       Log.d(TAG, "Kunci ${it.key} = ")
      it.value.forEach {
         Log.d(TAG, "128-D Values = ${it[0]}, ${it[1]}, ${it[2]}, ..., ${it[127]}")

        var lowest = 10F
        var highest = -10F
        it.forEach {
          if(it < lowest){
            lowest = it
          } else if (it > highest){
            highest = it
          }
        }
         Log.d(TAG, "Lowest Value = $lowest | Highest Value = $highest")
      }
    }

    // Button viewBinding
    val kotakNama = viewBinding.bottomSheet.editTextNama
    val idname = viewBinding.bottomSheet.etId
    val pass = viewBinding.bottomSheet.etPass
    val loginbtn = viewBinding.bottomSheet.btnLogin

    loginbtn.setOnClickListener {
      // call function to login and update status to "Hadir"
      val id = idname.text.toString()
      val pass = pass.text.toString()
      updateDataWithPass(id, "Hadir", pass)
    }

    viewBinding.bottomSheet.captureButton.setOnClickListener {
      registerState = true
    }

    viewBinding.bottomSheet.btnReport.setOnClickListener {
      val nama = kotakNama.text.toString()
      insertData(nama, "n@presensi.com", "n", "0811")
    }
    viewBinding.bottomSheet.btnUpdate.setOnClickListener{
      val id = kotakNama.text.toString()
      updateData(id, "Hadir") // Belum Presensi
    }
    viewBinding.bottomSheet.btnGet.setOnClickListener {
      val nama = kotakNama.text.toString()
      getID(nama)
    }
  }

  // Reset query and result when face is not detected
  fun resetQuery(){
    index = 0
    indexUpdate = 0
    
    Log.d("RESET", "Resetting query and result")

    if(query.size > 0 || result.size > 0){
      Log.d("RESET", "Query = $query")
      Log.d("RESET", "Result = $result")

      query = HashMap()
      result = HashMap()

      Log.d("RESET", "Query = $query")
      Log.d("RESET", "Result = $result")
      Log.d("RESET", "Complete")

      hasilPrediction = arrayOf("Unknown", 999F, 0)
    }

    viewBinding.videoCaptureButton.text = "Looking.."
    viewBinding.btnStatus.text = "Jangan lupa lepaskan masker"
  }

  /** Send data to mySQL server */
  // https://www.androidhire.com/insert-data-from-app-to-mysql-android/
  private fun insertData(name: String, email: String, password: String, noHP: String){
    val url = "http://192.168.100.7/connectToMySQL/add.php"
    val queue = Volley.newRequestQueue(context)
    val parameters: MutableMap<String, String> = HashMap()
    parameters["namaP"] = name
    parameters["passP"] = password
    parameters["emailP"] = email
    parameters["noHpP"] = noHP

    val request = requestPost(url, parameters)

    queue.add(request) // add a request to the dispatch queue
  }

  private fun updateData(id: String, status: String){
    val url = "http://192.168.100.7/connectToMySQL/update.php"
    val queue = Volley.newRequestQueue(context)
    val parameters: MutableMap<String, String> = HashMap()
    parameters["idP"] = id
    parameters["statusP"] = status

    val request = requestPost(url, parameters)

    queue.add(request)
  }

  private fun updateDataWithPass(id: String, status: String, pass: String){
    val url = "http://192.168.100.7/connectToMySQL/updatewithPass.php"
    val queue = Volley.newRequestQueue(context)
    val parameters: MutableMap<String, String> = HashMap()
    parameters["idP"] = id
    parameters["statusP"] = status
    parameters["passP"] = pass

    val request = requestPost(url, parameters)

    queue.add(request)
  }

  private fun getID(nama: String){
    val url = "http://192.168.100.7/connectToMySQL/getID.php"
    val queue = Volley.newRequestQueue(context)
    val parameters: MutableMap<String, String> = HashMap()
    parameters["namaP"] = nama

    val request = requestGetID(url, parameters)

    queue.add(request)
  }

  private fun get(id: String, kolom: String){
    val url = "http://192.168.100.7/connectToMySQL/get.php"
    val queue = Volley.newRequestQueue(context)
    val parameters: MutableMap<String, String> = HashMap()
    parameters["idP"] = id
    parameters["rowP"] = kolom

    val request = requestGet(url, parameters)

    queue.add(request)
  }

  // https://stackoverflow.com/questions/19837820/volley-jsonobjectrequest-post-request-not-working
  private fun requestGetID(url: String, parameters: MutableMap<String, String>): StringRequest {
    // Use 'object :' to override function inside StringRequest()
    val request = object: StringRequest(
      Method.POST,
      url,
      Response.Listener {
        Log.e("VOLLEY", it.toString())
        query["id"] = it.toString()
      },
      Response.ErrorListener {
        Toasty.error(context, "Fail to get response = $it", Toast.LENGTH_SHORT).show()
      }){
      override fun getParams(): MutableMap<String, String>? {
        return parameters
      }
    }

    return request
  }

  private fun requestGet(url: String, parameters: MutableMap<String, String>): StringRequest {
    // Use 'object :' to override function inside StringRequest()
    val request = object: StringRequest(
      Method.POST,
      url,
      Response.Listener {
        Log.e("VOLLEY", it.toString())
//        Toast.makeText(context, "Query Status = $it", Toast.LENGTH_LONG).show()

        parameters.forEach { (_, kolom) ->
          if(kolom == "status"){
            query[kolom] = it
          }
          if(kolom == "nama"){
            query[kolom] = it
          }
        }
      },
      Response.ErrorListener {
        Toasty.error(context, "Fail to get response = $it", Toast.LENGTH_SHORT).show()
      }){
      override fun getParams(): MutableMap<String, String>? {
        return parameters
      }
    }

    return request
  }

  private fun requestPost(url: String, parameters: MutableMap<String, String>): StringRequest{
    // Use 'object :' to override function inside StringRequest()
    val request = object : StringRequest(
      Method.POST,
      url,
      Response.Listener {
        Log.e("REGISTER", it)
        try{
          val message = JSONObject(it)
          Toasty.info(context, message.getString("message")).show()
          Log.d("POST", message.getString("message"))
        } catch (e: JSONException){
          e.printStackTrace()
        }},
      // https://stackoverflow.com/questions/45940861/android-8-cleartext-http-traffic-not-permitted
      Response.ErrorListener {
        Toasty.error(context, "Fail to get response = $it", Toast.LENGTH_SHORT).show()
      }){
      override fun getParams(): MutableMap<String, String>? {
        return parameters
      }
    }
    return request
  }
  /** END OF Sending to MySQL */

  fun setFaceBound(FaceRect: Rect){
    faceBounds = FaceRect
  }

  private fun bitmapToBuffer(image: Bitmap): ByteBuffer {
    return tfImageProcessor.process(TensorImage.fromBitmap(image)).buffer
  }

  fun analyze(image: ImageProxy, faces: Int): HashMap<String, String> {
    // Comparing faces using L2 Norm
    // Only compare if index is incrementing
    // L2 Norm = sqrt( SumEach( values^2 ) )

    /** Run every 5 or 10 clock to save resources */
    if((index+1) % 20 == 0){
      bitmapBuffer = BitmapUtils.getBitmap(image)!!
//      BitmapUtils.saveBitmap(context, bitmapBuffer, "BitmapTF.png")

      bitmapBuffer = BitmapUtils.cropBitmap(bitmapBuffer, faceBounds)
//      BitmapUtils.saveBitmap(context, bitmapBuffer, "BitmapCropTF.png")

      // outputBuffer should be an array of FloatArray
      // Create an array with 1 size, and fill it with 128 FloatArray
      // outputBuffer[0] = [128 FloatArray]
      var tfImageBuffer = bitmapToBuffer(bitmapBuffer)
      var outputBuffer = Array(1) {FloatArray(128)}
      tfLiteInterp.run(tfImageBuffer, outputBuffer)

      // Save the embed to storage
      // Only Register if the button is pressed
      var name = viewBinding.bottomSheet.editTextNama.text.toString()

      if(registerState){
        registerEmbed(outputBuffer, name)
        registerState = false
      }

      hasilPrediction = L2Norm(outputBuffer)

      // Get result and save
      val (predikNama, predikScore, pictureID) = hasilPrediction
      result["score"] = predikScore.toString()

      if(predikScore as Float <= ACCURACY_THRESHOLD){
        result["detected"] = "Yes"
        result["name"] = predikNama.toString()

        // getID from name
        getID(result["name"]!!)

        Log.d("RESULT", result.toString())
        Log.d("QUERY", query.toString())
        if(query["id"] != null){
          get(query["id"]!!, "status")
          result["id"] = query["id"]!!

          if(query["status"] != null){
            result["status"] = query["status"]!!

            viewBinding.videoCaptureButton.text = result["name"] + " (" + query["id"] + ")"
            viewBinding.btnStatus.text = query["status"]

            if(query["status"] == "Belum Presensi"){
              viewBinding.btnStatus.setBackgroundColor(Color.parseColor("#74B4FF"))
              indexUpdate += 1
            } else if(query["status"] == "Hadir"){
              viewBinding.btnStatus.setBackgroundColor(Color.parseColor("#FF0000"))
            }
            else {
              viewBinding.btnStatus.text = "Processing..."
            }
          }
        }
      }
    }

    // Jika dikenali 3 kali, maka update status menjadi hadir
    if(indexUpdate == 3){
      updateData(query["id"]!!, "Hadir")
      Toasty.info(context, result["name"] + " berhasil melakukan presensi", Toast.LENGTH_LONG).show()
      indexUpdate = 0
    }

    viewBinding.bottomSheet.btnGet.text = query["status"]
    viewBinding.bottomSheet.btnUpdate.text = "ID: " + query["id"]
    viewBinding.bottomSheet.btnReport.text = result["name"]

    // Debug Listener
    var iniLog = "Predicting: ${result["name"]} [${result["score"]}] | Face Registered: ${nameDistanceHash.entries.size} " +
        "| IndexReg: $indexReg | IndexUpdate: $indexUpdate"
    //    listener(iniLog)

    viewBinding.bottomSheet.tvView.text = iniLog
    viewBinding.bottomSheet.tvClock.text = "Clock: $index"

    if(index >= 120){
      index = 0
    } else {
      index += 1;
    }

    return result
  }

  // Create L2Norm to find distances
  fun L2Norm(data: Array<FloatArray>): Array<Any>{
    Log.d(TAG, "Calculating Distance START >>>>>>>>>>>>>>>>>>>>")
    var distance = Float.MAX_VALUE
    var distanceT = 0.0F
    val valueF = data[0]
    var nama = "Unknown"
    var pictureUsed = 0

    // Loop to all embeds in knownEmbed
    nameDistanceHash.forEach{ // People.size Loops
      // Log.d(TAG, "Now comparing with ${it.key} ${it.value.size}")
      // Loop to all values in 128-D embeds, and find L2 Norm on the difference between knownEmbed..
      // ..and currentEmbed
      val currentName = it.key
      var idx = 1
      it.value.forEach {
        for (i in 0 until valueF.size-1){ //128-D Loops
          // Log.d(TAG, "valueF.get(i) - it.value[0][i]: ${valueF.get(i)} - ${it.value[0][i]}")
          val diff = valueF[i] - it[i]
          distanceT +=  diff * diff
        }
        distanceT = sqrt(distanceT)

        if(distance>distanceT){
          distance = distanceT
          nama = currentName
          pictureUsed = idx
        }
        idx += 1
        // Log.d(TAG, "Cur.Low.dist.: ${distance} | distanceTo ${currentName}: ${distanceT} | Est: ${nama}")
      }
    }
    Log.d(TAG, "Calculating Distance FINISHED >>>>>>>>>>>>>>>>>>>>")
    return arrayOf(nama, distance, pictureUsed)
  }

  fun saveHashInternal(context: Context, data: HashMap<String, Array<FloatArray>>){
    try {
      val fos: FileOutputStream =
        context.openFileOutput("FaceEmbeddings.c31", Context.MODE_PRIVATE)
      val oos = ObjectOutputStream(fos)
      oos.writeObject(data)
      oos.close()
    } catch (e: IOException) {
      e.printStackTrace()
    }
  }

  fun loadHashInternal(context: Context, filename:String): HashMap<String, Array<FloatArray>>{
    var myHashMap: HashMap<String, Array<FloatArray>> = HashMap()
    try {
      val fileInputStream = FileInputStream(context.filesDir.toString() + filename)
      val objectInputStream = ObjectInputStream(fileInputStream)
      myHashMap = objectInputStream.readObject() as HashMap<String, Array<FloatArray>>
//      Toast.makeText(context, "File Loaded Succesfully", Toast.LENGTH_SHORT).show()
      Toasty.success(context, "File Loaded Successfully", Toast.LENGTH_SHORT).show()
    } catch (e: IOException) {
      Toast.makeText(context, "Empty embed, please register new faces", Toast.LENGTH_SHORT).show()
      e.printStackTrace()
    }
    return myHashMap
  }

  fun registerEmbed(outputBuffer: Array<FloatArray>, name: String){
    val name = name

    // Save embedding faces in hashmap. "Bill Gates" will have multiple embedding in the future,..
    // ..so we will save it in Array(FloatArray)
    if(indexReg < numFaceStored){
      outputs[indexReg] = outputBuffer[0]
      nameDistanceHash[name] = outputs /** Register the embedding for $name */
      indexReg += 1

      if(indexReg == numFaceStored){
        viewBinding.bottomSheet.captureButton.text = "Save to DB"
      }
    } else if (indexReg == numFaceStored){
      saveHashInternal(context, nameDistanceHash)
      indexReg = 0

      // Clear all values in outputs
      outputs = Array(5){FloatArray(128)}

      viewBinding.bottomSheet.captureButton.text = "Capture Face"
      Toast.makeText(context, "$name is saved", Toast.LENGTH_SHORT).show()
    }
  }

  companion object{
    // Define the settings for the model used in TF
    private const val TAG = "TF Class"
    private const val ACCURACY_THRESHOLD = 10f
    private const val MODEL_PATH = "facenet.tflite"
  }
}

/** Creating RectOverlay to create a custom view */
class FaceBoundOverlay constructor(context: Context?,
                                   attributeSet: AttributeSet?
) : View(context, attributeSet) {
  val TAG = "FaceBoundOverlay"
  val faceBounds: MutableList<RectF> = mutableListOf()
  var prediction: HashMap<String, String> = HashMap()

  private val redPaint = Paint().also{
    it.style = Paint.Style.STROKE
    it.color = Color.RED
    it.strokeWidth = 10f
  }

  private val greenPaint = Paint().also{
    it.style = Paint.Style.STROKE
    it.color = Color.GREEN
    it.strokeWidth = 10f
  }

  private val orangePaint = Paint().also{
    it.style = Paint.Style.STROKE
    it.color = Color.YELLOW
    it.strokeWidth = 10f
  }

  fun clear(){
    this.faceBounds.clear()
    invalidate()
  }

  // Override onDraw() to paint some UI above Camera Preview
  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    // Pass it a list of RectF (rectBounds)
    faceBounds.forEach {
      val height = height
      val width = width

      if(it.width() < 1){
        it.left = it.left * width
        it.right = it.right * width
        it.top = it.top * height
        it.bottom = it.bottom * height
      }
      if(prediction["name"] == "Unknown"){
        canvas.drawRoundRect(it, 16F, 16F, redPaint)
      } else if(prediction["status"] == "Hadir") {
        canvas.drawRoundRect(it, 16F, 16F, greenPaint)
      } else if(prediction["status"] == "Belum Presensi"){
        canvas.drawRoundRect(it, 16F, 16F, orangePaint)
      } else {
        canvas.drawRoundRect(it, 16F, 16F, redPaint)
      }

      // Log.d(TAG, "Draw the bounding box of : ${it.toString()} Surface Res = ${width} x ${height}")
    }

    /** Draw a debug Rect */
    // val valuerectF = RectF(20F, 10F, 50F, 50F)
    // canvas.drawRoundRect(valuerectF, 16F, 16F, customPaint)
  }

  fun drawFaceBounds(faceBounds: List<RectF>, detected: HashMap<String, String>){
    this.faceBounds.clear()
    this.faceBounds.addAll(faceBounds)
    this.prediction = detected

    invalidate()
    // Invalid to re-draw the canvas
    // Method onDraw will be called with new data.
  }
}

