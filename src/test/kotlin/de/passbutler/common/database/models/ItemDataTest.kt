package de.passbutler.common.database.models

import de.passbutler.common.assertJSONObjectEquals
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ItemDataTest {

    @Test
    fun `Serialize and deserialize an ItemData should result an equal object`() {
        val exampleItemData = createExampleItemData()

        val serializedItemData = exampleItemData.serialize()
        val deserializedItemData = ItemData.Deserializer.deserializeOrNull(serializedItemData)

        assertEquals(exampleItemData, deserializedItemData)
    }

    @Test
    fun `Serialize an ItemData`() {
        val exampleItemData = createExampleItemData()
        val expectedSerialized = createSerializedExampleItemData()

        assertJSONObjectEquals(expectedSerialized, exampleItemData.serialize())
    }

    @Test
    fun `Deserialize an ItemData`() {
        val serializedItemData = createSerializedExampleItemData()
        val expectedItemData = createExampleItemData()

        assertEquals(expectedItemData, ItemData.Deserializer.deserializeOrNull(serializedItemData))
    }

    @Test
    fun `Serialize an ItemData without any tags`() {
        val exampleItemData = ItemData(
            title = "exampleTitle",
            username = "exampleUsername",
            password = "examplePassword",
            url = "exampleUrl",
            notes = "exampleNotes",
            tags = emptyList()
        )

        val expectedSerialized = JSONObject(
            """
            {
              "title": "exampleTitle",
              "username": "exampleUsername",
              "password": "examplePassword",
              "url": "exampleUrl",
              "notes": "exampleNotes",
              "tags": []
            }
            """.trimIndent()
        )

        assertJSONObjectEquals(expectedSerialized, exampleItemData.serialize())
    }

    @Test
    fun `Deserialize an ItemData without any tags`() {
        val serializedItemData = JSONObject(
            """
            {
              "title": "exampleTitle",
              "username": "exampleUsername",
              "password": "examplePassword",
              "url": "exampleUrl",
              "notes": "exampleNotes",
              "tags": []
            }
            """.trimIndent()
        )

        val expectedItemData = ItemData(
            title = "exampleTitle",
            username = "exampleUsername",
            password = "examplePassword",
            url = "exampleUrl",
            notes = "exampleNotes",
            tags = emptyList()
        )

        assertEquals(expectedItemData, ItemData.Deserializer.deserializeOrNull(serializedItemData))
    }

    @Test
    fun `Deserialize an invalid ItemData returns null`() {
        val serializedItemData = JSONObject(
            """{"foo":"bar"}"""
        )
        val expectedItemData = null

        assertEquals(expectedItemData, ItemData.Deserializer.deserializeOrNull(serializedItemData))
    }

    companion object {
        private fun createExampleItemData(): ItemData {
            return ItemData(
                title = "exampleTitle",
                username = "exampleUsername",
                password = "examplePassword",
                url = "exampleUrl",
                notes = "exampleNotes",
                tags = listOf("tag1", "tag2")
            )
        }

        private fun createSerializedExampleItemData(): JSONObject {
            return JSONObject(
                """
                {
                  "title": "exampleTitle",
                  "username": "exampleUsername",
                  "password": "examplePassword",
                  "url": "exampleUrl",
                  "notes": "exampleNotes",
                  "tags": ["tag1", "tag2"]
                }
                """.trimIndent()
            )
        }
    }
}
