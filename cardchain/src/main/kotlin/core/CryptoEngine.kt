package core

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

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
