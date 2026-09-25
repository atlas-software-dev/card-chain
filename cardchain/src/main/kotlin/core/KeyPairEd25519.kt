package core

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters

data class KeyPairEd25519(
    val publicKeyBase64: String,
    val privateKeyParams: Ed25519PrivateKeyParameters,
    val publicKeyParams: Ed25519PublicKeyParameters
)