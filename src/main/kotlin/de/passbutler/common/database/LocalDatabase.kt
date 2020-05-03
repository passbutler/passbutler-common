package de.passbutler.common.database

import de.passbutler.common.crypto.models.CryptographicKey
import de.passbutler.common.crypto.models.KeyDerivationInformation
import de.passbutler.common.crypto.models.ProtectedValue
import de.passbutler.common.database.models.Item
import de.passbutler.common.database.models.ItemAuthorization
import de.passbutler.common.database.models.ItemData
import de.passbutler.common.database.models.generated.ItemModel
import de.passbutler.common.database.models.generated.ItemQueries
import de.passbutler.common.database.models.User
import de.passbutler.common.database.models.UserSettings
import de.passbutler.common.database.models.generated.ItemAuthorizationModel
import de.passbutler.common.database.models.generated.ItemAuthorizationQueries
import de.passbutler.common.database.models.generated.UserModel
import de.passbutler.common.database.models.generated.UserQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

const val LOCAL_DATABASE_SQL_FOREIGN_KEYS_ENABLE = "PRAGMA foreign_keys=TRUE;"
const val LOCAL_DATABASE_SQL_DEFER_FOREIGN_KEYS_ENABLE = "PRAGMA defer_foreign_keys=TRUE;"

class LocalRepository(
    private val localDatabase: PassButlerDatabase
) : UserDao by UserDao.Implementation(localDatabase.userQueries),
    ItemDao by ItemDao.Implementation(localDatabase.itemQueries),
    ItemAuthorizationDao by ItemAuthorizationDao.Implementation(localDatabase.itemAuthorizationQueries) {
    suspend fun reset() {
        withContext(Dispatchers.IO) {
            // TODO: Defer foreign keys first and vacuum finally?
            localDatabase.transaction {
                localDatabase.itemAuthorizationQueries.deleteAll()
                localDatabase.itemQueries.deleteAll()
                localDatabase.userQueries.deleteAll()
            }
        }
    }
}

interface UserDao {
    val userQueries: UserQueries

    suspend fun findAllUsers(): List<User> {
        return withContext(Dispatchers.IO) {
            userQueries.findAll().executeAsList().map { it.toUser() }
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
            itemQueries.findAll().executeAsList().map { it.toItem() }
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
            itemAuthorizationQueries.findAll().executeAsList().map { it.toItemAuthorization() }
        }
    }

    suspend fun findItemAuthorization(id: String): ItemAuthorization? {
        return withContext(Dispatchers.IO) {
            itemAuthorizationQueries.findById(id).executeAsOneOrNull()?.toItemAuthorization()
        }
    }

    suspend fun findItemAuthorizationForItem(item: Item): List<ItemAuthorization> {
        return withContext(Dispatchers.IO) {
            itemAuthorizationQueries.findForItem(item.id).executeAsList().map { it.toItemAuthorization() }
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

internal fun UserModel.toUser(): User {
    // TODO: Catch exception?
    return User(
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

internal fun ItemModel.toItem(): Item {
    // TODO: Catch exception?
    return Item(
        id = id,
        userId = userId,
        data = ProtectedValue.Deserializer<ItemData>().deserialize(data),
        deleted = deleted.toBoolean(),
        modified = modified.toDate(),
        created = created.toDate()
    )
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

internal fun ItemAuthorizationModel.toItemAuthorization(): ItemAuthorization {
    // TODO: Catch exception?
    return ItemAuthorization(
        id = id,
        userId = userId,
        itemId = itemId,
        itemKey = ProtectedValue.Deserializer<CryptographicKey>().deserialize(itemKey),
        readOnly = readOnly.toBoolean(),
        deleted = deleted.toBoolean(),
        modified = modified.toDate(),
        created = created.toDate()
    )
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