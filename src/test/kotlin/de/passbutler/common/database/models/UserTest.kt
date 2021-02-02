package de.passbutler.common.database.models

import de.passbutler.common.assertJSONObjectEquals
import de.passbutler.common.crypto.EncryptionAlgorithm
import de.passbutler.common.crypto.models.CryptographicKey
import de.passbutler.common.crypto.models.KeyDerivationInformation
import de.passbutler.common.crypto.models.ProtectedValue
import de.passbutler.common.hexToBytes
import de.passbutler.common.toDate
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UserTest {

    @Nested
    inner class DefaultUser {
        @Test
        fun `Serialize and deserialize a default User should result an equal object`() {
            val exampleUser = createExampleDefaultUser()

            val serializedUser = exampleUser.serialize()
            val deserializedUser = User.DefaultUserDeserializer.deserializeOrNull(serializedUser)

            assertEquals(exampleUser, deserializedUser)
        }

        @Test
        fun `Serialize a default User`() {
            val exampleUser = createExampleDefaultUser()
            val expectedSerialized = createSerializedExampleDefaultUser()

            assertJSONObjectEquals(expectedSerialized, exampleUser.serialize())
        }

        @Test
        fun `Deserialize a default User`() {
            val serializedUser = createSerializedExampleDefaultUser()
            val expectedUser = createExampleDefaultUser()

            assertEquals(expectedUser, User.DefaultUserDeserializer.deserializeOrNull(serializedUser))
        }

        @Test
        fun `Deserialize an invalid default User returns null`() {
            val serializedUser = JSONObject(
                """{"foo":"bar"}"""
            )
            val expectedUser = null

            assertEquals(expectedUser, User.DefaultUserDeserializer.deserializeOrNull(serializedUser))
        }

        private fun createExampleDefaultUser(): User {
            return User(
                id = "exampleId",
                username = "myUserName",
                fullName = "My Full Name",
                serverComputedAuthenticationHash = createTestServerComputedAuthenticationHash(),
                masterKeyDerivationInformation = createTestKeyDerivationInformation(),
                masterEncryptionKey = createTestProtectedValueMasterEncryptionKey(),
                itemEncryptionPublicKey = createTestItemEncryptionPublicKey(),
                itemEncryptionSecretKey = createTestItemEncryptionSecretKey(),
                settings = createTestProtectedValueSettings(),
                deleted = true,
                modified = "2019-12-27T12:00:01Z".toDate(),
                created = "2019-12-27T12:00:00Z".toDate()
            )
        }

        private fun createSerializedExampleDefaultUser(): JSONObject {
            return JSONObject(
                """
                {
                  "id": "exampleId",
                  "username": "myUserName",
                  "fullName": "My Full Name",
                  "serverComputedAuthenticationHash": "pbkdf2:sha256:150000${'$'}nww6C11M${'$'}241ac264e71f35826b8a475bdeb8c6b231a4de2b228f7af979f246c24b4905de",
                  "masterKeyDerivationInformation": {
                    "iterationCount": 1234,
                    "salt": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
                  },
                  "masterEncryptionKey": {
                    "initializationVector": "qqqqqqqqqqqqqqqq",
                    "encryptedValue": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "encryptionAlgorithm": "AES-256-GCM"
                  },
                  "itemEncryptionPublicKey": {
                    "key": "qrvM"
                  },
                  "itemEncryptionSecretKey": {
                    "initializationVector": "zMzMzMzMzMzMzMzM",
                    "encryptedValue": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "encryptionAlgorithm": "AES-256-GCM"
                  },
                  "settings": {
                    "initializationVector": "u7u7u7u7u7u7u7u7",
                    "encryptedValue": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "encryptionAlgorithm": "AES-256-GCM"
                  },
                  "deleted": true,
                  "modified": 1577448001000,
                  "created": 1577448000000
                }
                """.trimIndent()
            )
        }
    }

    @Nested
    inner class PartialUser {
        @Test
        fun `Serialize and deserialize a partial User should result an equal object`() {
            val exampleUser = createExamplePartialUser()

            val serializedUser = exampleUser.serialize()
            val deserializedUser = User.PartialUserDeserializer.deserializeOrNull(serializedUser)

            assertEquals(exampleUser, deserializedUser)
        }

        @Test
        fun `Serialize a partial User`() {
            val exampleUser = createExamplePartialUser()
            val expectedSerialized = createSerializedExamplePartialUser()

            assertJSONObjectEquals(expectedSerialized, exampleUser.serialize())
        }

        @Test
        fun `Deserialize a partial User`() {
            val serializedUser = createSerializedExamplePartialUser()
            val expectedUser = createExamplePartialUser()

            assertEquals(expectedUser, User.PartialUserDeserializer.deserializeOrNull(serializedUser))
        }

        @Test
        fun `Deserialize an invalid partial User returns null`() {
            val serializedUser = JSONObject(
                """{"foo":"bar"}"""
            )
            val expectedUser = null

            assertEquals(expectedUser, User.PartialUserDeserializer.deserializeOrNull(serializedUser))
        }

        private fun createExamplePartialUser(): User {
            return User(
                id = "exampleId",
                username = "myUserName",
                fullName = "My Full Name",
                serverComputedAuthenticationHash = null,
                masterKeyDerivationInformation = null,
                masterEncryptionKey = null,
                itemEncryptionPublicKey = createTestItemEncryptionPublicKey(),
                itemEncryptionSecretKey = null,
                settings = null,
                deleted = true,
                modified = "2019-12-27T12:00:01Z".toDate(),
                created = "2019-12-27T12:00:00Z".toDate()
            )
        }

        private fun createSerializedExamplePartialUser(): JSONObject {
            return JSONObject(
                """
                {
                  "id": "exampleId",
                  "username": "myUserName",
                  "fullName": "My Full Name",
                  "itemEncryptionPublicKey": {
                    "key": "qrvM"
                  },
                  "deleted": true,
                  "modified": 1577448001000,
                  "created": 1577448000000
                }
                """.trimIndent()
            )
        }
    }

    companion object {
        private fun createTestServerComputedAuthenticationHash(): String {
            return "pbkdf2:sha256:150000\$nww6C11M\$241ac264e71f35826b8a475bdeb8c6b231a4de2b228f7af979f246c24b4905de"
        }

        private fun createTestKeyDerivationInformation(): KeyDerivationInformation {
            val salt = "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes()
            val iterationCount = 1234
            val testMasterKeyDerivationInformation = KeyDerivationInformation(salt, iterationCount)
            return testMasterKeyDerivationInformation
        }

        private fun createTestProtectedValueMasterEncryptionKey(): ProtectedValue<CryptographicKey> {
            val testProtectedValueMasterEncryptionKey = ProtectedValue.createInstanceForTesting<CryptographicKey>(
                "AAAAAAAAAAAAAAAAAAAAAAAA".hexToBytes(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes(),
                EncryptionAlgorithm.Symmetric.AES256GCM
            )
            return testProtectedValueMasterEncryptionKey
        }

        private fun createTestItemEncryptionPublicKey() = CryptographicKey("AABBCC".hexToBytes())

        private fun createTestItemEncryptionSecretKey(): ProtectedValue<CryptographicKey> {
            val testProtectedValueMasterEncryptionKey = ProtectedValue.createInstanceForTesting<CryptographicKey>(
                "CCCCCCCCCCCCCCCCCCCCCCCC".hexToBytes(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes(),
                EncryptionAlgorithm.Symmetric.AES256GCM
            )
            return testProtectedValueMasterEncryptionKey
        }

        private fun createTestProtectedValueSettings(): ProtectedValue<UserSettings> {
            val testProtectedValueSettings = ProtectedValue.createInstanceForTesting<UserSettings>(
                "BBBBBBBBBBBBBBBBBBBBBBBB".hexToBytes(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes(),
                EncryptionAlgorithm.Symmetric.AES256GCM
            )
            return testProtectedValueSettings
        }
    }
}
