package de.passbutler.common.database.models

import de.passbutler.common.assertJSONObjectEquals
import de.passbutler.common.crypto.EncryptionAlgorithm
import de.passbutler.common.crypto.models.CryptographicKey
import de.passbutler.common.crypto.models.ProtectedValue
import de.passbutler.common.hexToBytes
import de.passbutler.common.toDate
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ItemAuthorizationTest {

    @Test
    fun `Serialize and deserialize an ItemAuthorization should result an equal object`() {
        val exampleItemAuthorization = createExampleItemAuthorization()

        val serializedItemAuthorization = exampleItemAuthorization.serialize()
        val deserializedItemAuthorization = ItemAuthorization.Deserializer.deserializeOrNull(serializedItemAuthorization)

        assertEquals(exampleItemAuthorization, deserializedItemAuthorization)
    }

    @Test
    fun `Serialize an ItemAuthorization`() {
        val exampleItemAuthorization = createExampleItemAuthorization()
        val expectedSerialized = createSerializedExampleItemAuthorization()

        assertJSONObjectEquals(expectedSerialized, exampleItemAuthorization.serialize())
    }

    @Test
    fun `Deserialize an ItemAuthorization`() {
        val serializedItemAuthorization = createSerializedExampleItemAuthorization()
        val expectedItemAuthorization = createExampleItemAuthorization()

        assertEquals(expectedItemAuthorization, ItemAuthorization.Deserializer.deserializeOrNull(serializedItemAuthorization))
    }

    @Test
    fun `Deserialize an invalid ItemAuthorization returns null`() {
        val serializedItemAuthorization = JSONObject(
            """{"foo":"bar"}"""
        )
        val expectedItemAuthorization = null

        assertEquals(expectedItemAuthorization, ItemAuthorization.Deserializer.deserializeOrNull(serializedItemAuthorization))
    }

    companion object {
        private fun createExampleItemAuthorization(): ItemAuthorization {
            val itemKey = ProtectedValue.createInstanceForTesting<CryptographicKey>(
                "AAAAAAAAAAAAAAAAAAAAAAAA".hexToBytes(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes(),
                EncryptionAlgorithm.Symmetric.AES256GCM
            )

            return ItemAuthorization(
                id = "exampleId",
                userId = "exampleUserId",
                itemId = "exampleItemId",
                itemKey = itemKey,
                readOnly = true,
                deleted = true,
                modified = "2019-12-27T12:00:01Z".toDate(),
                created = "2019-12-27T12:00:00Z".toDate()
            )
        }

        private fun createSerializedExampleItemAuthorization(): JSONObject {
            return JSONObject(
                """
                {
                  "id": "exampleId",
                  "itemId": "exampleItemId",
                  "userId": "exampleUserId",
                  "itemKey": {
                    "encryptedValue": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "encryptionAlgorithm": "AES-256-GCM",
                    "initializationVector": "qqqqqqqqqqqqqqqq"
                  },
                  "readOnly": true,
                  "deleted": true,
                  "modified": 1577448001000,
                  "created": 1577448000000
                }
                """.trimIndent()
            )
        }
    }
}
