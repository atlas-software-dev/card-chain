package domain

import kotlinx.serialization.Serializable

@Serializable
data class CardTransaction(
    val txId: String,
    val senderPublicKey: String,
    val recipientPublicKey: String,
    val timestamp: Long,
    val nonce: Long,
    val signature: String
)