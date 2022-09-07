package com.android.example.SKRpresensi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.camera.core.ImageProxy
import com.android.example.SKRpresensi.databinding.ActivityMainBinding
import com.android.example.SKRpresensi.libraryFromGoogle.BitmapUtils
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.bottomsheet.BottomSheetBehavior
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
import kotlin.math.sqrt


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
  private var indextidakdikenal: Int = 0
  private var terdaftar: Int = 0 // Digunakan untuk update kembali jika peserta sudah presensi dan keluar

  private var outputs = Array(5) {FloatArray(128)} // We will store 10 images for 1 person
  private var registerState = false
  private var numFaceStored = 5
  private var hasilPrediction: Array<Any> = arrayOf("Unknown", 999F, 0)
  private var result: HashMap<String, String> = HashMap()
  private var query: HashMap<String, String> = HashMap()

  val loginbottombehavior = BottomSheetBehavior.from(viewBinding.loginSheet.standardLoginSheet)

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

    Log.e("TFLITEInput", tfInputSize.toString())

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
      nameDistanceHash = loadHashInternal(context, filename)
    } catch(e: IOException){
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
    val idname = viewBinding.loginSheet.etId
    val pass = viewBinding.loginSheet.etPass
    val loginbtn = viewBinding.loginSheet.btnLogin
    val tombol_in = viewBinding.btnIn
    val tombol_out = viewBinding.btnOut

    tombol_in.setOnClickListener {
      terdaftar = 0
      tombol_in.setBackgroundColor(Color.parseColor("#0F766E"))

      tombol_out.setBackgroundColor(Color.parseColor("#575757"))
    }

    tombol_out.setOnClickListener {
      terdaftar = 1
      tombol_out.setBackgroundColor(Color.parseColor("#0F766E"))

      tombol_in.setBackgroundColor(Color.parseColor("#575757"))
    }

    loginbtn.setOnClickListener {
      // call function to login and update status to "Hadir"
      val id = idname.text.toString()
      val pass = pass.text.toString()
      updateDataWithPass(id, "Hadir", pass)
    }

    viewBinding.bottomSheet.captureButton.setOnClickListener {
      registerState = true
    }
    viewBinding.bottomSheet.btnAddUser.setOnClickListener {
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

    val feedbackbottombehaviour = BottomSheetBehavior.from(viewBinding.feedbackSheet.standardFeedbackSheet)
    feedbackbottombehaviour.state = BottomSheetBehavior.STATE_HIDDEN

    val notRecognizedbottombehaviour = BottomSheetBehavior.from(viewBinding.notrecognizedSheet.standardNotrecognizedSheet)
    notRecognizedbottombehaviour.state = BottomSheetBehavior.STATE_HIDDEN


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
    viewBinding.btnStatus.setBackgroundColor(Color.parseColor("#FF5722"))
  }

  /** Send data to mySQL server */
  // https://www.androidhire.com/insert-data-from-app-to-mysql-android/
  private fun insertData(name: String, email: String, password: String, noHP: String){
    val url = "https://c31.website//add.php"
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
    val url = "https://c31.website/update.php"
    val queue = Volley.newRequestQueue(context)
    val parameters: MutableMap<String, String> = HashMap()
    parameters["idP"] = id.toString()
    parameters["statusP"] = status
    parameters["noHpP"] = getSecureAndroidID()

    Log.e("UpdateToDB", "parameters = $id dan $status")

    val request = requestPost(url, parameters)

    queue.add(request)
  }

  private fun updateDataWithPass(id: String, status: String, pass: String){
    val url = "https://c31.website/updatewithPass.php"
    val queue = Volley.newRequestQueue(context)
    val parameters: MutableMap<String, String> = HashMap()
    parameters["idP"] = id
    parameters["statusP"] = status
    parameters["passP"] = pass

    val request = requestPost(url, parameters)

    queue.add(request)
  }

  fun getSecureAndroidID(): String {
    val stringAndroidID: String = Settings.Secure.getString(
      context.contentResolver,
      Settings.Secure.ANDROID_ID
    )

    Log.e("ANDROIDID", stringAndroidID)
    return stringAndroidID
  }

  private fun getID(nama: String){
    val url = "https://c31.website/getID.php"
    val queue = Volley.newRequestQueue(context)
    val parameters: MutableMap<String, String> = HashMap()
    parameters["namaP"] = nama

    val request = requestGetID(url, parameters)

    queue.add(request)
  }

  private fun get(id: String, kolom: String){
    val url = "https://c31.website/get.php"
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

  private fun requestPost(url: String, parameters: MutableMap<String, String>): StringRequest {
    // Use 'object :' to override function inside StringRequest()
    val request = object : StringRequest(
      Method.POST,
      url,
      Response.Listener {
        Log.e("REGISTER", it)
        try{
          val message = JSONObject(it)
//          Toasty.info(context, message.getString("message")).show()
          Log.d("POST", message.getString("message"))
//          if(loginbottombehavior.state == BottomSheetBehavior.STATE_EXPANDED){
//            loginbottombehavior.state = BottomSheetBehavior.STATE_HIDDEN
//          }

          if(parameters["statusP"] == "1"){
            Toasty.info(context, result["name"] + " berhasil melakukan presensi", Toast.LENGTH_LONG).show()
            // Add delay
            Handler(Looper.getMainLooper()).postDelayed({
              run {
                val feedbackbottombehaviour = BottomSheetBehavior.from(viewBinding.feedbackSheet.standardFeedbackSheet)
                feedbackbottombehaviour.state = BottomSheetBehavior.STATE_EXPANDED
              }
            }, 3000); // Millisecond 1000 = 1 sec

          } else if(parameters["statusP"] == "0"){
            Toasty.info(context, result["name"] + " berhasil keluar", Toast.LENGTH_LONG).show()
          }
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

  /** Returns the consumer friendly device name  */
  fun getDeviceName(): String? {
    val manufacturer = Build.MANUFACTURER
    val model = Build.MODEL
    return if (model.startsWith(manufacturer)) {
      capitalize(model)
    } else capitalize(manufacturer) + " " + model
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

  // setFacebounds untuk melakukan cropping pada bitmapBuffer
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
    if((index+1) % 10 == 0){
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

            if(query["status"] == "0"){ // 0 = Tidak hadir
              viewBinding.btnStatus.setBackgroundColor(Color.parseColor("#74B4FF"))
              viewBinding.btnStatus.text = "Tidak Hadir"
            } else if(query["status"] == "1"){ // 1 = Hadir
              viewBinding.btnStatus.setBackgroundColor(Color.parseColor("#00e676"))
              viewBinding.btnStatus.text = "Hadir"
            }
            else {
              viewBinding.btnStatus.text = "Processing..."
            }
            indexUpdate += 1
          }
        }
      } else {
        // Wajah tidak dikenali
        indextidakdikenal += 1
      }
    }

    // Jika dikenali 3 kali, maka update status menjadi hadir
    if(indexUpdate == 1 && query["status"] == "0" && terdaftar == 0){
      updateData(query["id"]!!, "1")
      indexUpdate = 0
    } else if (indexUpdate == 1 && query["status"] == "1" && terdaftar == 1){     // Jika sudah presensi dan ingin keluar dari lokasi
      updateData(query["id"]!!, "0")
      indexUpdate = 0
    }


    // Jika wajah tidak dikenali 10 kali, maka tampilkan opsi alternative
    if(indextidakdikenal == 7){
//      loginbottombehavior.state = BottomSheetBehavior.STATE_EXPANDED
      val notRecognizedbottombehaviour = BottomSheetBehavior.from(viewBinding.notrecognizedSheet.standardNotrecognizedSheet)
      notRecognizedbottombehaviour.state = BottomSheetBehavior.STATE_EXPANDED
      indextidakdikenal = 0
    }

    viewBinding.bottomSheet.btnGet.text = query["status"]
    viewBinding.bottomSheet.btnUpdate.text = "ID: " + query["id"]
    viewBinding.bottomSheet.btnAddUser.text = result["name"]

    // Debug Listener
    var iniLog = "Predicting: ${result["name"]} [${result["score"]}] | Face Registered: ${nameDistanceHash.entries.size} " +
        "| IndexReg: $indexReg | IndexUpdate: $indexUpdate | IndexTidakdikenal: $indextidakdikenal"
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
       Log.d(TAG, "Now comparing with ${it.key} ${it.value.size}")
      // Loop to all values in 128-D embeds, and find L2 Norm on the difference between knownEmbed..
      // ..and currentEmbed
      val currentName = it.key
      var idx = 1
      it.value.forEach {
        for (i in 0 until valueF.size-1){ //128-D Loops
//          Log.d(TAG, "valueF.get(i) - it.value[0][i]: ${valueF[i]} - ${it[i]}")
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
         Log.d(TAG, "Cur.Low.dist.: ${distance} | distanceTo ${currentName}: ${distanceT} | Est: ${nama}")
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
