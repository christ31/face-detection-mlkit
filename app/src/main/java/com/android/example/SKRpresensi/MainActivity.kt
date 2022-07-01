package com.android.example.SKRpresensi

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.lifecycle.LifecycleOwner
import com.android.example.SKRpresensi.databinding.ActivityMainBinding
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import es.dmoral.toasty.Toasty
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.HashMap

typealias LabelListener = (String) -> Unit

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {
  private lateinit var viewBinding: ActivityMainBinding
  private var imageCapture: ImageCapture? = null
  private lateinit var cameraExecutor: ExecutorService
  private lateinit var camera: Camera

  private var listRuang = arrayOfNulls<String>(5)
  var jenisLaporan = "Default"

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
    val dropdownRuang: Spinner = viewBinding.bottomSheet.spnRuang
    val topBar = viewBinding.topAppBar
    val reportBtn: Button = viewBinding.reportSheet.btnLapor
    val showReportSheet = viewBinding.btnShowLapor
    val reportbottombehavior = BottomSheetBehavior.from(viewBinding.reportSheet.standardReportSheet)
    val radioButton = viewBinding.reportSheet.RGoup

    // For Button
    captureButton.setOnClickListener{takePhoto()}
    popupButton.setOnClickListener{showPopUpMaterial()}
    showReportSheet.setOnClickListener{showReportSheet(reportbottombehavior)}
    reportBtn.setOnClickListener { laporSekarang() }

    // Bottom Sheet
    val adminbottombehavior = BottomSheetBehavior.from(viewBinding.bottomSheet.standardBottomSheet)
    adminbottombehavior.state = BottomSheetBehavior.STATE_HIDDEN

    val loginbottombehavior = BottomSheetBehavior.from(viewBinding.loginSheet.standardLoginSheet)
    loginbottombehavior.state = BottomSheetBehavior.STATE_HIDDEN

    reportbottombehavior.state = BottomSheetBehavior.STATE_HIDDEN
    radioButton.check(R.id.rb1)

    // Top Bar
    topBar.setOnMenuItemClickListener { menuItem ->
      when (menuItem.itemId){
        R.id.adminItem -> {
          if(adminbottombehavior.state == BottomSheetBehavior.STATE_EXPANDED){
            adminbottombehavior.state = BottomSheetBehavior.STATE_HIDDEN
          } else {
            adminbottombehavior.state = BottomSheetBehavior.STATE_EXPANDED
          }
          true
        }
        else -> false
      }
    }

    // Check for connection to DB
    getRuang()

    // If conn = true, then spinner
    var listofitems = arrayOf("Item 1", "Item 2", "Item 3")
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

  /** Lapor masalah ke DB */
  private fun laporSekarang() {
    val opsi1 = viewBinding.reportSheet.rb1
    val opsi2 = viewBinding.reportSheet.rb2

    if (opsi1.isChecked){
      jenisLaporan = opsi1.text.toString()
    } else if (opsi2.isChecked){
      jenisLaporan = opsi2.text.toString()
    }

    var ref = getRandomString()
    insertLaporan(ref, jenisLaporan)
    showCodeRef(ref)
  }

  fun getRandomString() : String {
    val sdf = SimpleDateFormat("yyyyMMddhhmmss")
    val currentDate = sdf.format(Date())

    return(currentDate)
  }

  private fun insertLaporan(ref: String,  jenis: String){
    val url = "http://192.168.100.7/connectToMySQL/addLaporan.php"
    val queue = Volley.newRequestQueue(this)
    val parameters: MutableMap<String, String> = HashMap()
    parameters["refP"] = "#$ref"
    parameters["jenisLaporanP"] = jenis

    val request = requestPost(url, parameters)

    queue.add(request) // add a request to the dispatch queue
  }

  private fun requestPost(url: String, parameters: MutableMap<String, String>): StringRequest {
    // Use 'object :' to override function inside StringRequest()
    val request = object : StringRequest(
      Method.POST,
      url,
      Response.Listener {
        Log.e("REGISTER", it)
        try{
          val message = JSONObject(it)
          Toasty.info(this, message.getString("message")).show()
          Log.d("POST", message.getString("message"))
          val reportbottombehavior = BottomSheetBehavior.from(viewBinding.reportSheet.standardReportSheet)
          if(reportbottombehavior.state == BottomSheetBehavior.STATE_EXPANDED){
            reportbottombehavior.state = BottomSheetBehavior.STATE_HIDDEN
          }
        } catch (e: JSONException){
          e.printStackTrace()
        }},
      // https://stackoverflow.com/questions/45940861/android-8-cleartext-http-traffic-not-permitted
      Response.ErrorListener {
        Toasty.error(this, "Fail to get response = $it", Toast.LENGTH_SHORT).show()
      }){
      override fun getParams(): MutableMap<String, String>? {
        return parameters
      }
    }
    return request
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

  private fun showCodeRef(ref: String) {
    val context = this
    MaterialAlertDialogBuilder(context)
      .setTitle("PERHATIAN! Catat kode ini untuk melakukan presensi di Website Presensi")
      .setMessage("$ref")
      .setPositiveButton("Ok"){ dialog, which ->}
      .show()
  }

  private fun showReportSheet(it: BottomSheetBehavior<LinearLayout>) {
    it.state = BottomSheetBehavior.STATE_EXPANDED
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

