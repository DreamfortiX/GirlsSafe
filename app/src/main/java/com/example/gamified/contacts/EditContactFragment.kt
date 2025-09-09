package com.example.gamified.contacts

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.gamified.R
import com.example.gamified.data.Contact
import com.example.gamified.databinding.FragmentEditContactBinding
import kotlinx.coroutines.launch

class EditContactFragment : Fragment(R.layout.fragment_edit_contact) {
    private val vm: EditContactViewModel by viewModels()
    private var _b: FragmentEditContactBinding? = null
    private val b get() = _b!!

    private var imagePath: String? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            imagePath = it.toString()
            b.imgProfile.setImageURI(uri)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _b = FragmentEditContactBinding.bind(view)

        b.btnPickImage.setOnClickListener { pickImage.launch("image/*") }

        val contactId = arguments?.getLong(ARG_CONTACT_ID, -1L)?.takeIf { it > 0L }
        if (contactId != null) {
            // Editing existing contact
            viewLifecycleOwner.lifecycleScope.launch {
                val c = vm.load(contactId)
                if (c != null) fillForm(c)
            }
        } else {
            // New contact: hide delete
            b.btnDelete.visibility = View.GONE
        }

        b.btnSave.setOnClickListener {
            val first = b.etFirst.text.toString().trim()
            val phone = b.etPhone.text.toString().trim()
            if (first.isEmpty() || phone.isEmpty()) {
                Toast.makeText(requireContext(), "First name and phone are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val contact = Contact(
                id = contactId ?: 0L,
                firstName = first,
                lastName = b.etLast.text.toString().trim().ifEmpty { null },
                phone = phone,
                email = b.etEmail.text.toString().trim().ifEmpty { null },
                address = b.etAddress.text.toString().trim().ifEmpty { null },
                profileImagePath = imagePath
            )
            viewLifecycleOwner.lifecycleScope.launch {
                if (contactId == null) vm.insert(contact) else vm.update(contact)
                Toast.makeText(requireContext(), "Saved", Toast.LENGTH_SHORT).show()
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        b.btnDelete.setOnClickListener {
            val id = contactId ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                vm.load(id)?.let {
                    vm.delete(it)
                    Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
    }

    private fun fillForm(c: Contact) {
        b.etFirst.setText(c.firstName)
        b.etLast.setText(c.lastName ?: "")
        b.etPhone.setText(c.phone)
        b.etEmail.setText(c.email ?: "")
        b.etAddress.setText(c.address ?: "")
        imagePath = c.profileImagePath
        // Optional: load image with Glide/Picasso
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_CONTACT_ID = "contactId"
    }
}
