package de.passbutler.common.database

import com.squareup.sqldelight.db.SqlDriver
import de.passbutler.common.base.toURI
import de.passbutler.common.crypto.models.AuthToken
import de.passbutler.common.crypto.models.CryptographicKey
import de.passbutler.common.crypto.models.EncryptedValue
import de.passbutler.common.crypto.models.KeyDerivationInformation
import de.passbutler.common.crypto.models.ProtectedValue
import de.passbutler.common.database.models.Item
import de.passbutler.common.database.models.ItemAuthorization
import de.passbutler.common.database.models.ItemData
import de.passbutler.common.database.models.LoggedInStateStorage
import de.passbutler.common.database.models.User
import de.passbutler.common.database.models.UserSettings
import de.passbutler.common.database.models.UserType
import de.passbutler.common.database.models.generated.ItemAuthorizationModel
import de.passbutler.common.database.models.generated.ItemAuthorizationQueries
import de.passbutler.common.database.models.generated.ItemModel
import de.passbutler.common.database.models.generated.ItemQueries
import de.passbutler.common.database.models.generated.LoggedInStateStorageModel
import de.passbutler.common.database.models.generated.LoggedInStateStorageQueries
import de.passbutler.common.database.models.generated.UserModel
import de.passbutler.common.database.models.generated.UserQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tinylog.Logger
import java.util.*

const val LOCAL_DATABASE_SQL_FOREIGN_KEYS_ENABLE = "PRAGMA foreign_keys=TRUE;"
const val LOCAL_DATABASE_SQL_FOREIGN_KEYS_DISABLE = "PRAGMA foreign_keys=FALSE;"
const val LOCAL_DATABASE_SQL_DEFER_FOREIGN_KEYS_ENABLE = "PRAGMA defer_foreign_keys=TRUE;"
const val LOCAL_DATABASE_SQL_VACUUM = "VACUUM;"

class LocalRepository(
    private val localDatabase: PassButlerDatabase,
    private val driver: SqlDriver
) : LoggedInStateStorageDao by LoggedInStateStorageDao.Implementation(localDatabase.loggedInStateStorageQueries),
    UserDao by UserDao.Implementation(localDatabase.userQueries),
    ItemDao by ItemDao.Implementation(localDatabase.itemQueries),
    ItemAuthorizationDao by ItemAuthorizationDao.Implementation(localDatabase.itemAuthorizationQueries) {
    suspend fun reset() {
        withContext(Dispatchers.IO) {
            // Disable foreign key constraint checks before transaction as a preventative measure
            driver.executeWithoutParameters(LOCAL_DATABASE_SQL_FOREIGN_KEYS_DISABLE)

            localDatabase.transaction {
                // For this transaction explicit ignore foreign key constraints (additionally to general foreign key constraint deactivation)
                driver.executeWithoutParameters(LOCAL_DATABASE_SQL_DEFER_FOREIGN_KEYS_ENABLE)

                localDatabase.userQueries.deleteAll()
                localDatabase.itemQueries.deleteAll()
                localDatabase.itemAuthorizationQueries.deleteAll()

                localDatabase.loggedInStateStorageQueries.delete()
            }

            // Re-enable foreign key constraint checks and deallocate unused database space (vacuum) after transaction
            driver.executeWithoutParameters(LOCAL_DATABASE_SQL_FOREIGN_KEYS_ENABLE)
            driver.executeWithoutParameters(LOCAL_DATABASE_SQL_VACUUM)
        }
    }
}

internal fun SqlDriver.executeWithoutParameters(sql: String) {
    execute(identifier = null, sql = sql, parameters = 0)
}

interface LoggedInStateStorageDao {
    val loggedInStateStorageQueries: LoggedInStateStorageQueries

    suspend fun findLoggedInStateStorage(): LoggedInStateStorage? {
        return withContext(Dispatchers.IO) {
            loggedInStateStorageQueries.find(STATIC_ID).executeAsOneOrNull()?.toLoggedInStateStorage()
        }
    }

    suspend fun insertLoggedInStateStorage(loggedInStateStorage: LoggedInStateStorage) {
        withContext(Dispatchers.IO) {
            loggedInStateStorageQueries.insert(loggedInStateStorage.toLoggedInStateStorageModel())
        }
    }

    suspend fun updateLoggedInStateStorage(loggedInStateStorage: LoggedInStateStorage) {
        withContext(Dispatchers.IO) {
            val model = loggedInStateStorage.toLoggedInStateStorageModel()
            loggedInStateStorageQueries.update(
                username = model.username,
                userType = model.userType,
                authToken = model.authToken,
                serverUrl = model.serverUrl,
                lastSuccessfulSyncDate = model.lastSuccessfulSyncDate,
                encryptedMasterPassword = model.encryptedMasterPassword,
                id = STATIC_ID
            )
        }
    }

    class Implementation(override val loggedInStateStorageQueries: LoggedInStateStorageQueries) : LoggedInStateStorageDao

    companion object {
        internal const val STATIC_ID = 1L
    }
}

interface UserDao {
    val userQueries: UserQueries

    suspend fun findAllUsers(): List<User> {
        return withContext(Dispatchers.IO) {
            userQueries.findAll().executeAsList().mapNotNull { it.toUser() }
        }
    }

    suspend fun findUser(username: String): User? {
        return withContext(Dispatchers.IO) {
            userQueries.findById(username).executeAsOneOrNull()?.toUser()
        }
    }

    suspend fun insertUser(vararg users: User) {
        withContext(Dispatchers.IO) {
            userQueries.transaction {
                users.forEach { user ->
                    userQueries.insert(user.toUserModel())
                }
            }
        }
    }

    suspend fun updateUser(vararg users: User) {
        withContext(Dispatchers.IO) {
            userQueries.transaction {
                users.forEach { user ->
                    val model = user.toUserModel()
                    userQueries.update(
                        masterPasswordAuthenticationHash = model.masterPasswordAuthenticationHash,
                        masterKeyDerivationInformation = model.masterKeyDerivationInformation,
                        masterEncryptionKey = model.masterEncryptionKey,
                        itemEncryptionPublicKey = model.itemEncryptionPublicKey,
                        itemEncryptionSecretKey = model.itemEncryptionSecretKey,
                        settings = model.settings,
                        deleted = model.deleted,
                        modified = model.modified,
                        created = model.created,
                        username = model.username
                    )
                }
            }
        }
    }

    class Implementation(override val userQueries: UserQueries) : UserDao
}

interface ItemDao {
    val itemQueries: ItemQueries

    suspend fun findAllItems(): List<Item> {
        return withContext(Dispatchers.IO) {
            itemQueries.findAll().executeAsList().mapNotNull { it.toItem() }
        }
    }

    suspend fun findItem(id: String): Item? {
        return withContext(Dispatchers.IO) {
            itemQueries.findById(id).executeAsOneOrNull()?.toItem()
        }
    }

    suspend fun insertItem(vararg items: Item) {
        withContext(Dispatchers.IO) {
            itemQueries.transaction {
                items.forEach { item ->
                    itemQueries.insert(item.toItemModel())
                }
            }
        }
    }

    suspend fun updateItem(vararg items: Item) {
        withContext(Dispatchers.IO) {
            itemQueries.transaction {
                items.forEach { item ->
                    val model = item.toItemModel()
                    itemQueries.update(
                        userId = model.userId,
                        data = model.data,
                        deleted = model.deleted,
                        modified = model.modified,
                        created = model.created,
                        id = model.id
                    )
                }
            }
        }
    }

    class Implementation(override val itemQueries: ItemQueries) : ItemDao
}

interface ItemAuthorizationDao {
    val itemAuthorizationQueries: ItemAuthorizationQueries

    suspend fun findAllItemAuthorizations(): List<ItemAuthorization> {
        return withContext(Dispatchers.IO) {
            itemAuthorizationQueries.findAll().executeAsList().mapNotNull { it.toItemAuthorization() }
        }
    }

    suspend fun findItemAuthorization(id: String): ItemAuthorization? {
        return withContext(Dispatchers.IO) {
            itemAuthorizationQueries.findById(id).executeAsOneOrNull()?.toItemAuthorization()
        }
    }

    suspend fun findItemAuthorizationForItem(item: Item): List<ItemAuthorization> {
        return withContext(Dispatchers.IO) {
            itemAuthorizationQueries.findForItem(item.id).executeAsList().mapNotNull { it.toItemAuthorization() }
        }
    }

    suspend fun insertItemAuthorization(vararg itemAuthorizations: ItemAuthorization) {
        withContext(Dispatchers.IO) {
            itemAuthorizationQueries.transaction {
                itemAuthorizations.forEach { itemAuthorization ->
                    itemAuthorizationQueries.insert(itemAuthorization.toItemAuthorizationModel())
                }
            }
        }
    }

    suspend fun updateItemAuthorization(vararg itemAuthorizations: ItemAuthorization) {
        withContext(Dispatchers.IO) {
            itemAuthorizationQueries.transaction {
                itemAuthorizations.forEach { itemAuthorization ->
                    val model = itemAuthorization.toItemAuthorizationModel()
                    itemAuthorizationQueries.update(
                        userId = model.userId,
                        itemId = model.itemId,
                        itemKey = model.itemKey,
                        readOnly = model.readOnly,
                        deleted = model.deleted,
                        modified = model.modified,
                        created = model.created,
                        id = model.id
                    )
                }
            }
        }
    }

    class Implementation(override val itemAuthorizationQueries: ItemAuthorizationQueries) : ItemAuthorizationDao
}

/**
 * General type converters
 */

fun Long.toBoolean(): Boolean = this == 1L
fun Boolean.toLong(): Long = if (this) 1L else 0L

fun Long.toDate(): Date = Date(this)
fun Date.toLong(): Long = this.time

/**
 * Model type converters
 */

internal fun LoggedInStateStorageModel.toLoggedInStateStorage(): LoggedInStateStorage? {
    return try {
        LoggedInStateStorage.Implementation(
            username = username,
            userType = UserType.valueOf(userType),
            authToken = authToken?.let { AuthToken.Deserializer.deserialize(it) },
            serverUrl = serverUrl?.toURI(),
            lastSuccessfulSyncDate = lastSuccessfulSyncDate?.takeIf { it > 0 }?.let { Date(it) },
            encryptedMasterPassword = encryptedMasterPassword?.let { EncryptedValue.Deserializer.deserialize(it) }
        )
    } catch (exception: Exception) {
        Logger.warn(exception, "The LoggedInStateStorageModel could not be converted!")
        null
    }
}

internal fun LoggedInStateStorage.toLoggedInStateStorageModel(): LoggedInStateStorageModel {
    return LoggedInStateStorageModel.Impl(
        id = LoggedInStateStorageDao.STATIC_ID,
        username = username,
        userType = userType.name,
        authToken = authToken?.serialize()?.toString(),
        serverUrl = serverUrl?.toString(),
        lastSuccessfulSyncDate = lastSuccessfulSyncDate?.time,
        encryptedMasterPassword = encryptedMasterPassword?.serialize()?.toString()
    )
}

internal fun UserModel.toUser(): User? {
    return try {
        User(
            username = username,
            masterPasswordAuthenticationHash = masterPasswordAuthenticationHash,
            masterKeyDerivationInformation = masterKeyDerivationInformation?.let { KeyDerivationInformation.Deserializer.deserialize(it) },
            masterEncryptionKey = masterEncryptionKey?.let { ProtectedValue.Deserializer<CryptographicKey>().deserialize(it) },
            itemEncryptionPublicKey = CryptographicKey.Deserializer.deserialize(itemEncryptionPublicKey),
            itemEncryptionSecretKey = itemEncryptionSecretKey?.let { ProtectedValue.Deserializer<CryptographicKey>().deserialize(it) },
            settings = settings?.let { ProtectedValue.Deserializer<UserSettings>().deserialize(it) },
            deleted = deleted.toBoolean(),
            modified = modified.toDate(),
            created = created.toDate()
        )
    } catch (exception: Exception) {
        Logger.warn(exception, "The UserModel could not be converted!")
        null
    }
}

internal fun User.toUserModel(): UserModel {
    return UserModel.Impl(
        username = username,
        masterPasswordAuthenticationHash = masterPasswordAuthenticationHash,
        masterKeyDerivationInformation = masterKeyDerivationInformation?.serialize()?.toString(),
        masterEncryptionKey = masterEncryptionKey?.serialize()?.toString(),
        itemEncryptionPublicKey = itemEncryptionPublicKey.serialize().toString(),
        itemEncryptionSecretKey = itemEncryptionSecretKey?.serialize()?.toString(),
        settings = settings?.serialize()?.toString(),
        deleted = deleted.toLong(),
        modified = modified.toLong(),
        created = created.toLong()
    )
}

internal fun ItemModel.toItem(): Item? {
    return try {
        Item(
            id = id,
            userId = userId,
            data = ProtectedValue.Deserializer<ItemData>().deserialize(data),
            deleted = deleted.toBoolean(),
            modified = modified.toDate(),
            created = created.toDate()
        )
    } catch (exception: Exception) {
        Logger.warn(exception, "The ItemModel could not be converted!")
        null
    }
}

internal fun Item.toItemModel(): ItemModel {
    return ItemModel.Impl(
        id = id,
        userId = userId,
        data = data.serialize().toString(),
        deleted = deleted.toLong(),
        modified = modified.toLong(),
        created = created.toLong()
    )
}

internal fun ItemAuthorizationModel.toItemAuthorization(): ItemAuthorization? {
    return try {
        ItemAuthorization(
            id = id,
            userId = userId,
            itemId = itemId,
            itemKey = ProtectedValue.Deserializer<CryptographicKey>().deserialize(itemKey),
            readOnly = readOnly.toBoolean(),
            deleted = deleted.toBoolean(),
            modified = modified.toDate(),
            created = created.toDate()
        )
    } catch (exception: Exception) {
        Logger.warn(exception, "The ItemAuthorizationModel could not be converted!")
        null
    }
}

internal fun ItemAuthorization.toItemAuthorizationModel(): ItemAuthorizationModel {
    return ItemAuthorizationModel.Impl(
        id = id,
        userId = userId,
        itemId = itemId,
        itemKey = itemKey.serialize().toString(),
        readOnly = readOnly.toLong(),
        deleted = deleted.toLong(),
        modified = modified.toLong(),
        created = created.toLong()
    )
}