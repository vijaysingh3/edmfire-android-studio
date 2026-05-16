package com.edm.fire

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// OfferAdapter, RecyclerView को हमारी ऑफर लिस्ट दिखाने में मदद करता है।
// यह दो चीज़ों को अपने कंस्ट्रक्टर में लेता है:
// 1. offers: Offer क्लास की एक लिस्ट
// 2. onOfferClick: एक फंक्शन (lambda) जो तब चलेगा जब यूज़र किसी ऑफर पर क्लिक करेगा।
class OfferAdapter(
    private val offers: List<Offer>,
    private val onOfferClick: (Offer) -> Unit
) : RecyclerView.Adapter<OfferAdapter.OfferViewHolder>() {

    // ViewHolder एक क्लास है जो हमारे item_offer.xml लेआउट के हर एक व्यू (TextView आदि) को रखता है।
    // इसे बार-बार findViewById करने से बचाता है, जो परफॉर्मेंस के लिए अच्छा है।
    class OfferViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvOfferTitle: TextView = itemView.findViewById(R.id.tvOfferTitle)
        val tvOfferBonus: TextView = itemView.findViewById(R.id.tvOfferBonus)
        val tvOfferTotal: TextView = itemView.findViewById(R.id.tvOfferTotal)
        val tvBestValueTag: TextView = itemView.findViewById(R.id.tvBestValueTag)
    }

    // यह तब बुलाया जाता है जब RecyclerView को एक नया ViewHolder बनाने की ज़रूरत होती है।
    // यह हमारे item_offer.xml लेआउट को "inflate" करता है, यानी इसे कोड में एक View ऑब्जेक्ट में बदलता है।
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OfferViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_offer, parent, false)
        return OfferViewHolder(view)
    }

    // यह सबसे महत्वपूर्ण फंक्शन है। यह डेटा को ViewHolder में बाइंड (जोड़) करता है।
    // यह हर एक ऑफर आइटम के लिए चलेगा।
    override fun onBindViewHolder(holder: OfferViewHolder, position: Int) {
        val currentOffer = offers[position]

        // ViewHolder के TextViews में डेटा सेट करें
        holder.tvOfferTitle.text = currentOffer.offerTitle
        holder.tvOfferBonus.text = currentOffer.offerBonus
        holder.tvOfferTotal.text = "Total: ${currentOffer.offerTotalCoins} Coins"

        // "Best Value" टैग को दिखाएं या छुपाएं
        if (currentOffer.isBestValue) {
            holder.tvBestValueTag.visibility = View.VISIBLE
        } else {
            holder.tvBestValueTag.visibility = View.GONE
        }

        // जब यूज़र किसी ऑफर आइटम पर क्लिक करे
        holder.itemView.setOnClickListener {
            // हमने जो onOfferClick lambda बाहर से भेजी थी, उसे चलाएं और उसे क्लिक किया गया ऑफर भेजें।
            onOfferClick(currentOffer)
        }
    }

    // यह फंक्शन RecyclerView को बताता है कि हमारी लिस्ट में कितने आइटम्स हैं।
    override fun getItemCount(): Int {
        return offers.size
    }
}