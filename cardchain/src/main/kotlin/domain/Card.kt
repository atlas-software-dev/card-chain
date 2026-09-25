package domain

import kotlinx.serialization.Serializable

@Serializable
data class Card(
    val id: String,
    val seriesId: String,
    val title: String,
    val editionNumber: Int,
    val maxEditions: Int,
    val rarity: String,
    val creatorPublicKey: String
)