package com.lemzeeyyy.sayfe.fragments

import android.os.Binder
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.lemzeeyyy.sayfe.NotificationBodyClickListener
import com.lemzeeyyy.sayfe.R
import com.lemzeeyyy.sayfe.adapters.IncomingAlertsRecyclerAdapter
import com.lemzeeyyy.sayfe.adapters.OutgoingAlertsRecyclerAdapter
import com.lemzeeyyy.sayfe.databinding.FragmentIncomingAlertBinding
import com.lemzeeyyy.sayfe.model.IncomingAlertData
import com.lemzeeyyy.sayfe.model.OutgoingAlertData
import com.lemzeeyyy.sayfe.model.Users
import com.lemzeeyyy.sayfe.service.AccessibilityKeyDetector
import com.lemzeeyyy.sayfe.service.AccessibilityKeyDetector.Companion.alertTriggerId
import com.lemzeeyyy.sayfe.service.AccessibilityKeyDetector.Companion.appTokenList
import com.lemzeeyyy.sayfe.viewmodels.MainActivityViewModel


class IncomingAlertFragment : Fragment(),NotificationBodyClickListener {

    private lateinit var binding : FragmentIncomingAlertBinding
    private lateinit var incomingAlertsRecyclerAdapter: IncomingAlertsRecyclerAdapter
    private val incomingAlertDb = Firebase.database
    private var fAuth = Firebase.auth
    private val database = Firebase.firestore
    private val usersCollection = database.collection("Users")

    private val myRef = incomingAlertDb.getReference("IncomingAlerts")
    private lateinit var notificationBodyListener : NotificationBodyClickListener

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentIncomingAlertBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fAuth = Firebase.auth
        val currentUser = fAuth.currentUser
        val currentUserId = currentUser?.uid
        notificationBodyListener = this
        incomingAlertsRecyclerAdapter = IncomingAlertsRecyclerAdapter(notificationBodyListener)
        binding.incomingRecycler.adapter = incomingAlertsRecyclerAdapter

        myRef.addValueEventListener(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
               //Update recyclerView for only targeted appID
                //update recycler view of devices with the uid in child of incoming alerts
                snapshot.children.forEach {
                    val incomingDataList = it.getValue<MutableList<IncomingAlertData>>()
                    if (currentUserId == it.key){
                        if (incomingDataList != null) {
                            incomingAlertsRecyclerAdapter.updateDataList(incomingDataList)
                        }
                    }


                }

            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(),error.message.toString(),Toast.LENGTH_SHORT).show()
            }

        })
    }

    override fun onNotificationBodyClick(view: View, alertBody : String) {
        val action = ActivitiesFragmentDirections.actionIncomimgAlertFragmentToWebViewFragment(alertBody)

        Log.d("ALertBodyCheck", "onNotificationBodyClick: ${alertBody.toString()}")
        findNavController().navigate(action)

    }

}