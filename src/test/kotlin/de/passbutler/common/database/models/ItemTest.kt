package de.passbutler.common.database.models

import de.passbutler.common.assertJSONObjectEquals
import de.passbutler.common.crypto.EncryptionAlgorithm
import de.passbutler.common.crypto.models.ProtectedValue
import de.passbutler.common.hexToBytes
import de.passbutler.common.toDate
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ItemTest {

    @Nested
    inner class DefaultItem {
        @Test
        fun `Serialize and deserialize a default Item should result an equal object`() {
            val exampleItem = createExampleDefaultItem()

            val serializedItem = exampleItem.serialize()
            val deserializedItem = Item.Deserializer.deserializeOrNull(serializedItem)

            assertEquals(exampleItem, deserializedItem)
        }

        @Test
        fun `Serialize a default Item`() {
            val exampleItem = createExampleDefaultItem()
            val expectedSerialized = createSerializedExampleDefaultItem()

            assertJSONObjectEquals(expectedSerialized, exampleItem.serialize())
        }

        @Test
        fun `Deserialize a default Item`() {
            val serializedItem = createSerializedExampleDefaultItem()
            val expectedItem = createExampleDefaultItem()

            assertEquals(expectedItem, Item.Deserializer.deserializeOrNull(serializedItem))
        }

        @Test
        fun `Deserialize an invalid default Item returns null`() {
            val serializedItem = JSONObject(
                """{"foo":"bar"}"""
            )
            val expectedItem = null

            assertEquals(expectedItem, Item.Deserializer.deserializeOrNull(serializedItem))
        }

        private fun createExampleDefaultItem(): Item {
            val itemData = ProtectedValue.createInstanceForTesting<ItemData>(
                "AAAAAAAAAAAAAAAAAAAAAAAA".hexToBytes(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes(),
                EncryptionAlgorithm.Symmetric.AES256GCM
            )

            return Item(
                id = "exampleId",
                userId = "exampleUserId",
                data = itemData,
                deleted = true,
                modified = "2019-12-27T12:00:01Z".toDate(),
                created = "2019-12-27T12:00:00Z".toDate()
            )
        }

        private fun createSerializedExampleDefaultItem(): JSONObject {
            return JSONObject(
                """
                {
                  "id": "exampleId",
                  "userId": "exampleUserId",
                  "data": {
                    "encryptedValue": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "encryptionAlgorithm": "AES-256-GCM",
                    "initializationVector": "qqqqqqqqqqqqqqqq"
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
    inner class PartialItem {
        @Test
        fun `Serialize and deserialize a partial Item should result an equal object`() {
            val exampleItem = createExamplePartialItem()

            val serializedItem = exampleItem.serialize()
            val deserializedItem = Item.Deserializer.deserializeOrNull(serializedItem)

            assertEquals(exampleItem, deserializedItem)
        }

        @Test
        fun `Serialize a partial Item`() {
            val exampleItem = createExamplePartialItem()
            val expectedSerialized = createSerializedExamplePartialItem()

            assertJSONObjectEquals(expectedSerialized, exampleItem.serialize())
        }

        @Test
        fun `Deserialize a partial Item`() {
            val serializedItem = createSerializedExamplePartialItem()
            val expectedItem = createExamplePartialItem()

            assertEquals(expectedItem, Item.Deserializer.deserializeOrNull(serializedItem))
        }

        @Test
        fun `Deserialize an invalid partial Item returns null`() {
            val serializedItem = JSONObject(
                """{"foo":"bar"}"""
            )
            val expectedItem = null

            assertEquals(expectedItem, Item.Deserializer.deserializeOrNull(serializedItem))
        }

        private fun createExamplePartialItem(): Item {
            return Item(
                id = "exampleId",
                userId = "exampleUserId",
                data = null,
                deleted = true,
                modified = "2019-12-27T12:00:01Z".toDate(),
                created = "2019-12-27T12:00:00Z".toDate()
            )
        }

        private fun createSerializedExamplePartialItem(): JSONObject {
            return JSONObject(
                """
                {
                  "id": "exampleId",
                  "userId": "exampleUserId",
                  "deleted": true,
                  "modified": 1577448001000,
                  "created": 1577448000000
                }
                """.trimIndent()
            )
        }
    }
}
