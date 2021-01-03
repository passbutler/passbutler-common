package de.passbutler.common.crypto.models

import de.passbutler.common.base.JSONSerializable
import de.passbutler.common.base.JSONSerializableDeserializer
import de.passbutler.common.base.putString
import org.json.JSONException
import org.json.JSONObject

/**
 * Wraps a authentication token string (actually a JSON Web Token) in a `JSONSerializable`.
 */
data class AuthToken(val token: String) : JSONSerializable {

    override fun serialize(): JSONObject {
        return JSONObject().apply {
            putString(SERIALIZATION_KEY_TOKEN, token)
        }
    }

    object Deserializer : JSONSerializableDeserializer<AuthToken>() {
        @Throws(JSONException::class)
        override fun deserialize(jsonObject: JSONObject): AuthToken {
            return AuthToken(
                token = jsonObject.getString(SERIALIZATION_KEY_TOKEN)
            )
        }
    }

    companion object {
        private const val SERIALIZATION_KEY_TOKEN = "token"
    }
}
