package de.passbutler.common.database.models

import de.passbutler.common.base.JSONSerializable
import de.passbutler.common.base.JSONSerializableDeserializer
import de.passbutler.common.base.getDate
import de.passbutler.common.base.putBoolean
import de.passbutler.common.base.putDate
import de.passbutler.common.base.putString
import de.passbutler.common.crypto.models.ProtectedValue
import de.passbutler.common.crypto.models.getProtectedValue
import de.passbutler.common.crypto.models.putProtectedValue
import de.passbutler.common.database.Synchronizable
import org.json.JSONException
import org.json.JSONObject
import java.util.*

data class Item(
    val id: String,
    val userId: String,
    val data: ProtectedValue<ItemData>,
    override val deleted: Boolean,
    override val modified: Date,
    override val created: Date
) : Synchronizable, JSONSerializable {

    override val primaryField = id

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putString(SERIALIZATION_KEY_ID, id)
            putString(SERIALIZATION_KEY_USER_ID, userId)
            putProtectedValue(SERIALIZATION_KEY_DATA, data)
            putBoolean(SERIALIZATION_KEY_DELETED, deleted)
            putDate(SERIALIZATION_KEY_MODIFIED, modified)
            putDate(SERIALIZATION_KEY_CREATED, created)
        }
    }

    object Deserializer : JSONSerializableDeserializer<Item>() {
        @Throws(JSONException::class)
        override fun deserialize(jsonObject: JSONObject): Item {
            return Item(
                id = jsonObject.getString(SERIALIZATION_KEY_ID),
                userId = jsonObject.getString(SERIALIZATION_KEY_USER_ID),
                data = jsonObject.getProtectedValue(SERIALIZATION_KEY_DATA),
                deleted = jsonObject.getBoolean(SERIALIZATION_KEY_DELETED),
                modified = jsonObject.getDate(SERIALIZATION_KEY_MODIFIED),
                created = jsonObject.getDate(SERIALIZATION_KEY_CREATED)
            )
        }
    }

    companion object {
        private const val SERIALIZATION_KEY_ID = "id"
        private const val SERIALIZATION_KEY_USER_ID = "userId"
        private const val SERIALIZATION_KEY_DATA = "data"
        private const val SERIALIZATION_KEY_DELETED = "deleted"
        private const val SERIALIZATION_KEY_MODIFIED = "modified"
        private const val SERIALIZATION_KEY_CREATED = "created"
    }
}

data class ItemData(
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String
) : JSONSerializable {

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putString(SERIALIZATION_KEY_TITLE, title)
            putString(SERIALIZATION_KEY_USERNAME, username)
            putString(SERIALIZATION_KEY_PASSWORD, password)
            putString(SERIALIZATION_KEY_URL, url)
            putString(SERIALIZATION_KEY_NOTES, notes)
        }
    }

    object Deserializer : JSONSerializableDeserializer<ItemData>() {
        @Throws(JSONException::class)
        override fun deserialize(jsonObject: JSONObject): ItemData {
            return ItemData(
                title = jsonObject.getString(SERIALIZATION_KEY_TITLE),
                username = jsonObject.getString(SERIALIZATION_KEY_USERNAME),
                password = jsonObject.getString(SERIALIZATION_KEY_PASSWORD),
                url = jsonObject.getString(SERIALIZATION_KEY_URL),
                notes = jsonObject.getString(SERIALIZATION_KEY_NOTES)
            )
        }
    }

    companion object {
        private const val SERIALIZATION_KEY_TITLE = "title"
        private const val SERIALIZATION_KEY_USERNAME = "username"
        private const val SERIALIZATION_KEY_PASSWORD = "password"
        private const val SERIALIZATION_KEY_URL = "url"
        private const val SERIALIZATION_KEY_NOTES = "notes"
    }
}
