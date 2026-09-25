import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.util.Base64

// Registrar o Provedor Bouncy Castle na JVM


// Configuração do Serializador JSON determinístico (Ordem Estável)
val canonicalJson = Json {
    prettyPrint = false
    encodeDefaults = true
}

// ==========================================
// 1. ESTRUTURAS DE DADOS SERIALIZÁVEIS
// ==========================================

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

@Serializable
data class CardTransaction(
    val txId: String,
    val senderPublicKey: String,
    val recipientPublicKey: String,
    val timestamp: Long,
    val nonce: Long,
    val signature: String
)

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

data class CardChain(
    val card: Card,
    val chain: MutableList<CardBlock> = mutableListOf()
) {
    fun currentOwner(): String = chain.last().transaction.recipientPublicKey
    fun headHash(): String = chain.last().hash
}

data class Deck(
    val name: String,
    val keyPair: KeyPairEd25519,
    val cards: MutableList<CardChain> = mutableListOf()
) {
    val ownerPublicKey: String get() = keyPair.publicKeyBase64
}

data class KeyPairEd25519(
    val publicKeyBase64: String,
    val privateKeyParams: Ed25519PrivateKeyParameters,
    val publicKeyParams: Ed25519PublicKeyParameters
)

// ==========================================
// 2. MOTOR CRIPTOGRÁFICO REAL (Ed25519 + SHA-256)
// ==========================================

object CryptoEngine {

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // Gera um Par de Chaves Real usando a curva elíptica Ed25519
    fun generateEd25519KeyPair(): KeyPairEd25519 {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()

        val priv = keyPair.private as Ed25519PrivateKeyParameters
        val pub = keyPair.public as Ed25519PublicKeyParameters

        val pubBase64 = Base64.getEncoder().encodeToString(pub.encoded)
        return KeyPairEd25519(pubBase64, priv, pub)
    }

    // Recria os parâmetros de Chave Pública a partir da String Base64
    fun decodePublicKey(base64PubKey: String): Ed25519PublicKeyParameters {
        val bytes = Base64.getDecoder().decode(base64PubKey)
        return Ed25519PublicKeyParameters(bytes, 0)
    }

    // Assina os dados digitais com a Chave Privada do emissor/remetente
    fun sign(data: String, privateKey: Ed25519PrivateKeyParameters): String {
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        val dataBytes = data.toByteArray(Charsets.UTF_8)
        signer.update(dataBytes, 0, dataBytes.size)
        val signatureBytes = signer.generateSignature()
        return Base64.getEncoder().encodeToString(signatureBytes)
    }

    // Valida criptograficamente a assinatura com a Chave Pública
    fun verify(data: String, signatureBase64: String, publicKeyBase64: String): Boolean {
        return try {
            val publicKey = decodePublicKey(publicKeyBase64)
            val signer = Ed25519Signer()
            signer.init(false, publicKey)
            val dataBytes = data.toByteArray(Charsets.UTF_8)
            signer.update(dataBytes, 0, dataBytes.size)
            val signatureBytes = Base64.getDecoder().decode(signatureBase64)
            signer.verifySignature(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }
}

// ==========================================
// 3. CORE DE MINERAÇÃO E VALIDAÇÃO CARDCHAIN
// ==========================================

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

        println("\n📦 Gerando Lote Genesis: $title ($maxEditions edições)...")

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
        println("\n🔍 Validando integridade criptográfica do Card #${cardChain.card.editionNumber} [${cardChain.card.title}]...")

        for (i in cardChain.chain.indices) {
            val block = cardChain.chain[i]

            // 1. Validação de PoW ('ccc')
            val rawBlockData = "${block.index}:${block.cardId}:${block.previousBlockHash}:${block.timestamp}:${block.proofNonce}:${block.transaction.txId}"
            val calculatedHash = CryptoEngine.sha256(rawBlockData)

            if (calculatedHash != block.hash || !block.hash.startsWith(DIFFICULTY_PREFIX)) {
                println("❌ FALHA CRÍTICA: Hash do Bloco #${block.index} inválido ou sem Dificuldade 'ccc'!")
                return false
            }

            // 2. Validação da Encadeamento do Bloco Anterior
            if (i > 0) {
                val previousBlock = cardChain.chain[i - 1]
                if (block.previousBlockHash != previousBlock.hash) {
                    println("❌ FALHA CRÍTICA: Quebra de integridade na cadeia entre Bloco #${block.index - 1} e Bloco #${block.index}!")
                    return false
                }
            } else {
                if (block.previousBlockHash != "0000000000000000000000000000000000000000000000000000000000000000") {
                    println("❌ FALHA CRÍTICA: Bloco Genesis adulterado!")
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
                println("❌ FALHA CRÍTICA: Assinatura Digital Ed25519 INVÁLIDA no Bloco #${block.index}!")
                return false
            }
        }

        println("✅ SUCESSO: A cadeia possui assinaturas Ed25519 válidas e PoW autêntica!")
        return true
    }
}

// ==========================================
// 4. FLUXO PRINCIPAL DE TESTE
// ==========================================

fun main() {
    if (Security.getProvider("BC") == null) {
        Security.addProvider(BouncyCastleProvider())
    }

    println("==========================================================")
    println("       CARDCHAIN CORE (Ed25519 + BouncyCastle)           ")
    println("==========================================================")

    // STEP 1: Gerar Par de Chaves Ed25519 para as Entidades
    val creatorKeyPair = CryptoEngine.generateEd25519KeyPair()
    val aliceKeyPair = CryptoEngine.generateEd25519KeyPair()
    val bobKeyPair = CryptoEngine.generateEd25519KeyPair()

    val aliceDeck = Deck(name = "Deck da Alice", keyPair = aliceKeyPair)
    val bobDeck = Deck(name = "Deck do Bob", keyPair = bobKeyPair)

    println("🔑 Chave Pública do Emissor Oficial: ${creatorKeyPair.publicKeyBase64.take(20)}...")
    println("🔑 Chave Pública da Alice:           ${aliceDeck.ownerPublicKey.take(20)}...")
    println("🔑 Chave Pública do Bob:             ${bobDeck.ownerPublicKey.take(20)}...")

    // STEP 2: Minting do Lote Genesis diretamente para a Alice
    val cardBatch = CardChainEngine.mintBatch(
        seriesId = "DEV-BELEM-2026",
        title = "Dragão Criptográfico",
        maxEditions = 2,
        rarity = "LEGENDARY",
        creatorKeyPair = creatorKeyPair,
        initialOwnerPublicKey = aliceDeck.ownerPublicKey
    )

    aliceDeck.cards.addAll(cardBatch)

    // STEP 3: Validação Inicial da Carteira da Alice
    CardChainEngine.validateCardChain(aliceDeck.cards[0])

    // STEP 4: Transferência Segura Ed25519 de Alice -> Bob
    println("\n----------------------------------------------------------")
    println("🔄 Transferindo Card da Alice para o Bob (Assinando com Ed25519)...")
    println("----------------------------------------------------------")

    val cardToTrade = aliceDeck.cards[0]

    CardChainEngine.transferCard(
        cardChain = cardToTrade,
        senderKeyPair = aliceKeyPair,
        recipientPublicKey = bobDeck.ownerPublicKey
    )

    aliceDeck.cards.remove(cardToTrade)
    bobDeck.cards.add(cardToTrade)

    // STEP 5: Serialização do Payload em JSON Canonical
    val serializedCardChain = canonicalJson.encodeToString(CardBlock.serializer(), cardToTrade.chain.last())
    println("\n📄 Payload do Último Bloco em JSON Determinístico (Pronto para QR/BLE):")
    println("   $serializedCardChain")

    // STEP 6: Revalidação da Cadeia do Bob
    CardChainEngine.validateCardChain(cardToTrade)

    // STEP 7: Teste de Ataque/Adulteração
    println("\n----------------------------------------------------------")
    println("⚠️ SIMULAÇÃO DE ATAQUE: Adulterando Transação no Bloco 1")
    println("----------------------------------------------------------")

    val hackerKeyPair = CryptoEngine.generateEd25519KeyPair()
    val targetBlock = cardToTrade.chain[1]

    // Tenta trocar a chave do remetente sem assinar novamente com a chave do hacker
    val forgedTransaction = targetBlock.transaction.copy(senderPublicKey = hackerKeyPair.publicKeyBase64)
    cardToTrade.chain[1] = targetBlock.copy(transaction = forgedTransaction)

    // Revalida após ataque
    CardChainEngine.validateCardChain(cardToTrade)
}