package domain

data class CardChain(
    val card: Card,
    val chain: MutableList<CardBlock> = mutableListOf()
) {
    fun currentOwner(): String = chain.last().transaction.recipientPublicKey
    fun headHash(): String = chain.last().hash
}