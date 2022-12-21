package com.lemzeeyyy.sayfe

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.Navigation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.lemzeeyyy.sayfe.databinding.FragmentAddPhoneNumberBinding


class AddPhoneNumber : Fragment() {

    private lateinit var binding : FragmentAddPhoneNumberBinding
    private lateinit var fAuth: FirebaseAuth
    private val database = Firebase.firestore
    private val collectionReference = database.collection("Users")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentAddPhoneNumberBinding.inflate(inflater,container,false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fAuth = Firebase.auth
        binding.setupLaterTxt.setOnClickListener {
            Navigation.findNavController(view).navigate(R.id.dashboardFragment)
        }
        binding.continueAddPhone.setOnClickListener {
            savePhoneNumber()
            Navigation.findNavController(view).navigate(R.id.dashboardFragment)
        }
    }



    private fun savePhoneNumber() {
        val phone = binding.phoneNumberEt.text.toString()
        val user = fAuth.currentUser
         val currentUserId = user!!.uid

        collectionReference.whereEqualTo("userid",currentUserId)
            .addSnapshotListener { value, error ->
                if(!value!!.isEmpty){
                    for( snapshot : QueryDocumentSnapshot in value){
                        collectionReference.document(snapshot.id).update("phoneNumber",phone)
                    }

                }
            }


    }

}