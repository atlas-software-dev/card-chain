package domain

import core.KeyPairEd25519

data class Deck(
    val name: String,
    val keyPair: KeyPairEd25519,
    val cards: MutableList<CardChain> = mutableListOf()
) {
    val ownerPublicKey: String get() = keyPair.publicKeyBase64
}