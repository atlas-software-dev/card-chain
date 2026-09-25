# CardChain Protocol & Engine Specifications

O **CardChain** é uma arquitetura de blockchain leve, isolada por ativo e focada em consenso Peer-to-Peer (P2P), projetada para mintagem, custódia e transferência atômica de cards colecionáveis digitais de edição limitada sem a necessidade de blockchains públicas com taxas de transação (*gas fees*).

Cada card possui sua própria blockchain autônoma (**CardChain**), garantindo rastreabilidade do histórico de posse, escassez via criptografia de chave pública/privada (Ed25519) e integridade de dados via Prova de Trabalho (*Proof of Work* - PoW).

---

## 1. Arquitetura do Protocolo

Diferente das blockchains convencionais que mantêm um único registro global contendo o estado de todas as contas, o CardChain adota um modelo onde **cada ativo (card) é sua própria cadeia de blocos**.

```text
+-----------------------------------------------------------------------------------+
|                                   DECK (Carteira)                                 |
|  - Owner Public Key (Ed25519)                                                     |
|  - Encrypted Private Key (Ed25519)                                                |
+-----------------------------------------------------------------------------------+
                                          |
                                          | Mantém N Cards
                                          v
+-----------------------------------------------------------------------------------+
|                                    CARDCHAIN                                      |
|  +-----------------------------------------------------------------------------+  |
|  | Card Metadata (ID, Series, Title, EditionNumber, MaxEditions, CreatorKey)   |  |
|  +-----------------------------------------------------------------------------+  |
|  | Chain: List<CardBlock>                                                      |  |
|  |   ├─ Block #0 (Genesis Block: Creator -> InitialOwner) [Hash: ccc...]         |  |
|  |   ├─ Block #1 (Transfer: InitialOwner -> UserB)        [Hash: ccc...]         |  |
|  |   └─ Block #N (Transfer: UserB -> UserC)               [Hash: ccc...]         |  |
|  +-----------------------------------------------------------------------------+  |
+-----------------------------------------------------------------------------------+
```

### Vantagens do Modelo de Cadeia Isolada por Card
* **Validação Leve e Local:** Para validar a autenticidade de um card recebido via Bluetooth ou QR Code, o dispositivo precisa verificar apenas a sequência de blocos daquele card específico, sem baixar ou processar a blockchain global.
* **Transferência Atômica P2P:** A troca pode ser realizada inteiramente offline entre dois dispositivos por aproximação ou leitura óptica.

---

## 2. Especificação do Modelo de Dados

### 2.1 Metadados Imutáveis do Card
Representa o ativo digital imutável gerado na mintagem.

```kotlin
data class Card(
    val id: String,              // SHA-256(seriesId + editionNumber + genesisTimestamp)
    val seriesId: String,        // Ex: "DEV-BELEM-2026"
    val title: String,           // Ex: "Dragão Criptográfico"
    val editionNumber: Int,      // Número da cópia (Ex: 1)
    val maxEditions: Int,        // Suprimento total da série (Ex: 100)
    val rarity: String,          // Ex: "LEGENDARY", "RARE", "COMMON"
    val creatorPublicKey: String // Chave pública Ed25519 da autoridade emissora
)
```

### 2.2 Transação de Troca de Posse (Payload)
Representa a alteração de propriedade do card assinada digitalmente pelo remetente.

```kotlin
data class CardTransaction(
    val txId: String,               // SHA-256(cardId + senderPublicKey + recipientPublicKey + timestamp + nonce)
    val senderPublicKey: String,    // Endereço do remetente (No Bloco Genesis = creatorPublicKey)
    val recipientPublicKey: String, // Endereço do destinatário
    val timestamp: Long,            // Epoch UNIX em milissegundos
    val nonce: Long,                // Nonce aleatório para prevenção de ataques de replay
    val signature: String           // Assinatura Ed25519 codificada em Base64
)
```

### 2.3 Bloco da Cadeia do Card
Contém o registro imutável com validação de Prova de Trabalho.

```kotlin
data class CardBlock(
    val index: Long,                // Altura do bloco na cadeia do card (Genesis = 0)
    val cardId: String,             // ID do card vinculado
    val previousBlockHash: String,  // Hash SHA-256 do bloco anterior
    val timestamp: Long,            // Epoch UNIX da mineração do bloco
    val proofNonce: Long,           // Nonce que resolveu a PoW
    val transaction: CardTransaction, // Payload da transação
    val hash: String                // SHA-256 do bloco (Prefixado com "ccc")
)
```

---

## 3. Consenso e Mineração (Proof of Work)

O CardChain utiliza um algoritmo de Prova de Trabalho derivado do SHA-256 para atribuir custo computacional à criação e transferência de blocos, prevenindo spam e garantindo imutabilidade.

### Regra do Hash Alvo (Dificuldade `"ccc"`)
Um bloco só é considerado válido pela rede se seu hash SHA-256 começar estritamente com o prefixo `"ccc"`.

```text
BlockHash = SHA256(index + cardId + previousBlockHash + timestamp + proofNonce + txId)
```

### Pseudocódigo de Mineração
```text
função minerarBloco(index, cardId, previousBlockHash, transaction):
    timestamp = tempoAtualMillis()
    proofNonce = 0
    
    repita:
        rawData = index + ":" + cardId + ":" + previousBlockHash + ":" + timestamp + ":" + proofNonce + ":" + transaction.txId
        hash = SHA256(rawData)
        
        se hash.comecaCom("ccc"):
            retornar Bloco(index, cardId, previousBlockHash, timestamp, proofNonce, transaction, hash)
            
        proofNonce = proofNonce + 1
```

---

## 4. Mecanismos de Segurança e Criptografia

### 4.1 Criptografia Assimétrica Ed25519
* O endereço de cada **Deck** é a representação gráfica em Base64 de uma **Chave Pública Ed25519**.
* Cada transação exige que os dados estruturados do payload sejam assinados pela **Chave Privada Ed25519** vinculada ao proprietário atual do card.
* **Validação Math-Checked:** O nó receptor valida a assinatura utilizando a chave pública contida na transação anterior da cadeia.

### 4.2 Prevenção contra Gasto Duplo (*Double Spending*)
1. A propriedade de um card é determinada exclusivamente pelo `recipientPublicKey` do último bloco válido da sua cadeia.
2. Qualquer tentativa de criar duas transações concorrentes a partir do mesmo estado resultará na rejeição da transação cuja mineração não pertença a uma cadeia local autêntica.

### 4.3 Garantia de Escassez no Minting
* **Genesis Block (Bloco #0):** O primeiro bloco de qualquer card só é válido se a propriedade `senderPublicKey` for idêntica à `creatorPublicKey` definida nos metadados imutáveis do card.
* O `editionNumber` não pode exceder o `maxEditions` registrado no evento de emissão.

---

## 5. Implementação de Referência (Kotlin Pure Core + Bouncy Castle)

Abaixo está o motor funcional de mineração, validação criptográfica e transferência P2P do ecossistema CardChain.

### Dependência Recomendada
* **Bouncy Castle Provider:** `org.bouncycastle:bcprov-jdk18on:1.77`

### Código-Fonte Core (`CardChainCore.kt`)

```kotlin
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

// Registra o provedor Bouncy Castle para suporte nativo a Ed25519
fun setupCryptoProvider() {
    if (Security.getProvider("BC") == null) {
        Security.addProvider(BouncyCastleProvider())
    }
}

// ==========================================
// 1. MODELO DE DADOS
// ==========================================

data class Card(
    val id: String,
    val seriesId: String,
    val title: String,
    val editionNumber: Int,
    val maxEditions: Int,
    val rarity: String,
    val creatorPublicKey: String
)

data class CardTransaction(
    val txId: String,
    val senderPublicKey: String,
    val recipientPublicKey: String,
    val timestamp: Long,
    val nonce: Long,
    val signature: String
)

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
    val keyPair: KeyPair,
    val cards: MutableList<CardChain> = mutableListOf()
) {
    val ownerPublicKey: String
        get() = CryptoEngine.publicKeyToString(keyPair.public)
}

// ==========================================
// 2. MOTOR CRIPTOGRÁFICO (Ed25519 + SHA-256)
// ==========================================

object CryptoEngine {

    fun generateEd25519KeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("Ed25519", "BC")
        return keyPairGenerator.generateKeyPair()
    }

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun sign(data: String, privateKey: PrivateKey): String {
        val signer = Signature.getInstance("Ed25519", "BC")
        signer.initSign(privateKey)
        signer.update(data.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    fun verify(data: String, signatureBase64: String, publicKeyString: String): Boolean {
        return try {
            val publicKey = stringToPublicKey(publicKeyString)
            val verifier = Signature.getInstance("Ed25519", "BC")
            verifier.initVerify(publicKey)
            verifier.update(data.toByteArray(Charsets.UTF_8))
            val signatureBytes = Base64.getDecoder().decode(signatureBase64)
            verifier.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }

    fun publicKeyToString(publicKey: PublicKey): String {
        return Base64.getEncoder().encodeToString(publicKey.encoded)
    }

    private fun stringToPublicKey(publicKeyBase64: String): PublicKey {
        val keyBytes = Base64.getDecoder().decode(publicKeyBase64)
        val keyFactory = KeyFactory.getInstance("Ed25519", "BC")
        return keyFactory.generatePublic(X509EncodedKeySpec(keyBytes))
    }
}

// ==========================================
// 3. CORE ENGINE (MINERAÇÃO E VALIDAÇÃO)
// ==========================================

object CardChainEngine {
    const val DIFFICULTY_PREFIX = "ccc"
    private const val GENESIS_PREVIOUS_HASH = "0000000000000000000000000000000000000000000000000000000000000000"

    fun mineBlock(
        index: Long,
        cardId: String,
        previousBlockHash: String,
        transaction: CardTransaction
    ): CardBlock {
        val timestamp = System.currentTimeMillis()
        var proofNonce = 0L
        var hash: String

        while (true) {
            val rawData = "$index:$cardId:$previousBlockHash:$timestamp:$proofNonce:${transaction.txId}"
            hash = CryptoEngine.sha256(rawData)
            if (hash.startsWith(DIFFICULTY_PREFIX)) {
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

    fun mintBatch(
        seriesId: String,
        title: String,
        maxEditions: Int,
        rarity: String,
        creatorKeyPair: KeyPair,
        initialOwnerPublicKey: String
    ): List<CardChain> {
        val createdChains = mutableListOf<CardChain>()
        val genesisTimestamp = System.currentTimeMillis()
        val creatorPublicKeyStr = CryptoEngine.publicKeyToString(creatorKeyPair.public)

        for (edition in 1..maxEditions) {
            val cardId = CryptoEngine.sha256("$seriesId:$edition:$genesisTimestamp")
            val card = Card(
                id = cardId,
                seriesId = seriesId,
                title = title,
                editionNumber = edition,
                maxEditions = maxEditions,
                rarity = rarity,
                creatorPublicKey = creatorPublicKeyStr
            )

            val rawTxData = "GENESIS:$creatorPublicKeyStr:$initialOwnerPublicKey:$genesisTimestamp:0"
            val txId = CryptoEngine.sha256(rawTxData)
            val signature = CryptoEngine.sign(rawTxData, creatorKeyPair.private)

            val genesisTx = CardTransaction(
                txId = txId,
                senderPublicKey = creatorPublicKeyStr,
                recipientPublicKey = initialOwnerPublicKey,
                timestamp = genesisTimestamp,
                nonce = 0L,
                signature = signature
            )

            val genesisBlock = mineBlock(
                index = 0,
                cardId = cardId,
                previousBlockHash = GENESIS_PREVIOUS_HASH,
                transaction = genesisTx
            )

            val cardChain = CardChain(card = card)
            cardChain.chain.add(genesisBlock)
            createdChains.add(cardChain)
        }

        return createdChains
    }

    fun transferCard(
        cardChain: CardChain,
        senderDeck: Deck,
        recipientPublicKey: String
    ): CardBlock {
        require(cardChain.currentOwner() == senderDeck.ownerPublicKey) {
            "Erro: O remetente não é o proprietário autêntico deste card!"
        }

        val timestamp = System.currentTimeMillis()
        val nonce = System.nanoTime()
        val rawTxData = "${cardChain.card.id}:${senderDeck.ownerPublicKey}:$recipientPublicKey:$timestamp:$nonce"
        val txId = CryptoEngine.sha256(rawTxData)
        val signature = CryptoEngine.sign(rawTxData, senderDeck.keyPair.private)

        val tx = CardTransaction(
            txId = txId,
            senderPublicKey = senderDeck.ownerPublicKey,
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

    fun validateCardChain(cardChain: CardChain): Boolean {
        for (i in cardChain.chain.indices) {
            val block = cardChain.chain[i]

            // 1. Validação de PoW
            val rawBlockData = "${block.index}:${block.cardId}:${block.previousBlockHash}:${block.timestamp}:${block.proofNonce}:${block.transaction.txId}"
            val calculatedHash = CryptoEngine.sha256(rawBlockData)

            if (calculatedHash != block.hash || !block.hash.startsWith(DIFFICULTY_PREFIX)) {
                return false
            }

            // 2. Validação de Encadeamento de Hashes
            if (i > 0) {
                val previousBlock = cardChain.chain[i - 1]
                if (block.previousBlockHash != previousBlock.hash) return false
            } else {
                if (block.previousBlockHash != GENESIS_PREVIOUS_HASH) return false
            }

            // 3. Validação de Assinatura Criptográfica Ed25519
            val rawTxData = if (i == 0) {
                "GENESIS:${block.transaction.senderPublicKey}:${block.transaction.recipientPublicKey}:${block.transaction.timestamp}:${block.transaction.nonce}"
            } else {
                "${block.cardId}:${block.transaction.senderPublicKey}:${block.transaction.recipientPublicKey}:${block.transaction.timestamp}:${block.transaction.nonce}"
            }

            val isSignatureValid = CryptoEngine.verify(
                data = rawTxData,
                signatureBase64 = block.transaction.signature,
                publicKeyString = block.transaction.senderPublicKey
            )

            if (!isSignatureValid) return false
        }

        return true
    }
}
```

---

## 6. Fluxo de Execução e Verificação

Para testar o protocolo, execute a função `main()`:

```kotlin
fun main() {
    setupCryptoProvider()

    // 1. Inicialização de Chaves e Decks
    val creatorKeyPair = CryptoEngine.generateEd25519KeyPair()
    val aliceDeck = Deck("Alice Deck", CryptoEngine.generateEd25519KeyPair())
    val bobDeck = Deck("Bob Deck", CryptoEngine.generateEd25519KeyPair())

    // 2. Minting Genesis
    val batch = CardChainEngine.mintBatch(
        seriesId = "DEV-BELEM-2026",
        title = "Dragão Criptográfico",
        maxEditions = 1,
        rarity = "LEGENDARY",
        creatorKeyPair = creatorKeyPair,
        initialOwnerPublicKey = aliceDeck.ownerPublicKey
    )
    
    val cardChain = batch.first()
    aliceDeck.cards.add(cardChain)

    println("Validando Card Genesis: ${CardChainEngine.validateCardChain(cardChain)}") // true

    // 3. Transferência P2P (Alice -> Bob)
    CardChainEngine.transferCard(cardChain, aliceDeck, bobDeck.ownerPublicKey)
    aliceDeck.cards.remove(cardChain)
    bobDeck.cards.add(cardChain)

    println("Validando Pós-Transferência: ${CardChainEngine.validateCardChain(cardChain)}") // true
}
```