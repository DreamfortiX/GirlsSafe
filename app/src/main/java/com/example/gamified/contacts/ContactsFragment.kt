package com.example.gamified.contacts

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gamified.R
import com.example.gamified.databinding.FragmentContactsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.LayoutInflater
import android.widget.EditText

class ContactsFragment : Fragment(R.layout.fragment_contacts) {
    private val vm: ContactsViewModel by viewModels()
    private var _b: FragmentContactsBinding? = null
    private val b get() = _b!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _b = FragmentContactsBinding.bind(view)

        val adapter = ContactsAdapter(
            onClick = { contact ->
                val frag = EditContactFragment().apply {
                    arguments = Bundle().apply {
                        putLong(EditContactFragment.ARG_CONTACT_ID, contact.id)
                    }
                }
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, frag)
                    .addToBackStack(null)
                    .commit()
            },
            onLongClick = { contact ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete contact?")
                    .setMessage("Are you sure you want to delete ${contact.firstName}?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        vm.delete(contact)
                        Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            },
            onCall = { contact ->
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}"))
                startActivity(intent)
            },
            onEdit = { contact ->
                val frag = EditContactFragment().apply {
                    arguments = Bundle().apply {
                        putLong(EditContactFragment.ARG_CONTACT_ID, contact.id)
                    }
                }
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, frag)
                    .addToBackStack(null)
                    .commit()
            },
            onDelete = { contact ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete contact?")
                    .setMessage("Are you sure you want to delete ${contact.firstName}?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        vm.delete(contact)
                        Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        )
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter

        vm.contacts.observe(viewLifecycleOwner) { adapter.submitList(it) }

        b.fabAdd.setOnClickListener {
            showAddContactDialog()
        }
    }

    private fun showAddContactDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_contact, null)
        val etFirstName = dialogView.findViewById<EditText>(R.id.etFirstName)
        val etLastName = dialogView.findViewById<EditText>(R.id.etLastName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etPhone)
        val etEmail = dialogView.findViewById<EditText>(R.id.etEmail)
        val etAddress = dialogView.findViewById<EditText>(R.id.etAddress)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Contact")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val btn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                val name = etFirstName.text?.toString()?.trim().orEmpty()
                val lastName = etLastName.text?.toString()?.trim().orEmpty()
                val phone = etPhone.text?.toString()?.trim().orEmpty()
                val email = etEmail.text?.toString()?.trim().orEmpty()
                val address = etAddress.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    etFirstName.error = "Required"
                    return@setOnClickListener
                }
                if (phone.isEmpty()) {
                    etPhone.error = "Required"
                    return@setOnClickListener
                }
                vm.add(name, lastName, phone, email, address) { success, error ->
                    if (success) {
                        Toast.makeText(requireContext(), "Contact added", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(requireContext(), error ?: "Failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
