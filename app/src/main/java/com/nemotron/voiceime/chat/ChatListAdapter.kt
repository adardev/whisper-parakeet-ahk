package com.nemotron.voiceime.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.nemotron.voiceime.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatListAdapter(
    private val items: MutableList<Conversation>,
    private val onClick: (Conversation) -> Unit,
    private val onDelete: (Conversation) -> Unit,
    private val onRename: (Conversation) -> Unit,
    private val onPin: (Conversation) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.VH>() {

    fun replaceItems(next: List<Conversation>) {
        val old = items.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = old[oldPosition].id == next[newPosition].id
            override fun areContentsTheSame(oldPosition: Int, newPosition: Int) =
                old[oldPosition].title == next[newPosition].title &&
                    old[oldPosition].createdAt == next[newPosition].createdAt &&
                    old[oldPosition].pinned == next[newPosition].pinned
        })
        items.clear()
        items.addAll(next)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.title.text = c.title
        holder.pin.setImageResource(if (c.pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin)
        val date = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(c.createdAt))
        val link = if (c.source.isNotBlank()) "${c.source} · ${c.displayName.ifBlank { c.chatId }}" else date
        holder.meta.text = if (c.source.isNotBlank()) "$link  ·  $date" else date
        holder.itemView.setOnClickListener { onClick(c) }
        holder.itemView.setOnLongClickListener { onRename(c); true }
        holder.trash.setOnClickListener { onDelete(c) }
        holder.pin.setOnClickListener { onPin(c) }
    }

    override fun getItemCount(): Int = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.chatTitle)
        val meta: TextView = v.findViewById(R.id.chatMeta)
        val trash: ImageView = v.findViewById(R.id.chatTrash)
        val pin: ImageView = v.findViewById(R.id.chatPin)
    }
}
