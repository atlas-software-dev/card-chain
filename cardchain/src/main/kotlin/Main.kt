import core.CardChainEngine
import core.CryptoEngine
import domain.CardBlock
import domain.Deck
import kotlinx.serialization.json.Json
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

// Registrar o Provedor Bouncy Castle na JVM


// Configuração do Serializador JSON determinístico (Ordem Estável)
val canonicalJson = Json {
    prettyPrint = false
    encodeDefaults = true
}


fun main() {
    if (Security.getProvider("BC") == null) {
        Security.addProvider(BouncyCastleProvider())
    }
    test2()
}



fun test1() {

    println("==========================================================")
    println("       CARDCHAIN CORE (Ed25519 + BouncyCastle)           ")
    println("==========================================================")

    // STEP 1: Gerar Par de Chaves Ed25519 para as Entidades
    val creatorKeyPair = CryptoEngine.generateEd25519KeyPair()
    val aliceKeyPair = CryptoEngine.generateEd25519KeyPair()
    val bobKeyPair = CryptoEngine.generateEd25519KeyPair()

    val aliceDeck = Deck(name = "Deck da Alice", keyPair = aliceKeyPair)
    val bobDeck = Deck(name = "Deck do Bob", keyPair = bobKeyPair)

    println("Chave Pública do Emissor Oficial: ${creatorKeyPair.publicKeyBase64.take(20)}...")
    println("Chave Pública da Alice:           ${aliceDeck.ownerPublicKey.take(20)}...")
    println("Chave Pública do Bob:             ${bobDeck.ownerPublicKey.take(20)}...")

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
    println("Transferindo Card da Alice para o Bob (Assinando com Ed25519)...")
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
    println("⚠SIMULAÇÃO DE ATAQUE: Adulterando Transação no Bloco 1")
    println("----------------------------------------------------------")

    val hackerKeyPair = CryptoEngine.generateEd25519KeyPair()
    val targetBlock = cardToTrade.chain[1]

    // Tenta trocar a chave do remetente sem assinar novamente com a chave do hacker
    val forgedTransaction = targetBlock.transaction.copy(senderPublicKey = hackerKeyPair.publicKeyBase64)
    cardToTrade.chain[1] = targetBlock.copy(transaction = forgedTransaction)

    // Revalida após ataque
    CardChainEngine.validateCardChain(cardToTrade)
}

fun test2() {
    println("==========================================================")
    println("       CARDCHAIN - INICIALIZANDO TESTE DE DECKS           ")
    println("==========================================================")

    // 1. Inicialização de Chaves e Decks
    val creatorKeyPair = CryptoEngine.generateEd25519KeyPair()
    val aliceDeck = Deck("Deck da Alice", CryptoEngine.generateEd25519KeyPair())
    val bobDeck = Deck("Deck do Bob", CryptoEngine.generateEd25519KeyPair())

    // 2. Minting Genesis (Gerando Lote com 2 Edições)
    val batch = CardChainEngine.mintBatch(
        seriesId = "DEV-BELEM-2026",
        title = "Dragão Criptográfico",
        maxEditions = 10,
        rarity = "LEGENDARY",
        creatorKeyPair = creatorKeyPair,
        initialOwnerPublicKey = aliceDeck.ownerPublicKey
    )

    // Alice recebe todas as edições do lote inicial
    aliceDeck.cards.addAll(batch)

    // 3. Transferência P2P (Alice transfere apenas a Edição #1 para Bob)
    println("\nTransferindo Edição #1 da Alice para o Bob...")
    val cardToTransfer = aliceDeck.cards.find { it.card.editionNumber == 1 }!!

    CardChainEngine.transferCard(cardToTransfer, aliceDeck.keyPair, bobDeck.ownerPublicKey)

    // Atualiza o acervo local dos decks após a troca
    aliceDeck.cards.remove(cardToTransfer)
    bobDeck.cards.add(cardToTransfer)

    // 5. Imprime o log detalhado dos dois decks no estado final
    printDeckState(aliceDeck)
    printDeckState(bobDeck)
}

// 4. Função auxiliar para imprimir todas as informações de um Deck
fun printDeckState(deck: Deck) {
    println("\n==========================================================")
    println(" DECK: ${deck.name.uppercase()}")
    println(" ENDEREÇO (PUB KEY): ${deck.ownerPublicKey}")
    println(" TOTAL DE CARDS NA CARTEIRA: ${deck.cards.size}")
    println("==========================================================")

    if (deck.cards.isEmpty()) {
        println("  (Deck vazio)")
    }

    deck.cards.forEach { cardChain ->
        val card = cardChain.card
        println("\n  CARD: [${card.title}] - Edição #${card.editionNumber}/${card.maxEditions} (${card.rarity})")
        println("     ID do Ativo : ${card.id}")
        println("     Série       : ${card.seriesId}")
        println("     Emissor(Pub): ${card.creatorPublicKey.take(30)}...")
        println("     Dono Atual  : ${cardChain.currentOwner().take(30)}...")

        println("     --- HISTÓRICO DA BLOCKCHAIN (${cardChain.chain.size} blocos) ---")
        cardChain.chain.forEach { block ->
            println("       BLOCO #${block.index}")
            println("          Hash Atual : ${block.hash}")
            println("          Hash Ant.  : ${block.previousBlockHash}")
            println("          Timestamp  : ${block.timestamp} | PoW Nonce: ${block.proofNonce}")
            println("          [Transação]")
            println("            ID Tx  : ${block.transaction.txId}")
            println("            De     : ${block.transaction.senderPublicKey.take(40)}...")
            println("            Para   : ${block.transaction.recipientPublicKey.take(40)}...")
            println("            Assin. : ${block.transaction.signature.take(40)}...")
            println("       ----------------------------------------------------")
        }
    }
}