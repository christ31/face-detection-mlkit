package com.android.example.SKRpresensi

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
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
import com.google.gson.Gson
import es.dmoral.toasty.Toasty
import org.json.JSONException
import org.json.JSONObject
import java.io.FileInputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
    val showReportSheet = viewBinding.bottomSheet.btnShowLapor
    val radioButton = viewBinding.reportSheet.RGoup

    // Bottom Sheet
    val reportbottombehavior = BottomSheetBehavior.from(viewBinding.reportSheet.standardReportSheet)
    val feedbackbottombehaviour = BottomSheetBehavior.from(viewBinding.feedbackSheet.standardFeedbackSheet)
    val feedbackOpsi1 = viewBinding.feedbackSheet.checkBox
    val feedbackOpsi2 = viewBinding.feedbackSheet.checkBox2
    val feedbackOpsi3 = viewBinding.feedbackSheet.checkBox3

    feedbackbottombehaviour.state = BottomSheetBehavior.STATE_HIDDEN

    // Buat rating
    val tvRating = viewBinding.feedbackSheet.tvRatingDesc
    val feedbackRating1 = viewBinding.feedbackSheet.btnFeed1
    val feedbackRating2 = viewBinding.feedbackSheet.btnFeed2
    val feedbackRating3 = viewBinding.feedbackSheet.btnFeed3
    val feedbackRating4 = viewBinding.feedbackSheet.btnFeed4
    val feedbackRating5 = viewBinding.feedbackSheet.btnFeed5
    val submitFeddback = viewBinding.feedbackSheet.btnSubmitFeedback
    var rating = 0
    var feedbackUser = ""
    tvRating.visibility = INVISIBLE


    feedbackRating1.setOnClickListener {
      feedbackRating1.background = getDrawable(R.drawable.ic_baseline_star_24)
      feedbackRating2.background = getDrawable(R.drawable.ic_baseline_star_outline_24)
      feedbackRating3.background = getDrawable(R.drawable.ic_baseline_star_outline_24)
      feedbackRating4.background = getDrawable(R.drawable.ic_baseline_star_outline_24)
      feedbackRating5.background = getDrawable(R.drawable.ic_baseline_star_outline_24)

      feedbackOpsi1.text = getString(R.string.fb_salah1)
      feedbackOpsi2.text = getString(R.string.fb_salah2)
      feedbackOpsi3.text = getString(R.string.fb_salah3)

      rating = 1
      feedbackUser = "Tidak puas karena: "

      tvRating.visibility = VISIBLE
      tvRating.text = feedbackUser
    }

    feedbackRating2.setOnClickListener {
      feedbackRating1.background = getDrawable(R.drawable.ic_baseline_star_24)
      feedbackRating2.background = getDrawable(R.drawable.ic_baseline_star_24)
      feedbackRating3.background = getDrawable(R.drawable.ic_baseline_star_outline_24)
      feedbackRating4.background = getDrawable(R.drawable.ic_baseline_star_outline_24)
      feedbackRating5.background = getDrawable(R.drawable.ic_baseline_star_outline_24)

      feedbackOpsi1.text = getString(R.string.fb_salah1)
      feedbackOpsi2.text = getString(R.string.fb_salah2)
      feedbackOpsi3.text = getString(R.string.fb_salah3)

      rating = 2
      feedbackUser = "Tidak puas karena: "

      tvRating.visibility = VISIBLE
      tvRating.text = feedbackUser
    }

    feedbackRating3.setOnClickListener {
      feedbackRating1.background = getDrawable(R.drawable.ic_baseline_star_24)
      feedbackRating2.background = getDrawable(R.drawable.ic_baseline_star_24)
      feedbackRating3.background = getDrawable(R.drawable.ic_baseline_star_24)
      feedbackRating4.background = getDrawable(R.drawable.ic_baseline_star_outline_24)
      feedbackRating5.background = getDrawable(R.drawable.ic_baseline_star_outline_24)

      feedbackOpsi1.text = getString(R.string.fb_salah1)
      feedbackOpsi2.text = getString(R.string.fb_salah2)
      feedbackOpsi3.text = getString(R.string.fb_salah3)

      rating = 3
      feedbackUser = "Tidak puas karena: "

      tvRating.visibility = VISIBLE
      tvRating.text = feedbackUser
    }

    feedbackRating4.setOnClickListener {
      feedbackRating1.background = getDrawable(R.drawable.ic_baseline_star_24)
      feedbackRating2.background = getDrawable(R.drawable.ic_baseline_star_24)
      feedbackRating3.background = getDrawable(R.drawable.ic_baseline_star_24)
      feedbackRating4.background = getDrawable(R.drawable.ic_baseline_star_24)
      feedbackRating5.background = getDrawable(R.drawable.ic_baseline_star_outline_24)

      feedbackOpsi1.text = getString(R.string.fb_improve1)
      feedbackOpsi2.text = getString(R.string.fb_improve2)
      feedbackOpsi3.text = getString(R.string.fb_improve3)

      rating = 4
      feedbackUser = "Perlu peningkatan pada: "

      tvRating.visibility = VISIBLE
      tvRating.text = feedbackUser
    }

    feedbackRating5.setOnClickListener {
      feedbackRating1.background = getDrawable(R.drawable.ic_baseline_star_24)
      feedbackRating2.background = getDrawable(R.drawable.ic_baseline_star_24)
      feedbackRating3.background = getDrawable(R.drawable.ic_baseline_star_24)
      feedbackRating4.background = getDrawable(R.drawable.ic_baseline_star_24)
      feedbackRating5.background = getDrawable(R.drawable.ic_baseline_star_24)

      feedbackOpsi1.text = getString(R.string.fb_opsi1)
      feedbackOpsi2.text = getString(R.string.fb_opsi2)
      feedbackOpsi3.text = getString(R.string.fb_opsi3)

      rating = 5
      feedbackUser = "Sangat puas karena: "

      tvRating.visibility = VISIBLE
      tvRating.text = feedbackUser
    }

    submitFeddback.setOnClickListener {
      if(feedbackOpsi1.isChecked){
        feedbackUser += "${feedbackOpsi1.text} | "
      }
      if(feedbackOpsi2.isChecked){
        feedbackUser += "${feedbackOpsi2.text} | "
      }
      if(feedbackOpsi3.isChecked){
        feedbackUser += "${feedbackOpsi3.text} | "
      }

      kirimFeedback(feedbackUser, rating)
      feedbackbottombehaviour.state = BottomSheetBehavior.STATE_HIDDEN
    }

    // For Button
    captureButton.setOnClickListener{takePhoto()}
    popupButton.setOnClickListener{showPopUpMaterial()}
    showReportSheet.setOnClickListener{showBottomSheet(feedbackbottombehaviour)}
    reportBtn.setOnClickListener { laporSekarang() }

    // Testing
    Log.d("MAC", getDeviceName().toString())

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

      // Select back camera as a default
      val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
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
          it.setAnalyzer(cameraExecutor, GoogleFaceDetector(this, faceBounds, viewBinding){})
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

  /** Kirim Feedback ke DB */
  private fun kirimFeedback(Feed: String, Rating: Int) {
    insertFeedback(Feed, getSecureAndroidID(), Rating)
  }

  fun getRandomString() : String {
    val sdf = SimpleDateFormat("yyyyMMddhhmmss")
    val currentDate = sdf.format(Date())

    return(currentDate)
  }

  private fun insertLaporan(ref: String,  jenis: String){
    val url = "https://c31.website/addLaporan.php"
    val queue = Volley.newRequestQueue(this)
    val parameters: MutableMap<String, String> = HashMap()
    parameters["refP"] = "#$ref"
    parameters["jenisLaporanP"] = jenis

    val request = requestPost(url, parameters)

    queue.add(request) // add a request to the dispatch queue
  }

  private fun insertFeedback(Feed: String,  DeviceID: String, Rating: Int){
    val url = "https://c31.website/addFeedback.php"
    val queue = Volley.newRequestQueue(this)
    val parameters: MutableMap<String, String> = HashMap()

    parameters["keteranganP"] = Feed
    parameters["deviceP"] = DeviceID
    parameters["ratingP"] = Rating.toString()

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
        val feedbackBottom = BottomSheetBehavior.from(viewBinding.feedbackSheet.standardFeedbackSheet)
        if(feedbackBottom.state == BottomSheetBehavior.STATE_EXPANDED){
          feedbackBottom.state = BottomSheetBehavior.STATE_HIDDEN
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
    val url = "https://c31.website/getRuang.php" //New Migrate
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
//        var listRuang2 = arrayOfNulls<String>(it.length())
//
//
//        val arrayJson = JSONArray(it)
//
//        for (i in 0 until arrayJson.length()){
//          var objectJson = arrayJson.getJSONObject(i)
//          listRuang2 = arrayOf(objectJson.getString("locId"))
//        }


        for (i in 0 until it.length()){
          listRuang[i] = it.getString(i)
        }
//        Log.e("LIST RUANG | ", listRuang2.toString())
        Log.e("IT | ", it.toString())

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
  // Get device MAC Address
  fun getMac(context: Context): String {
    val manager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val info = manager.connectionInfo
    return info.macAddress.toUpperCase()
  }

  /** Returns the consumer friendly device name  */
  fun getDeviceName(): String? {
    val manufacturer = Build.MANUFACTURER
    val model = Build.MODEL
    return if (model.startsWith(manufacturer)) {
      capitalize(model)
    } else capitalize(manufacturer) + " " + model
  }

  /** Get Secure Android ID */
  // https://developer.android.com/reference/android/provider/Settings.Secure#ANDROID_ID
  fun getSecureAndroidID(): String {
    val stringAndroidID: String = Settings.Secure.getString(
      this.contentResolver,
      Settings.Secure.ANDROID_ID
    )

    Log.e("ANDROIDID", stringAndroidID)
    return stringAndroidID
  }

  private fun capitalize(str: String): String {
    if (TextUtils.isEmpty(str)) {
      return str
    }
    val arr = str.toCharArray()
    var capitalizeNext = true
    val phrase = StringBuilder()
    for (c in arr) {
      if (capitalizeNext && Character.isLetter(c)) {
        phrase.append(c.uppercaseChar())
        capitalizeNext = false
        continue
      } else if (Character.isWhitespace(c)) {
        capitalizeNext = true
      }
      phrase.append(c)
    }
    return phrase.toString()
  }

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

  private fun showBottomSheet(it: BottomSheetBehavior<LinearLayout>) {
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

  fun uploadEmbedToDBfromFile(){
    // Baca file embed
    val embedding = loadHashInternal(this, "/FaceEmbeddings V5 Zoom in.c31")
    val gson = Gson()

    val embedString = arrayOfNulls<String>(5)

    // Ambil semua value dengan loop
    var index = 0
    var embed = "embed0"
    embedding.forEach { peoplename, arrayOfFloatArrays ->
      var floatArraytoJson = gson.toJson(arrayOfFloatArrays)

      embedString[index]=floatArraytoJson
      // Update ke DB as JSON Object
      embed = embed + index
      updateJsonData(peoplename, floatArraytoJson, embed)
      index += 1
      Log.e("GSON JSON", "JSON dari $peoplename = $floatArraytoJson")
    }
  }

  private fun updateJsonData(nama: String, embed: String, kolom: String){
    val url = "https://c31.website/updateEmbedFromFile.php"
    val queue = Volley.newRequestQueue(this)
    val parameters: MutableMap<String, String> = HashMap()
    parameters["nama"] = nama
    parameters["embedJson"] = embed
    parameters["column"] = kolom

    val request = requestPost2(url, parameters)

    queue.add(request)
  }

  private fun requestPost2(url: String, parameters: MutableMap<String, String>): StringRequest {
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

  private fun getID(nama: String){
    val url = "https://c31.website/getID.php"
    val queue = Volley.newRequestQueue(this)
    val parameters: MutableMap<String, String> = HashMap()
    parameters["namaP"] = nama

    val request = requestGetID(url, parameters)

    queue.add(request)
  }

  private fun requestGetID(url: String, parameters: MutableMap<String, String>): StringRequest {
    // Use 'object :' to override function inside StringRequest()
    val request = object: StringRequest(
      Method.POST,
      url,
      Response.Listener {

      },
      Response.ErrorListener {
        Toasty.error(this, "Fail to get response = $it", Toast.LENGTH_SHORT).show()
      }){
      override fun getParams(): MutableMap<String, String>? {
        return parameters
      }
    }

    return request
  }

  fun loadHashInternal(context: Context, filename:String): HashMap<String, Array<FloatArray>>{
    var myHashMap: HashMap<String, Array<FloatArray>> = HashMap()
    try {
      val fileInputStream = FileInputStream(context.filesDir.toString() + filename)
      val objectInputStream = ObjectInputStream(fileInputStream)
      myHashMap = objectInputStream.readObject() as HashMap<String, Array<FloatArray>>
      Toasty.success(context, "File Loaded Successfully", Toast.LENGTH_SHORT).show()
    } catch (e: IOException) {
      Toast.makeText(context, "Empty embed, please register new faces", Toast.LENGTH_SHORT).show()
      e.printStackTrace()
    }
    return myHashMap
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
      } else if(prediction["status"] == "1") {
        canvas.drawRoundRect(it, 16F, 16F, greenPaint)
      } else if(prediction["status"] == "0"){
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

