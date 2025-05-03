package com.example.try2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class UserAdapter : ListAdapter<UserSession, UserAdapter.UserViewHolder>(UserDiffCallback) {
    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(userSession: UserSession) {
            itemView.findViewById<TextView>(R.id.tvUser).text = userSession.user_id
            itemView.findViewById<TextView>(R.id.tvStatus).text =
                if (userSession.is_online) "Онлайн" else "Оффлайн"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

object UserDiffCallback : DiffUtil.ItemCallback<UserSession>() {
    override fun areItemsTheSame(oldItem: UserSession, newItem: UserSession): Boolean {
        return oldItem.user_id == newItem.user_id
    }

    override fun areContentsTheSame(oldItem: UserSession, newItem: UserSession): Boolean {
        return oldItem == newItem
    }
}