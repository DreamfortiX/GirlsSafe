package com.example.gamified.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.gamified.data.Contact
import com.example.gamified.databinding.ItemContactBinding

class ContactsAdapter(
    private val onClick: (Contact) -> Unit,
    private val onLongClick: (Contact) -> Unit = {},
    private val onCall: (Contact) -> Unit = {},
    private val onEdit: (Contact) -> Unit = {},
    private val onDelete: (Contact) -> Unit = {}
) :
    ListAdapter<Contact, ContactsAdapter.VH>(DIFF) {

    object DIFF : DiffUtil.ItemCallback<Contact>() {
        override fun areItemsTheSame(a: Contact, b: Contact) = a.id == b.id
        override fun areContentsTheSame(a: Contact, b: Contact) = a == b
    }

    inner class VH(private val b: ItemContactBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: Contact) {
            b.name.text = listOfNotNull(item.firstName, item.lastName).joinToString(" ")
            b.phone.text = item.phone
            b.root.setOnClickListener { onClick(item) }
            b.root.setOnLongClickListener {
                onLongClick(item)
                true
            }

            // Buttons
            b.btnCall.setOnClickListener { onCall(item) }
            b.btnEdit.setOnClickListener { onEdit(item) }
            b.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
