package com.example.try2

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UserAdapter : RecyclerView.Adapter<UserAdapter.ViewHolder>() {

    private var items = emptyList<UserSession>()

    fun submitList(list: List<UserSession>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvUser = view.findViewById<TextView>(R.id.tvUser)
        private val tvStatus = view.findViewById<TextView>(R.id.tvStatus)

        fun bind(session: UserSession) {
            tvUser.text = session.user_id
            tvStatus.text = if (session.is_online) "🟢 Онлайн" else "🔴 Оффлайн"
        }
    }

}
