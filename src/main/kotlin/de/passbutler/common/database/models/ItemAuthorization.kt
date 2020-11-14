package de.passbutler.common.database.models

import de.passbutler.common.base.JSONSerializable
import de.passbutler.common.base.JSONSerializableDeserializer
import de.passbutler.common.base.getDate
import de.passbutler.common.base.putBoolean
import de.passbutler.common.base.putDate
import de.passbutler.common.base.putString
import de.passbutler.common.crypto.models.CryptographicKey
import de.passbutler.common.crypto.models.ProtectedValue
import de.passbutler.common.crypto.models.getProtectedValue
import de.passbutler.common.crypto.models.putProtectedValue
import de.passbutler.common.database.Synchronizable
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant

data class ItemAuthorization(
    val id: String,
    val userId: String,
    val itemId: String,
    val itemKey: ProtectedValue<CryptographicKey>,
    val readOnly: Boolean,
    override val deleted: Boolean,
    override val modified: Instant,
    override val created: Instant
) : Synchronizable, JSONSerializable {

    override val primaryField = id

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putString(SERIALIZATION_KEY_ID, id)
            putString(SERIALIZATION_KEY_USER_ID, userId)
            putString(SERIALIZATION_KEY_ITEM_ID, itemId)
            putProtectedValue(SERIALIZATION_KEY_ITEM_KEY, itemKey)
            putBoolean(SERIALIZATION_KEY_READONLY, readOnly)
            putBoolean(SERIALIZATION_KEY_DELETED, deleted)
            putDate(SERIALIZATION_KEY_MODIFIED, modified)
            putDate(SERIALIZATION_KEY_CREATED, created)
        }
    }

    object Deserializer : JSONSerializableDeserializer<ItemAuthorization>() {
        @Throws(JSONException::class)
        override fun deserialize(jsonObject: JSONObject): ItemAuthorization {
            return ItemAuthorization(
                id = jsonObject.getString(SERIALIZATION_KEY_ID),
                userId = jsonObject.getString(SERIALIZATION_KEY_USER_ID),
                itemId = jsonObject.getString(SERIALIZATION_KEY_ITEM_ID),
                itemKey = jsonObject.getProtectedValue(SERIALIZATION_KEY_ITEM_KEY),
                readOnly = jsonObject.getBoolean(SERIALIZATION_KEY_READONLY),
                deleted = jsonObject.getBoolean(SERIALIZATION_KEY_DELETED),
                modified = jsonObject.getDate(SERIALIZATION_KEY_MODIFIED),
                created = jsonObject.getDate(SERIALIZATION_KEY_CREATED)
            )
        }
    }

    companion object {
        private const val SERIALIZATION_KEY_ID = "id"
        private const val SERIALIZATION_KEY_USER_ID = "userId"
        private const val SERIALIZATION_KEY_ITEM_ID = "itemId"
        private const val SERIALIZATION_KEY_ITEM_KEY = "itemKey"
        private const val SERIALIZATION_KEY_READONLY = "readOnly"
        private const val SERIALIZATION_KEY_DELETED = "deleted"
        private const val SERIALIZATION_KEY_MODIFIED = "modified"
        private const val SERIALIZATION_KEY_CREATED = "created"
    }
}
