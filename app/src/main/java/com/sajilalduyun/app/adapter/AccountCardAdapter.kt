package com.sajilalduyun.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.sajilalduyun.app.R
import com.sajilalduyun.app.model.User
import com.sajilalduyun.app.model.UserRole

class AccountCardAdapter(
    private val accounts: List<User>,
    private val onAccountClick: (User) -> Unit
) : RecyclerView.Adapter<AccountCardViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountCardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_account_card, parent, false)
        return AccountCardViewHolder(view)
    }

    override fun onBindViewHolder(holder: AccountCardViewHolder, position: Int) {
        holder.bind(accounts[position], onAccountClick)
    }

    override fun getItemCount() = accounts.size
}

class AccountCardViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
    private val card: MaterialCardView = itemView.findViewById(R.id.cardAccount)
    private val tvName: android.widget.TextView = itemView.findViewById(R.id.tvAccountName)
    private val tvRole: android.widget.TextView = itemView.findViewById(R.id.tvAccountRole)
    private val tvStatus: android.widget.TextView = itemView.findViewById(R.id.tvAccountStatus)

    fun bind(user: User, onAccountClick: (User) -> Unit) {
        tvName.text = user.name
        tvRole.text = when (user.role) {
            UserRole.OWNER -> "المالك"
            UserRole.WORKER -> "العامل"
        }
        tvStatus.text = if (user.isActive) "نشط" else "غير نشط"
        tvStatus.setTextColor(
            if (user.isActive) 0xFFCFFF04.toInt() else 0xFF808080.toInt()
        )

        card.setOnClickListener { onAccountClick(user) }
    }
}

