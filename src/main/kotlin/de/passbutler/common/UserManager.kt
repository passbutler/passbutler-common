package de.passbutler.common

import com.squareup.sqldelight.Query
import de.passbutler.common.base.Bindable
import de.passbutler.common.base.BuildInformationProviding
import de.passbutler.common.base.Failure
import de.passbutler.common.base.MutableBindable
import de.passbutler.common.base.Result
import de.passbutler.common.base.SignalEmitter
import de.passbutler.common.base.Success
import de.passbutler.common.base.byteSize
import de.passbutler.common.base.clear
import de.passbutler.common.base.resultOrThrowException
import de.passbutler.common.crypto.Derivation
import de.passbutler.common.crypto.EncryptionAlgorithm
import de.passbutler.common.crypto.MASTER_KEY_BIT_LENGTH
import de.passbutler.common.crypto.MASTER_KEY_ITERATION_COUNT
import de.passbutler.common.crypto.RandomGenerator
import de.passbutler.common.crypto.ServerComputedAuthenticationHash
import de.passbutler.common.crypto.models.CryptographicKey
import de.passbutler.common.crypto.models.KeyDerivationInformation
import de.passbutler.common.crypto.models.ProtectedValue
import de.passbutler.common.database.AuthWebservice
import de.passbutler.common.database.Differentiation
import de.passbutler.common.database.LocalRepository
import de.passbutler.common.database.SynchronizationTask
import de.passbutler.common.database.UserWebservice
import de.passbutler.common.database.models.Item
import de.passbutler.common.database.models.ItemAuthorization
import de.passbutler.common.database.models.LoggedInStateStorage
import de.passbutler.common.database.models.User
import de.passbutler.common.database.models.UserSettings
import de.passbutler.common.database.models.UserType
import de.passbutler.common.database.remoteChangedItems
import de.passbutler.common.database.requestWithResult
import de.passbutler.common.database.requestWithoutResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.tinylog.kotlin.Logger
import java.net.SocketTimeoutException
import java.net.URI
import java.time.Instant
import java.util.*

class UserManager(val localRepository: LocalRepository, val buildInformationProvider: BuildInformationProviding) {

    val loggedInStateStorage: Bindable<LoggedInStateStorage?>
        get() = _loggedInStateStorage

    val webservices: Bindable<Webservices?>
        get() = _webservices

    val itemsOrItemAuthorizationsChanged = SignalEmitter()

    private val itemsOrItemAuthorizationsQueryListener = ItemsOrItemAuthorizationsQueryListener()

    private val _loggedInStateStorage = MutableBindable<LoggedInStateStorage?>(null)
    private val _webservices = MutableBindable<Webservices?>(null)

    init {
        Logger.debug("Create new UserManager ($this)")

        // Listen for complete application lifecycle for repository changes
        localRepository.itemQueries.findAll().addListener(itemsOrItemAuthorizationsQueryListener)
        localRepository.itemAuthorizationQueries.findAll().addListener(itemsOrItemAuthorizationsQueryListener)
    }

    suspend fun loginRemoteUser(username: String, masterPassword: String, serverUrlString: String): Result<LoggedInUserResult.LoggedIn.PerformedLogin> {
        return try {
            val serverUrl = URI.create(serverUrlString)
            val createdLoggedInStateStorage = LoggedInStateStorage.Implementation(
                username = username,
                userType = UserType.REMOTE,
                serverUrl = serverUrl
            )

            // Create logged-in state storage first to be sure, auth token can be applied
            localRepository.insertLoggedInStateStorage(createdLoggedInStateStorage)
            _loggedInStateStorage.value = createdLoggedInStateStorage

            val createdAuthWebservice = createAuthWebservice(serverUrl, username, masterPassword, buildInformationProvider)
            val createdUserWebservice = createUserWebservice(serverUrl, createdAuthWebservice, this)

            _webservices.value = Webservices(createdAuthWebservice, createdUserWebservice)

            val newUser = createdUserWebservice.requestWithResult { getUserDetails() }.resultOrThrowException()
            localRepository.insertUser(newUser)

            Success(LoggedInUserResult.LoggedIn.PerformedLogin(newUser))
        } catch (exception: Exception) {
            Logger.warn("The user could not be logged in - reset logged-in user to avoid corrupt state")
            logoutUser(LogoutBehaviour.ClearDatabase)

            Failure(exception)
        }
    }

    suspend fun loginLocalUser(username: String, masterPassword: String): Result<LoggedInUserResult.LoggedIn.PerformedLogin> {
        var masterKey: ByteArray? = null
        var masterEncryptionKey: ByteArray? = null

        return try {
            val createdLoggedInStateStorage = LoggedInStateStorage.Implementation(
                username = username,
                userType = UserType.LOCAL
            )

            localRepository.insertLoggedInStateStorage(createdLoggedInStateStorage)
            _loggedInStateStorage.value = createdLoggedInStateStorage

            val newUserId = UUID.randomUUID().toString()
            val serverComputedAuthenticationHash = deriveServerComputedAuthenticationHash(username, masterPassword)
            val masterKeyDerivationInformation = createMasterKeyDerivationInformation()

            masterKey = Derivation.deriveMasterKey(masterPassword, masterKeyDerivationInformation).resultOrThrowException()
            masterEncryptionKey = EncryptionAlgorithm.Symmetric.AES256GCM.generateEncryptionKey().resultOrThrowException()

            val serializableMasterEncryptionKey = CryptographicKey(masterEncryptionKey)
            val protectedMasterEncryptionKey = ProtectedValue.create(EncryptionAlgorithm.Symmetric.AES256GCM, masterKey, serializableMasterEncryptionKey).resultOrThrowException()

            val (itemEncryptionPublicKey, protectedItemEncryptionSecretKey) = generateItemEncryptionKeyPair(masterEncryptionKey)
            val protectedUserSettings = createUserSettings(masterEncryptionKey)
            val currentDate = Instant.now()

            val newUser = User(
                newUserId,
                username,
                serverComputedAuthenticationHash,
                masterKeyDerivationInformation,
                protectedMasterEncryptionKey,
                itemEncryptionPublicKey,
                protectedItemEncryptionSecretKey,
                protectedUserSettings,
                false,
                currentDate,
                currentDate
            )

            localRepository.insertUser(newUser)

            Success(LoggedInUserResult.LoggedIn.PerformedLogin(newUser))
        } catch (exception: Exception) {
            Logger.warn("The user could not be logged in - reset logged-in user to avoid corrupt state")
            logoutUser(LogoutBehaviour.ClearDatabase)

            Failure(exception)
        } finally {
            // Always active clear all sensible data before returning method
            masterKey?.clear()
            masterEncryptionKey?.clear()
        }
    }

    suspend fun registerLocalUser(loggedInUser: User, serverUrlString: String, invitationCode: String, masterPassword: String): Result<Unit> {
        return try {
            val serverUrl = URI.create(serverUrlString)

            val username = loggedInUser.username
            val createdAuthWebservice = createAuthWebservice(serverUrl, username, masterPassword, buildInformationProvider)
            val createdUserWebservice = createUserWebservice(serverUrl, createdAuthWebservice, this)

            createdUserWebservice.requestWithoutResult { registerUser(invitationCode, loggedInUser) }.resultOrThrowException()

            _webservices.value = Webservices(createdAuthWebservice, createdUserWebservice)

            // If everything worked, update logged-in state storage
            updateLoggedInStateStorage {
                it.userType = UserType.REMOTE
                it.serverUrl = serverUrl
            }

            Success(Unit)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    suspend fun restoreLoggedInUser(): Result<LoggedInUserResult.LoggedIn.RestoredLogin> {
        val restoredLoggedInStateStorage = localRepository.findLoggedInStateStorage()
        val restoredLoggedInUser = restoredLoggedInStateStorage?.username?.let { loggedInUsername ->
            localRepository.findUserByUsername(loggedInUsername)
        }

        return if (restoredLoggedInUser != null) {
            _loggedInStateStorage.value = restoredLoggedInStateStorage
            Success(LoggedInUserResult.LoggedIn.RestoredLogin(restoredLoggedInUser))
        } else {
            Failure(LoggedInUserNotFoundException)
        }
    }

    suspend fun restoreWebservices(masterPassword: String) {
        Logger.debug("Restore webservices")

        try {
            val serverUrl = loggedInStateStorage.value?.serverUrl ?: throw IllegalStateException("The server url is null!")
            val username = loggedInStateStorage.value?.username ?: throw IllegalStateException("The username is null!")

            _webservices.value = webservices.value ?: run {
                val createdAuthWebservice = createAuthWebservice(serverUrl, username, masterPassword, buildInformationProvider)
                val createdUserWebservice = createUserWebservice(serverUrl, createdAuthWebservice, this)
                Webservices(createdAuthWebservice, createdUserWebservice)
            }
        } catch (exception: Exception) {
            Logger.warn(exception, "The webservices could not be restored")
        }
    }

    suspend fun reinitializeWebservices(masterPassword: String) {
        _webservices.value = null
        restoreWebservices(masterPassword)
    }

    suspend fun updateLoggedInStateStorage(block: (LoggedInStateStorage) -> Unit) {
        val updatedLoggedInStateStorage = (loggedInStateStorage.value as? LoggedInStateStorage.Implementation)?.copy() ?: throw LoggedInStateStorageUninitializedException
        block.invoke(updatedLoggedInStateStorage)

        localRepository.updateLoggedInStateStorage(updatedLoggedInStateStorage)

        _loggedInStateStorage.value = updatedLoggedInStateStorage
    }

    suspend fun updateUser(loggedInUser: User): Result<Unit> {
        Logger.debug("Update user")

        val loggedInStateStorage = loggedInStateStorage.value ?: throw LoggedInStateStorageUninitializedException

        return try {
            // First update on remote (in case of remote user) to be sure the new authentication hash is updated
            if (loggedInStateStorage.userType == UserType.REMOTE) {
                val userWebservice = webservices.value?.userWebservice ?: throw UserWebserviceUninitializedException
                userWebservice.requestWithoutResult { setUserDetails(loggedInUser) }.resultOrThrowException()
            }

            // Then update in locale database
            localRepository.updateUser(loggedInUser)

            Success(Unit)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    suspend fun synchronize(loggedInUser: User): Result<Unit> {
        Logger.debug("Synchronize")

        val synchronizeResults = mutableListOf<Result<Differentiation.Result<*>>>()

        // Execute each task synchronously
        for (task in createSynchronizationTasks(loggedInUser)) {
            val synchronizeTaskName = task.javaClass.simpleName

            Logger.debug("Starting task '$synchronizeTaskName'")
            val result = task.synchronize()

            val printableResult = when (result) {
                is Success -> "${result.javaClass.simpleName} (${result.result})"
                is Failure -> result.javaClass.simpleName
            }
            Logger.debug("Finished task '$synchronizeTaskName' with result: $printableResult")

            synchronizeResults.add(result)

            // Do not stop if a task failed (otherwise later tasks may never synced if prior task failed) - except for timeout
            if ((result as? Failure)?.throwable is SocketTimeoutException) {
                Logger.debug("Skip all other tasks because '$synchronizeTaskName' failed with timeout")
                break
            }
        }

        val firstFailedTask = synchronizeResults.filterIsInstance(Failure::class.java).firstOrNull()

        return if (firstFailedTask != null) {
            Failure(firstFailedTask.throwable)
        } else {
            updateLoggedInStateStorage {
                it.lastSuccessfulSyncDate = Instant.now()
            }

            Success(Unit)
        }
    }

    private fun createSynchronizationTasks(loggedInUser: User): List<SynchronizationTask> {
        val userWebservice = webservices.value?.userWebservice ?: throw UserWebserviceUninitializedException

        return listOf(
            UsersSynchronizationTask(localRepository, userWebservice, loggedInUser),
            UserDetailsSynchronizationTask(localRepository, userWebservice, loggedInUser),
            ItemsSynchronizationTask(localRepository, userWebservice, loggedInUser.id),
            ItemAuthorizationsSynchronizationTask(localRepository, userWebservice, loggedInUser.id)
        )
    }

    suspend fun logoutUser(logoutBehaviour: LogoutBehaviour): Result<LoggedInUserResult.LoggedOut> {
        Logger.debug("Logout user with logoutBehaviour = $logoutBehaviour")

        val clearDatabase = when (logoutBehaviour) {
            LogoutBehaviour.ClearDatabase -> true
            LogoutBehaviour.KeepDatabase -> false
        }

        _webservices.value = null
        _loggedInStateStorage.value = null

        if (clearDatabase) {
            Logger.debug("Clear database")
            localRepository.reset()
        }

        return Success(LoggedInUserResult.LoggedOut)
    }

    private inner class ItemsOrItemAuthorizationsQueryListener : Query.Listener {
        override fun queryResultsChanged() {
            itemsOrItemAuthorizationsChanged.emit()
        }
    }

    sealed class LogoutBehaviour {
        object ClearDatabase : LogoutBehaviour()
        object KeepDatabase : LogoutBehaviour()
    }
}

@Throws(Exception::class)
suspend fun deriveServerComputedAuthenticationHash(username: String, masterPassword: String): ServerComputedAuthenticationHash {
    val localComputedAuthenticationHash = Derivation.deriveLocalComputedAuthenticationHash(username, masterPassword).resultOrThrowException()
    val serverComputedAuthenticationHash = Derivation.deriveServerComputedAuthenticationHash(localComputedAuthenticationHash).resultOrThrowException()
    return serverComputedAuthenticationHash
}

private suspend fun createMasterKeyDerivationInformation(): KeyDerivationInformation {
    val masterKeySalt = RandomGenerator.generateRandomBytes(MASTER_KEY_BIT_LENGTH.byteSize)
    val masterKeyIterationCount = MASTER_KEY_ITERATION_COUNT
    val masterKeyDerivationInformation = KeyDerivationInformation(masterKeySalt, masterKeyIterationCount)

    return masterKeyDerivationInformation
}

@Throws(Exception::class)
private suspend fun generateItemEncryptionKeyPair(masterEncryptionKey: ByteArray): Pair<CryptographicKey, ProtectedValue<CryptographicKey>> {
    val itemEncryptionKeyPair = EncryptionAlgorithm.Asymmetric.RSA2048OAEP.generateKeyPair().resultOrThrowException()

    val serializableItemEncryptionPublicKey = CryptographicKey(itemEncryptionKeyPair.public.encoded)

    val serializableItemEncryptionSecretKey = CryptographicKey(itemEncryptionKeyPair.private.encoded)
    val protectedItemEncryptionSecretKey = ProtectedValue.create(EncryptionAlgorithm.Symmetric.AES256GCM, masterEncryptionKey, serializableItemEncryptionSecretKey).resultOrThrowException()

    return Pair(serializableItemEncryptionPublicKey, protectedItemEncryptionSecretKey)
}

@Throws(Exception::class)
private suspend fun createUserSettings(masterEncryptionKey: ByteArray): ProtectedValue<UserSettings> {
    val userSettings = UserSettings()
    val protectedUserSettings = ProtectedValue.create(EncryptionAlgorithm.Symmetric.AES256GCM, masterEncryptionKey, userSettings).resultOrThrowException()

    return protectedUserSettings
}

@Throws(Exception::class)
private suspend fun createAuthWebservice(serverUrl: URI, username: String, masterPassword: String, buildInformationProvider: BuildInformationProviding): AuthWebservice {
    val localComputedAuthenticationHash = Derivation.deriveLocalComputedAuthenticationHash(username, masterPassword).resultOrThrowException()
    val authWebservice = AuthWebservice.create(serverUrl, username, localComputedAuthenticationHash, buildInformationProvider)
    return authWebservice
}

private suspend fun createUserWebservice(serverUrl: URI, authWebservice: AuthWebservice, userManager: UserManager): UserWebservice {
    val userWebservice = UserWebservice.create(serverUrl, authWebservice, userManager, userManager.buildInformationProvider)
    return userWebservice
}

data class Webservices(val authWebservice: AuthWebservice, val userWebservice: UserWebservice)

sealed class LoggedInUserResult {
    object LoggedOut : LoggedInUserResult()

    sealed class LoggedIn(val loggedInUser: User) : LoggedInUserResult() {
        class PerformedLogin(loggedInUser: User) : LoggedIn(loggedInUser)
        class RestoredLogin(loggedInUser: User) : LoggedIn(loggedInUser)
    }
}

private class UsersSynchronizationTask(
    private val localRepository: LocalRepository,
    private var userWebservice: UserWebservice,
    private val loggedInUser: User
) : SynchronizationTask {
    override suspend fun synchronize(): Result<Differentiation.Result<User>> {
        return try {
            coroutineScope {
                val localUsersDeferred = async {
                    val localUserList = localRepository.findAllUsers()
                    localUserList.takeIf { it.isNotEmpty() } ?: throw IllegalStateException("The local user list is null - can't process with synchronization!")
                }

                val remoteUsersDeferred = async {
                    userWebservice.requestWithResult { getUsers() }.resultOrThrowException()
                }

                // Only update the other users, not the logged-in user
                val localUsers = localUsersDeferred.await().excludeLoggedInUser()
                val remoteUsers = remoteUsersDeferred.await().excludeLoggedInUser()
                val differentiationResult = Differentiation.collectChanges(localUsers, remoteUsers)

                // Update local database
                localRepository.insertUser(*differentiationResult.newItemsForLocal.toTypedArray())
                localRepository.updateUser(*differentiationResult.modifiedItemsForLocal.toTypedArray())

                Success(differentiationResult)
            }
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    private fun List<User>.excludeLoggedInUser(): List<User> {
        val loggedInUserId = loggedInUser.id
        return filterNot { it.id == loggedInUserId }
    }
}

private class UserDetailsSynchronizationTask(
    private val localRepository: LocalRepository,
    private var userWebservice: UserWebservice,
    private val loggedInUser: User
) : SynchronizationTask {
    override suspend fun synchronize(): Result<Differentiation.Result<User>> {
        return try {
            coroutineScope {
                val localUser = loggedInUser
                val remoteUser = userWebservice.requestWithResult { getUserDetails() }.resultOrThrowException()
                val differentiationResult = Differentiation.collectChanges(listOf(localUser), listOf(remoteUser))

                when {
                    differentiationResult.modifiedItemsForLocal.isNotEmpty() -> {
                        Logger.debug("Update local user because remote user was lastly modified")
                        localRepository.updateUser(remoteUser)
                    }
                    differentiationResult.modifiedItemsForRemote.isNotEmpty() -> {
                        Logger.debug("Update remote user because local user was lastly modified")
                        userWebservice.requestWithoutResult { setUserDetails(localUser) }
                    }
                    else -> {
                        Logger.debug("No update needed because local and remote user are equal")
                    }
                }

                Success(differentiationResult)
            }
        } catch (exception: Exception) {
            Failure(exception)
        }
    }
}

private class ItemsSynchronizationTask(
    private val localRepository: LocalRepository,
    private var userWebservice: UserWebservice,
    private val loggedInUserId: String
) : SynchronizationTask {
    override suspend fun synchronize(): Result<Differentiation.Result<Item>> {
        return try {
            coroutineScope {
                val localItemsDeferred = async { localRepository.findAllItems() }
                val remoteItemsDeferred = async { userWebservice.requestWithResult { getUserItems() }.resultOrThrowException() }

                val localItems = localItemsDeferred.await()
                val remoteItems = remoteItemsDeferred.await()
                val differentiationResult = Differentiation.collectChanges(localItems, remoteItems)

                // Update local database
                localRepository.insertItem(*differentiationResult.newItemsForLocal.toTypedArray())
                localRepository.updateItem(*differentiationResult.modifiedItemsForLocal.toTypedArray())

                val remoteChangedItems = differentiationResult.remoteChangedItems.filter { item ->
                    // Only update items where the user has a non-deleted, non-readonly item authorization
                    localRepository.findItemAuthorizationForItem(item).any { it.userId == loggedInUserId && !it.readOnly && !it.deleted }
                }

                // Update remote webservice if necessary
                if (remoteChangedItems.isNotEmpty()) {
                    userWebservice.requestWithoutResult { setUserItems(remoteChangedItems) }.resultOrThrowException()
                }

                Success(differentiationResult)
            }
        } catch (exception: Exception) {
            Failure(exception)
        }
    }
}

private class ItemAuthorizationsSynchronizationTask(
    private val localRepository: LocalRepository,
    private var userWebservice: UserWebservice,
    private val loggedInUserId: String
) : SynchronizationTask {
    override suspend fun synchronize(): Result<Differentiation.Result<ItemAuthorization>> {
        return try {
            coroutineScope {
                val localItemAuthorizationsDeferred = async { localRepository.findAllItemAuthorizations() }
                val remoteItemAuthorizationsDeferred = async { userWebservice.requestWithResult { getUserItemAuthorizations() }.resultOrThrowException() }

                val localItemAuthorizations = localItemAuthorizationsDeferred.await()
                val remoteItemAuthorizations = remoteItemAuthorizationsDeferred.await()
                val differentiationResult = Differentiation.collectChanges(localItemAuthorizations, remoteItemAuthorizations)

                // Update local database
                localRepository.insertItemAuthorization(*differentiationResult.newItemsForLocal.toTypedArray())
                localRepository.updateItemAuthorization(*differentiationResult.modifiedItemsForLocal.toTypedArray())

                val remoteChangedItemAuthorizations = differentiationResult.remoteChangedItems.filter { itemAuthorization ->
                    // Only update item authorizations where the user is the owner of the item
                    localRepository.findItem(itemAuthorization.itemId)?.userId == loggedInUserId
                }

                // Update remote webservice if necessary
                if (remoteChangedItemAuthorizations.isNotEmpty()) {
                    userWebservice.requestWithoutResult { setUserItemAuthorizations(remoteChangedItemAuthorizations) }.resultOrThrowException()
                }

                Success(differentiationResult)
            }
        } catch (exception: Exception) {
            Failure(exception)
        }
    }
}

object UserManagerUninitializedException : IllegalStateException("The UserManager is null!")
object LoggedInStateStorageUninitializedException : IllegalStateException("Access of uninitialized LoggedInStateStorage!")
object LoggedInUserNotFoundException : IllegalStateException("The logged-in user was not found!")
object UserWebserviceUninitializedException : IllegalStateException("The user webservice is not initialized!")
