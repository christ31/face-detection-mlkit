# face-detection-mlkit-tfLite-facenet
Aplikasi Android Face detection menggunakan [Facenet](https://ieeexplore.ieee.org/document/7298682), dan diimplementasikan dengan TensorFlow Lite.
FaceNet merupakan teknologi pengenalan wajah dari Google menggunakan metode Deeplearning CNN yang dilatih dengan fungsi triplet loss yang membuat vektor dengan identitas yang sama menjadi semakin serupa (jarak semakin kecil), sedangkan vektor dengan identitas yang berbeda menjadi semakin tidak serupa (jarak semakin jauh).

FaceNet yang digunakan adalah implementasi FaceNet di TensorFlow dari [David Sandberg](https://github.com/davidsandberg/facenet), dan dikonversikan ke TensorFlow Lite oleh [Esteban Uri](https://medium.com/@estebanuri/converting-sandbergs-facenet-pre-trained-model-to-tensorflow-lite-using-an-unorthodox-way-7ee3a6ed02a3). Dataset yang digunakan oleh David Sandberg untuk dikonversikan ke TensorFlow Lite adalah dataset VGGFace2.

## Cara Kerja FaceDetection
1. Registrasi Wajah
2. Pengenalan wajah dengan FaceNet
3. Simpan hasil pola wajah unik (128 Dimensi Vektor) dan nama di database
4. Scan wajah baru
5. Lakukan pengenalan wajah dengan FaceNet
6. Hasil dari pengenalan wajah dilakukan komparasi dengan seluruh entry di database
7. Lakukan perhitungan kemiripan dengan mencari jarak dari kedua nilai tersebut (Menggunakan rumus L2Norm)
8. Ambil kandidat dengan kemiripan tertinggi (jarak terdekat)
 
## Notes
[Self note] Source used to create this project:
- https://www.tensorflow.org/lite/android/quickstart
- https://developer.android.com/reference/android/provider/MediaStore.MediaColumns#RELATIVE_PATH
- https://developers.google.com/ml-kit/vision/face-detection/android

Related on ImageProxy to Bitmap
- https://stackoverflow.com/a/72247661/19278731

Related to Flipping ViewFinder
- https://developer.android.com/training/camerax/preview
- https://developer.android.com/training/camerax/configuration#camera-output

Related to storing Image
- https://developer.android.com/reference/android/provider/MediaStore.MediaColumns#RELATIVE_PATH

What is unit?
- https://stackoverflow.com/questions/55953052/kotlin-void-vs-unit-vs-nothing#:~:text=The%20Unit%20type%20is%20what,(lowercase%20v)%20in%20Java.

Creating/Draw Rectangle in CameraPreview/PreviewView
- https://stackoverflow.com/questions/63090795/how-to-draw-on-previewview

Storing HashMap internally
- https://stackoverflow.com/questions/17728449/how-can-i-store-a-data-structure-such-as-a-hashmap-internally-in-android

Using OpenCV in Android Studio
- https://medium.com/android-news/a-beginners-guide-to-setting-up-opencv-android-library-on-android-studio-19794e220f3c

Connecting to MySQL using Android Library 'Volley'
- https://www.geeksforgeeks.org/volley-library-in-android/

Related to ViewBinding on included layout
- https://stackoverflow.com/a/68481692/19278731
