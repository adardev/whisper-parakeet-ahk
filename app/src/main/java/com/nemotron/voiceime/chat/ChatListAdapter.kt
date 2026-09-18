package com.nemotron.voiceime.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nemotron.voiceime.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatListAdapter(
    private val items: MutableList<Conversation>,
    private val onClick: (Conversation) -> Unit,
    private val onDelete: (Conversation) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.title.text = c.title
        holder.meta.text = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(c.createdAt))
        holder.itemView.setOnClickListener { onClick(c) }
        holder.itemView.setOnLongClickListener { onDelete(c); true }
        holder.trash.setOnClickListener { onDelete(c) }
    }

    override fun getItemCount(): Int = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.chatTitle)
        val meta: TextView = v.findViewById(R.id.chatMeta)
        val trash: ImageView = v.findViewById(R.id.chatTrash)
    }
}