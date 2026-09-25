package domain

import kotlinx.serialization.Serializable

@Serializable
data class CardBlock(
    val index: Long,
    val cardId: String,
    val previousBlockHash: String,
    val timestamp: Long,
    val proofNonce: Long,
    val transaction: CardTransaction,
    val hash: String
)