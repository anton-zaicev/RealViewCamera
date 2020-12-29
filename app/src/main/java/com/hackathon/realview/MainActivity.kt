package com.hackathon.realview

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.provider.MediaStore
import android.view.View
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    private val CAMERA_REQUEST_CODE = 2020
    private val FINE_LOCATION_PERMISSION_CODE = 2021

    private val DEG_PER_RADIAN = -(180.0 / Math.PI)

    private var _latitude: Double? = null
    private var _longitude: Double? = null
    private var _altitude: Double? = null
    private var _direction: Double? = null
    private var _pitch: Double? = null

    private var sensorManager: SensorManager? = null
    private var rotationVectorSensor: Sensor? = null
    private lateinit var latitudeTextView: TextView
    private lateinit var longitudeTextView: TextView
    private lateinit var altitudeTextView: TextView
    private lateinit var directionTextView: TextView
    private lateinit var pitchTextView: TextView

    private var skip = false

    private lateinit var _newPhotoFile: File

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            FINE_LOCATION_PERMISSION_CODE -> {
                if ((grantResults.isNotEmpty() &&
                                grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // ignored
                } else {
                    requestPermission(Manifest.permission.ACCESS_FINE_LOCATION, FINE_LOCATION_PERMISSION_CODE);
                }
                return
            }
        }
    }

    private fun requestPermission(permissionName: String, permissionRequestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permissionName), permissionRequestCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationVectorSensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        sensorManager!!.registerListener(this, rotationVectorSensor, 10000)

        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermission(Manifest.permission.ACCESS_FINE_LOCATION, FINE_LOCATION_PERMISSION_CODE);
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 1.0F, this)

        setContentView(R.layout.activity_main)
        title = "RealViewCamera"
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)

        latitudeTextView = findViewById(R.id.latitude) as TextView
        longitudeTextView = findViewById(R.id.longitude) as TextView
        altitudeTextView = findViewById(R.id.altitude) as TextView
        directionTextView = findViewById(R.id.direction) as TextView
        pitchTextView = findViewById(R.id.pitch) as TextView
    }

    fun makePhoto(view: View) {
        skip = true

        val builder: StrictMode.VmPolicy.Builder = StrictMode.VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())

        val storagePath: String = getExternalFilesDir(null)?.absolutePath + "/Photos/"

        val storageDir = File(storagePath)
        storageDir.mkdirs()

        val date = Date()
        val dateFormat: DateFormat = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS")
        val newPhotoFileName: String = "photo_" + dateFormat.format(date).toString() + ".jpg"
        val newPhotoFile = File(storagePath + newPhotoFileName)
        val newPhotoUri = Uri.fromFile(newPhotoFile)

        _newPhotoFile = newPhotoFile

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, newPhotoUri);
        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST_CODE) {
            try {
                val exif = ExifInterface(_newPhotoFile)
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, _latitude?.let { GPS.convert(it) })
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, _latitude?.let { GPS.latitudeRef(it) })
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, _longitude?.let { GPS.convert(it) })
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, _longitude?.let { GPS.longitudeRef(it) })
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, _altitude?.let { convertDecimalToFraction(it) })
                exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, _direction?.let { convertDecimalToFraction(it) })
                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "{AppName:\"RealViewCamera\",Pitch:\"" + _pitch + "\",Altitude:\"" + _altitude + "\",Direction:\"" + _direction + "\"}")
                exif.saveAttributes()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        skip = false
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (skip)
            return

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event?.values)
        SensorManager.getOrientation(rotationMatrix, orientation);

        val pitchRad = orientation[2]
        val pitchDeg = pitchRad.times(DEG_PER_RADIAN)
        val pitchNormalized = pitchDeg.minus(90.0)

        _pitch = pitchNormalized

        val azimuth = orientation[0]
        var direction = azimuth * 360 / (2 * Math.PI)
        direction = direction.plus(90.0)
        if (direction < 0)
            direction += 360.0

        _direction = direction

        directionTextView.setText("Direction: " + _direction)
        pitchTextView.setText("Pitch: " + _pitch)
    }

    override fun onLocationChanged(location: Location) {
        if (skip)
            return

        _latitude = location.latitude
        _longitude = location.longitude
        _altitude = location.altitude

        latitudeTextView.setText("Latitude: " + _latitude)
        longitudeTextView.setText("Longitude: " + _longitude)
        altitudeTextView.setText("Altitude: " + _altitude)
    }

    private fun convertDecimalToFraction(x: Double): String {
        if (x < 0) {
            return "-" + convertDecimalToFraction(-x)
        }
        val tolerance = 1.0E-6
        var h1 = 1.0
        var h2 = 0.0
        var k1 = 0.0
        var k2 = 1.0
        var b = x
        do {
            val a = Math.floor(b)
            var aux = h1
            h1 = a * h1 + h2
            h2 = aux
            aux = k1
            k1 = a * k1 + k2
            k2 = aux
            b = 1 / (b - a)
        } while (Math.abs(x - h1 / k1) > x * tolerance)
        return "$h1/$k1"
    }
}