package com.lemzeeyyy.sayfe.fragments

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firestore.v1.StructuredAggregationQuery.Aggregation.Count
import com.google.gson.Gson
import com.lemzeeyyy.sayfe.AccessibilityServiceSettings
import com.lemzeeyyy.sayfe.viewmodels.MainActivityViewModel
import com.lemzeeyyy.sayfe.R
import com.lemzeeyyy.sayfe.activities.PERMISSION_REQUEST
import com.lemzeeyyy.sayfe.database.SharedPrefs
import com.lemzeeyyy.sayfe.databinding.FragmentDashboardBinding
import com.lemzeeyyy.sayfe.model.GuardianData
import com.lemzeeyyy.sayfe.model.OutgoingAlertData
import com.lemzeeyyy.sayfe.model.PhonebookContact
import com.lemzeeyyy.sayfe.model.Users
import com.lemzeeyyy.sayfe.service.AccessibilityKeyDetector
import com.lemzeeyyy.sayfe.service.NOTIFICATION_URL
import com.lemzeeyyy.sayfe.service.WEB_KEY
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

const val LOCATION_PERMISSION = 2001

class DashboardFragment : Fragment() {

    private lateinit var binding: FragmentDashboardBinding
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private val viewModel: MainActivityViewModel by activityViewModels()
    private lateinit var fAuth: FirebaseAuth

    private val database = Firebase.firestore
    private val collectionReference = database.collection("Users")
    private val guardianCollection = database.collection("Guardian Angels")


    // private var imageUri : Uri = Uri.EMPTY
    private lateinit var backPressedCallback: OnBackPressedCallback
    private var sensorManager: SensorManager? = null
    private var acceleration = 0f
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var locationUrl: String = ""
    private var exitAppToastStillShowing = false
    private var senderName : String =""
    private var senderMessageBody : String =""
    private var guardianList = mutableListOf<PhonebookContact>()
    private var outgoingDataList = mutableListOf<OutgoingAlertData>()
    private val outgoingAlertDb = Firebase.database
    private val myRef = outgoingAlertDb.getReference("OutgoingAlerts")
    private var listItem = mutableListOf<Int>()
    private var shakeTrigger : Boolean = false
    private var volumeTrigger : Boolean = false
    private var tapTrigger : Boolean = false

    private val exitAppTimer = object : CountDownTimer(2000, 1000) {
        override fun onTick(millisUntilFinished: Long) {
        }
        override fun onFinish() {
            exitAppToastStillShowing = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backPressedCallback = requireActivity().onBackPressedDispatcher.addCallback(this) {
            exitApp()
        }
        backPressedCallback.isEnabled = true
    }

    private fun exitApp() {
        if (exitAppToastStillShowing) {
            requireActivity().finish()
            return
        }

        Toast.makeText(this.requireContext(), getString(R.string.exit), Toast.LENGTH_SHORT)
            .show()
        exitAppToastStillShowing = true
        exitAppTimer.start()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment

        binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fAuth = Firebase.auth
        collectionReference.whereEqualTo("currentUserId",fAuth.currentUser?.uid).addSnapshotListener { value, error ->
            if (value != null) {
                for ( i in value){
                    val users = i.toObject(Users::class.java)
                    binding.userNameHome.setText(users.fullName)
                }

            }
        }



        SharedPrefs.init(requireContext())
         shakeTrigger = SharedPrefs.getBoolean("shake", false)
         volumeTrigger = SharedPrefs.getBoolean("volume", false)
         tapTrigger = SharedPrefs.getBoolean("tap", false)

        if (SharedPrefs.getBoolean("shake", false)) {
            binding.triggerModeText.setText("Shake your phone to trigger Sayfe")
        } else if (SharedPrefs.getBoolean("tap", false)) {
            binding.triggerModeText.setText("Tap Screen twice to trigger sayfe")
        } else if (SharedPrefs.getBoolean("volume",false)){
            binding.triggerModeText.setText("Press volume up button twice to trigger sayfe")
        }
        else {
            binding.triggerModeText.setText("Press volume up button twice to trigger sayfe")
        }




        fAuth = Firebase.auth
        val user = fAuth.currentUser
        val currentUserId = user?.uid



        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireActivity())

        getCurrentLocation()

        if (currentUserId != null) {
            viewModel.getImageUriFromDb(currentUserId)
        }
        viewModel.userImageUri.observe(viewLifecycleOwner) {

            if (it.equals(Uri.EMPTY)) {
                binding.profileImage.setImageResource(R.drawable.profile_image)
            } else {
                Glide.with(requireActivity()).load(it).into(binding.profileImage)
            }

        }

        binding.alertBeneficiaryDashboard.setOnClickListener {
            findNavController().navigate(R.id.guardianAngelsFragment)

        }

        binding.triggersDashboard.setOnClickListener {
            findNavController().navigate(R.id.triggersFragment)
        }

        binding.sosTextsDashboard.setOnClickListener {
            findNavController().navigate(R.id.sosTextFragment)
        }
        getDoubleVolumeTap()


        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager

        Objects.requireNonNull(sensorManager)!!
            .registerListener(
                sensorListener, sensorManager!!
                    .getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL
            )

        acceleration = 10f
        currentAcceleration = SensorManager.GRAVITY_EARTH
        lastAcceleration = SensorManager.GRAVITY_EARTH

    }


    private fun sendPushNotifier(users: Users, data: OutgoingAlertData) {

        val body = Gson().toJson(data)

        val jsonObj = JSONObject()
        jsonObj.put("to", users.appToken)
        //jsonObj.put("notification", jsonNotifier)
        jsonObj.put("data", JSONObject(body))
        jsonObj.put("title",data)
        val request = okhttp3.Request.Builder()
            .url(NOTIFICATION_URL)
            .addHeader("Content-Type", "application/json")
            .addHeader(
                "Authorization",
                WEB_KEY
            )
            .post(
                jsonObj.toString().toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )
            ).build()

        val logger = HttpLoggingInterceptor()
        logger.level = HttpLoggingInterceptor.Level.BASIC
        OkHttpClient.Builder().addInterceptor(logger)
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build().newCall(request)
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d("CLOUD_MSG", "onFailure-- $e")
                }

                override fun onResponse(call: Call, response: Response) {
                    Log.d(
                        "CLOUD_MSG", "onResponse-- $response +++ " +
                                "${call.isExecuted()}====${response.isSuccessful}" +
                                "--${response.code}===${response.body.toString()}" +
                                "=${response.message}"
                    )
                }
            })
    }

    private fun getGuardianAngelsAppTokenAndSendNotification(currentUserid: String){
        //8106811525
        collectionReference.whereEqualTo("currentUserId",currentUserid)
            .addSnapshotListener { value, error ->
                value?.let {
                    if(!it.isEmpty){
                        for (snapshot : QueryDocumentSnapshot in value){
                            val currentUser = snapshot.toObject(Users::class.java)
                            senderName = currentUser.fullName
                            senderMessageBody = currentUser.userSOSText

                            Log.d("SENDER", "getGuardianAngelsAppToken: $senderName")
                        }
                    }
                }
            }
        guardianCollection.document(currentUserid)
            .get()
            .addOnSuccessListener {
                val data = it.toObject(GuardianData::class.java)
                data?.let { guardianData ->
                    guardianList = guardianData.guardianInfo
                    guardianList.forEach {recipientContact ->

                        collectionReference.whereEqualTo("number",recipientContact.number)
                            .get()
                            .addOnSuccessListener {querySnapshot ->
                                querySnapshot.forEach {queryDocumentSnapshot ->
                                    val users = queryDocumentSnapshot.toObject(Users::class.java)
                                    val appToken = users.appToken
                                    val userName = users.fullName
                                    AccessibilityKeyDetector.appTokenList.add(appToken)
                                    Log.d("APPTOKEN", "getGuardianAngelsAppToken: ${AccessibilityKeyDetector.appTokenList}")
                                    var cityName: String = ""
                                    val geoCoder = Geocoder(requireContext(), Locale.getDefault())
                                    val address = geoCoder.getFromLocation(latitude,longitude,1)
                                    if (address != null) {
                                        cityName = address[0].adminArea
                                        if (cityName == null){
                                            cityName = address[0].locality
                                            if (cityName == null){
                                                cityName = address[0].subAdminArea
                                            }
                                        }
                                    }
                                    Log.d("SENDER", "getGuardianAngelsAppToken: $senderName")
                                    val sdf = SimpleDateFormat("dd/M/yyyy hh:mm:ss")
                                    val currentDate = sdf.format(Date())

                                    val outgoingAlertData = OutgoingAlertData(senderName,
                                        locationUrl,currentDate,"Sayfe SOS Alert",cityName)

                                    outgoingDataList.add(outgoingAlertData)
                                    saveOutgoingAlertToDb(currentUserid, outgoingDataList)
                                    sendPushNotifier(users,outgoingAlertData)

                                }
                            }
                            .addOnFailureListener{exception ->
                                Log.d("APPTOKEN", "getGuardianAngelsAppToken: ${exception.toString()}")
                            }
                    }
                }
            }
            .addOnFailureListener {

            }
    }

    private fun saveOutgoingAlertToDb(currentUserid: String, outgoingAlertDataList: MutableList<OutgoingAlertData>){
        myRef.child(currentUserid).setValue(outgoingAlertDataList)
    }

    private fun getCurrentLocation() {
        if (checkPermissions()) {
            if (locationEnabled()) {
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermission()
                    return
                }
                fusedLocationProviderClient.lastLocation.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val location: Location? = task.result
                        if (location == null) {
//                            Toast.makeText(
//                                requireContext(),
//                                "Location is Null ke",
//                                Toast.LENGTH_SHORT
//                            ).show()
                        } else {
                            longitude = location.longitude
                            latitude = location.latitude
                            locationUrl =
                                "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"

                            Log.d("check locayu", "getCurrentLocation: $locationUrl ")
                        }
                    } else {
                        Toast.makeText(
                            requireContext(),
                            task.exception?.message.toString(),
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
                }
            } else {
                //go to settings.....
                Toast.makeText(requireContext(), "Turn on location", Toast.LENGTH_SHORT).show()
                val locationIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(locationIntent)
            }
        } else {
            //request permission
            requestPermission()
        }
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            PERMISSION_REQUEST
        )
    }

    private fun locationEnabled(): Boolean {
        val locationManager: LocationManager =
            requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun checkPermissions(): Boolean {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                Toast.makeText(requireContext(), "Granted", Toast.LENGTH_SHORT).show()
            getCurrentLocation()
        } else {
            Toast.makeText(requireContext(), "Denied", Toast.LENGTH_SHORT).show()
        }
    }





     private fun sendSMSSOS() {

         //Once triggered, check if guardian angels have app token, then send notification, else send only sms
        val currentUserId = fAuth.currentUser?.uid
         if (currentUserId != null) {
             getGuardianAngelsAppTokenAndSendNotification(currentUserId)
         }
        try {

            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= 23) {

                requireActivity().getSystemService(SmsManager::class.java)
            } else {

                SmsManager.getDefault()
            }

            if (currentUserId != null) {
                viewModel.getGuardianAngelsListFromDb(currentUserId)
            }
            viewModel.guardianLiveData.observe(viewLifecycleOwner) {
                val dataList = it.guardianInfo

                dataList.forEach { recipientContact ->

                    val guardianPhone = recipientContact.number

                    smsManager.sendTextMessage(
                        guardianPhone,
                        null,
                        "SOS! \n$locationUrl",
                        null,
                        null
                    )
                }
            }

            Toast.makeText(requireContext(), "Message Sent", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(requireContext(), e.message.toString(), Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun getDoubleVolumeTap(){
        val timer = object : CountDownTimer(300,300){
            override fun onTick(millisUntilFinished: Long) {
                listItem.add(1)
                Log.d("KOUNTER", "onTick: ${listItem.size}")
                if (listItem.size == 2){
                    if (!checkAccessibilityPermission() && volumeTrigger){
                        Toast.makeText(requireContext(),"Volume Up Twice Detected",Toast.LENGTH_SHORT).show()
                        sendSMSSOS()
                    }
                }
            }

            override fun onFinish() {
                listItem.clear()
            }

        }
        timer.start()
    }

    fun triggerSayfe() {
        SharedPrefs.init(requireContext())
        val shakeTrigger = SharedPrefs.getBoolean("shake", false)
        val volumeTrigger = SharedPrefs.getBoolean("volume", false)
        val tapTrigger = SharedPrefs.getBoolean("tap", false)

        if (!checkAccessibilityPermission()){
            if (shakeTrigger) {
                sendSMSSOS()
                return
            }
            if (volumeTrigger){
                getDoubleVolumeTap()
                return
            }
        }



    }


    private val sensorListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {

            // Fetching x,y,z values
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            lastAcceleration = currentAcceleration

            // Getting current accelerations
            // with the help of fetched x,y,z values
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta: Float = currentAcceleration - lastAcceleration
            acceleration = acceleration * 0.9f + delta

            if (acceleration > 12) {
               if (!checkAccessibilityPermission() && shakeTrigger){
                   Toast.makeText(requireContext(), "Shake event detected", Toast.LENGTH_SHORT).show()
                   sendSMSSOS()
               }
                return
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {

        }
    }

    private fun checkAccessibilityPermission(): Boolean {
        var accessEnabled = 0
        try {
            accessEnabled =
                Settings.Secure.getInt(requireActivity().contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
        }
        return if (accessEnabled == 0) {
            /** if not construct intent to request permission  */
            /** request permission via bottom sheet fragment  */
            openAccessibilitySettingsDialog()
            false
        } else {
            true
        }
    }

    private fun openAccessibilitySettingsDialog() {
        val dialog = AccessibilityServiceSettings()
        dialog.setCancelable(true)
        dialog.show(childFragmentManager, "NOTIFICATION SHEET")
    }

    override fun onResume() {
        sensorManager?.registerListener(
            sensorListener, sensorManager!!.getDefaultSensor(
                Sensor.TYPE_ACCELEROMETER
            ), SensorManager.SENSOR_DELAY_NORMAL
        )
        super.onResume()
    }

    override fun onPause() {
        sensorManager!!.unregisterListener(sensorListener)
        // fAuth = Firebase.auth
//        val user = fAuth.currentUser
//        val currentUserId = user!!.uid
//        viewModel.getImageUriFromDb(currentUserId)
//        viewModel.userImageUri.observe(viewLifecycleOwner){
//
//            if (it.equals(Uri.EMPTY)){
//                binding.profileImage.setImageResource(R.drawable.profile_image)
//            }else{
//                Glide.with(requireActivity()).load(it).into(binding.profileImage)
//            }
//
//        }
        super.onPause()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        checkAccessibilityPermission()


    }

}

