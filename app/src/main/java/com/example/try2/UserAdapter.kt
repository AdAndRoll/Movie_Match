package com.example.try2

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class UserAdapter : ListAdapter<RoomActivity.User, UserAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userName: TextView = view.findViewById(R.id.userName)
        val statusIndicator: View = view.findViewById(R.id.statusIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = getItem(position)
        holder.userName.text = user.name

        val statusColor = if (user.online) Color.GREEN else Color.GRAY
        holder.statusIndicator.setBackgroundColor(statusColor)

        if (user.uid == user.ownerId) {
            holder.userName.text = "${user.name} (Создатель)"
        }
    }
    // В UserAdapter
    override fun getItemId(position: Int): Long {
        return getItem(position).uid?.hashCode()?.toLong() ?: super.getItemId(position)
    }

    class DiffCallback : DiffUtil.ItemCallback<RoomActivity.User>() {
        override fun areItemsTheSame(oldItem: RoomActivity.User, newItem: RoomActivity.User) =
            oldItem.uid == newItem.uid

        override fun areContentsTheSame(oldItem: RoomActivity.User, newItem: RoomActivity.User) =
            oldItem == newItem
    }
}