package de.passbutler.common.crypto

import de.passbutler.common.base.Result
import javax.crypto.Cipher

interface BiometricsProviding {
    val isBiometricAvailable: Boolean

    fun obtainKeyInstance(): Result<Cipher>

    suspend fun generateKey(keyName: String): Result<Unit>

    suspend fun initializeKeyForEncryption(keyName: String, encryptionCipher: Cipher): Result<Unit>
    suspend fun initializeKeyForDecryption(keyName: String, decryptionCipher: Cipher, initializationVector: ByteArray): Result<Unit>

    suspend fun encryptData(encryptionCipher: Cipher, data: ByteArray): Result<ByteArray>
    suspend fun decryptData(decryptionCipher: Cipher, data: ByteArray): Result<ByteArray>

    suspend fun removeKey(keyName: String): Result<Unit>
}
