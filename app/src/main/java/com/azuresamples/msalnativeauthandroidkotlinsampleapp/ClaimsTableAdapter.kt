package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/** Renders a decoded ID/access token's claims as a scrollable Claim/Value/Description list. */
class ClaimsTableAdapter(
    private val rows: List<ClaimsUtils.ClaimRow>
) : RecyclerView.Adapter<ClaimsTableAdapter.ViewHolder>() {

    companion object {
        /** Highlighted so the step-up demo's payoff - the acrs claim landing on the access
         *  token - is easy to spot in the table instead of scanning every row for it. */
        private const val HIGHLIGHTED_CLAIM = "acrs"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_claim_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = rows[position]
        holder.claimName.text = row.claim
        holder.claimValue.text = row.value
        holder.claimDescription.text = row.description
        holder.claimDescription.visibility = if (row.description.isEmpty()) View.GONE else View.VISIBLE

        holder.itemView.setBackgroundColor(
            if (row.claim == HIGHLIGHTED_CLAIM) {
                ContextCompat.getColor(holder.itemView.context, R.color.claim_highlight_bg)
            } else {
                android.graphics.Color.TRANSPARENT
            }
        )
    }

    override fun getItemCount(): Int = rows.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val claimName: TextView = itemView.findViewById(R.id.claim_name)
        val claimValue: TextView = itemView.findViewById(R.id.claim_value)
        val claimDescription: TextView = itemView.findViewById(R.id.claim_description)
    }
}
