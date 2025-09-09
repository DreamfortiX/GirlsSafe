package com.example.gamified.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.gamified.R

class ProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.fab_edit_image).setOnClickListener {
            // TODO: Handle profile image edit
        }
        view.findViewById<View>(R.id.btn_emergency_contacts).setOnClickListener {
            // TODO: Navigate to emergency contacts screen
        }
        view.findViewById<View>(R.id.btn_logout).setOnClickListener {
            // TODO: Handle logout
        }
    }
}
