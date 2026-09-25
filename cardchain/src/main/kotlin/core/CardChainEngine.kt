package core

import domain.Card
import domain.CardBlock
import domain.CardChain
import domain.CardTransaction

object CardChainEngine {
    const val DIFFICULTY_PREFIX = "ccc"

    // Mineração de um novo Bloco (Proof of Work)
    fun mineBlock(
        index: Long,
        cardId: String,
        previousBlockHash: String,
        transaction: CardTransaction
    ): CardBlock {
        val timestamp = System.currentTimeMillis()
        var proofNonce = 0L
        var hash: String

        print("⛏️  Minerando Bloco #$index para o Card ${cardId.take(12)}...")

        while (true) {
            val rawData = "$index:$cardId:$previousBlockHash:$timestamp:$proofNonce:${transaction.txId}"
            hash = CryptoEngine.sha256(rawData)
            if (hash.startsWith(DIFFICULTY_PREFIX)) {
                println(" [SUCESSO!]")
                println("   └─ Nonce: $proofNonce | Hash: $hash")
                break
            }
            proofNonce++
        }

        return CardBlock(
            index = index,
            cardId = cardId,
            previousBlockHash = previousBlockHash,
            timestamp = timestamp,
            proofNonce = proofNonce,
            transaction = transaction,
            hash = hash
        )
    }

    // Geração do Lote Genesis (Minting de Cards)
    fun mintBatch(
        seriesId: String,
        title: String,
        maxEditions: Int,
        rarity: String,
        creatorKeyPair: KeyPairEd25519,
        initialOwnerPublicKey: String
    ): List<CardChain> {
        val createdChains = mutableListOf<CardChain>()
        val genesisTimestamp = System.currentTimeMillis()

        println("\n Gerando Lote Genesis: $title ($maxEditions edições)...")

        for (edition in 1..maxEditions) {
            val cardId = CryptoEngine.sha256("$seriesId:$edition:$genesisTimestamp")
            val card = Card(
                id = cardId,
                seriesId = seriesId,
                title = title,
                editionNumber = edition,
                maxEditions = maxEditions,
                rarity = rarity,
                creatorPublicKey = creatorKeyPair.publicKeyBase64
            )

            val rawTxData = "GENESIS:${creatorKeyPair.publicKeyBase64}:$initialOwnerPublicKey:$genesisTimestamp:0"
            val txId = CryptoEngine.sha256(rawTxData)
            val signature = CryptoEngine.sign(rawTxData, creatorKeyPair.privateKeyParams)

            val genesisTx = CardTransaction(
                txId = txId,
                senderPublicKey = creatorKeyPair.publicKeyBase64,
                recipientPublicKey = initialOwnerPublicKey,
                timestamp = genesisTimestamp,
                nonce = 0L,
                signature = signature
            )

            val genesisBlock = mineBlock(
                index = 0,
                cardId = cardId,
                previousBlockHash = "0000000000000000000000000000000000000000000000000000000000000000",
                transaction = genesisTx
            )

            val cardChain = CardChain(card = card)
            cardChain.chain.add(genesisBlock)
            createdChains.add(cardChain)
        }

        return createdChains
    }

    // Transferência P2P com Assinatura Digital do Remetente
    fun transferCard(
        cardChain: CardChain,
        senderKeyPair: KeyPairEd25519,
        recipientPublicKey: String
    ): CardBlock {
        require(cardChain.currentOwner() == senderKeyPair.publicKeyBase64) {
            "Erro: O remetente não possui as credenciais do proprietário atual!"
        }

        val timestamp = System.currentTimeMillis()
        val nonce = System.nanoTime()
        val rawTxData = "${cardChain.card.id}:${senderKeyPair.publicKeyBase64}:$recipientPublicKey:$timestamp:$nonce"
        val txId = CryptoEngine.sha256(rawTxData)
        val signature = CryptoEngine.sign(rawTxData, senderKeyPair.privateKeyParams)

        val tx = CardTransaction(
            txId = txId,
            senderPublicKey = senderKeyPair.publicKeyBase64,
            recipientPublicKey = recipientPublicKey,
            timestamp = timestamp,
            nonce = nonce,
            signature = signature
        )

        val newBlockIndex = cardChain.chain.size.toLong()
        val newBlock = mineBlock(
            index = newBlockIndex,
            cardId = cardChain.card.id,
            previousBlockHash = cardChain.headHash(),
            transaction = tx
        )

        cardChain.chain.add(newBlock)
        return newBlock
    }

    // Validação de Integridade e Assinaturas
    fun validateCardChain(cardChain: CardChain): Boolean {
        println("\n Validando integridade criptográfica do Card #${cardChain.card.editionNumber} [${cardChain.card.title}]...")

        for (i in cardChain.chain.indices) {
            val block = cardChain.chain[i]

            // 1. Validação de PoW ('ccc')
            val rawBlockData = "${block.index}:${block.cardId}:${block.previousBlockHash}:${block.timestamp}:${block.proofNonce}:${block.transaction.txId}"
            val calculatedHash = CryptoEngine.sha256(rawBlockData)

            if (calculatedHash != block.hash || !block.hash.startsWith(DIFFICULTY_PREFIX)) {
                println(" FALHA CRÍTICA: Hash do Bloco #${block.index} inválido ou sem Dificuldade 'ccc'!")
                return false
            }

            // 2. Validação da Encadeamento do Bloco Anterior
            if (i > 0) {
                val previousBlock = cardChain.chain[i - 1]
                if (block.previousBlockHash != previousBlock.hash) {
                    println("FALHA CRÍTICA: Quebra de integridade na cadeia entre Bloco #${block.index - 1} e Bloco #${block.index}!")
                    return false
                }
            } else {
                if (block.previousBlockHash != "0000000000000000000000000000000000000000000000000000000000000000") {
                    println("FALHA CRÍTICA: Bloco Genesis adulterado!")
                    return false
                }
            }

            // 3. Validação Criptográfica Ed25519 da Assinatura
            val rawTxData = if (i == 0) {
                "GENESIS:${block.transaction.senderPublicKey}:${block.transaction.recipientPublicKey}:${block.transaction.timestamp}:${block.transaction.nonce}"
            } else {
                "${block.cardId}:${block.transaction.senderPublicKey}:${block.transaction.recipientPublicKey}:${block.transaction.timestamp}:${block.transaction.nonce}"
            }

            val isSignatureValid = CryptoEngine.verify(
                data = rawTxData,
                signatureBase64 = block.transaction.signature,
                publicKeyBase64 = block.transaction.senderPublicKey
            )

            if (!isSignatureValid) {
                println("FALHA CRÍTICA: Assinatura Digital Ed25519 INVÁLIDA no Bloco #${block.index}!")
                return false
            }
        }

        println("SUCESSO: A cadeia possui assinaturas Ed25519 válidas e PoW autêntica!")
        return true
    }
}