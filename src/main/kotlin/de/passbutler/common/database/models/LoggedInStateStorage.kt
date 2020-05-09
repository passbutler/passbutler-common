package de.passbutler.common.database.models

import de.passbutler.common.crypto.models.AuthToken
import de.passbutler.common.crypto.models.EncryptedValue
import java.net.URI
import java.util.*

interface LoggedInStateStorage {
    var username: String
    var userType: UserType
    var authToken: AuthToken?
    var serverUrl: URI?
    var lastSuccessfulSyncDate: Date?
    var encryptedMasterPassword: EncryptedValue?

    data class Implementation(
        override var username: String,
        override var userType: UserType,
        override var authToken: AuthToken?,
        override var serverUrl: URI?,
        override var lastSuccessfulSyncDate: Date?,
        override var encryptedMasterPassword: EncryptedValue?
    ) : LoggedInStateStorage {
        constructor(username: String, userType: UserType): this(username, userType, null, null, null, null)
        constructor(username: String, userType: UserType, serverUrl: URI): this(username, userType, null, serverUrl, null, null)
    }
}

enum class UserType {
    LOCAL,
    REMOTE;

    companion object {
        fun valueOfOrNull(name: String): UserType? {
            return values().find { it.name == name }
        }
    }
}
