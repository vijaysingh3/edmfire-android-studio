package com.edm.fire

// यह एक डेटा क्लास है जो एक ऑफर की सारी जानकारी को स्टोर करेगा।
// data class इस्तेमाल करने से हमें बूयलरप्लेट कोड लिखने की ज़रूरत नहीं पड़ती।
data class Offer(
    // JSON के "OfferTitle" की वैल्यू यहाँ स्टोर होगी, जैसे "Add ₹25"
    val offerTitle: String,

    // JSON के "OfferBonus" की वैल्यू यहाँ स्टोर होगी, जैसे "and Get ₹4 Bonus"
    val offerBonus: String,

    // JSON के "OfferTotalCoins" की वैल्यू यहाँ स्टोर होगी, जैसे 29
    val offerTotalCoins: Int,

    // यह एक अतिरिक्त फील्ड है जो हमें UI को कंट्रोल करने में मदद करेगा।
    // अगर true है, तो "Best Value" टैग दिखेगा।
    val isBestValue: Boolean = false
)
