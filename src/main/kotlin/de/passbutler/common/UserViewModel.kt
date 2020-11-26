package de.passbutler.common

import de.passbutler.common.base.BindableObserver
import de.passbutler.common.base.Failure
import de.passbutler.common.base.MutableBindable
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import de.passbutler.common.base.ValueGetterBindable
import de.passbutler.common.base.addSignal
import de.passbutler.common.base.clear
import de.passbutler.common.base.resultOrThrowException
import de.passbutler.common.base.signal
import de.passbutler.common.crypto.BiometricsProviding
import de.passbutler.common.crypto.Derivation
import de.passbutler.common.crypto.models.CryptographicKey
import de.passbutler.common.crypto.models.EncryptedValue
import de.passbutler.common.crypto.models.KeyDerivationInformation
import de.passbutler.common.crypto.models.ProtectedValue
import de.passbutler.common.database.models.Item
import de.passbutler.common.database.models.User
import de.passbutler.common.database.models.UserSettings
import de.passbutler.common.database.models.UserType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger
import java.time.Instant
import javax.crypto.Cipher
import kotlin.coroutines.CoroutineContext

class UserViewModel private constructor(
    private val userManager: UserManager,
    val biometricsProvider: BiometricsProviding,
    private val initialUser: User,
    private var masterPasswordAuthenticationHash: String,
    private val masterKeyDerivationInformation: KeyDerivationInformation,
    private var protectedMasterEncryptionKey: ProtectedValue<CryptographicKey>,
    val itemEncryptionPublicKey: CryptographicKey,
    private val protectedItemEncryptionSecretKey: ProtectedValue<CryptographicKey>,
    private var protectedSettings: ProtectedValue<UserSettings>
) : CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + coroutineJob

    private val coroutineJob = SupervisorJob()

    val localRepository
        get() = userManager.localRepository

    val loggedInStateStorage
        get() = userManager.loggedInStateStorage

    val userType
        get() = loggedInStateStorage.value?.userType

    val encryptedMasterPassword
        get() = loggedInStateStorage.value?.encryptedMasterPassword

    val lastSuccessfulSyncDate
        get() = loggedInStateStorage.value?.lastSuccessfulSyncDate

    val webservices
        get() = userManager.webservices

    val id = initialUser.id
    val username = MutableBindable(initialUser.username)

    val itemViewModels = MutableBindable<List<ItemViewModel>>(emptyList())

    val automaticLockTimeout = MutableBindable<Int?>(null)
    val hidePasswordsEnabled = MutableBindable<Boolean?>(null)

    val biometricUnlockAvailable = ValueGetterBindable {
        biometricsProvider.isBiometricAvailable
    }

    val biometricUnlockEnabled = ValueGetterBindable {
        biometricUnlockAvailable.value && encryptedMasterPassword != null
    }

    private val updateItemViewModelsSignal = signal {
        updateItemViewModels()
    }

    private var itemViewModelsUpdateJob: Job? = null

    private val automaticLockTimeoutChangedObserver: BindableObserver<Int?> = { applyUserSettings() }
    private val hidePasswordsEnabledChangedObserver: BindableObserver<Boolean?> = { applyUserSettings() }

    private var masterEncryptionKey: ByteArray? = null
    private var itemEncryptionSecretKey: ByteArray? = null
    private var settings: UserSettings? = null

    private var persistUserSettingsJob: Job? = null

    @Throws(IllegalArgumentException::class)
    constructor(userManager: UserManager, biometricsProvider: BiometricsProviding, user: User) : this(
        userManager,
        biometricsProvider,
        user,
        user.masterPasswordAuthenticationHash ?: throw IllegalArgumentException("The given user has no master password authentication hash!"),
        user.masterKeyDerivationInformation ?: throw IllegalArgumentException("The given user has no master key derivation information!"),
        user.masterEncryptionKey ?: throw IllegalArgumentException("The given user has no master encryption key!"),
        user.itemEncryptionPublicKey,
        user.itemEncryptionSecretKey ?: throw IllegalArgumentException("The given user has no item encryption secret key!"),
        user.settings ?: throw IllegalArgumentException("The given user has no user settings!")
    )

    init {
        Logger.debug("Create new UserViewModel ($this)")
    }

    fun cancelJobs() {
        Logger.debug("Cancel the coroutine job...")
        coroutineJob.cancel()
    }

    fun createNewItemEditingViewModel(): ItemEditingViewModel {
        val itemModel = ItemEditingViewModel.ItemModel.New
        return ItemEditingViewModel(itemModel, this, localRepository)
    }

    suspend fun decryptSensibleData(masterPassword: String): Result<Unit> {
        Logger.debug("Decrypt sensible data")

        return try {
            masterEncryptionKey = decryptMasterEncryptionKey(masterPassword).resultOrThrowException().also { masterEncryptionKey ->
                itemEncryptionSecretKey = protectedItemEncryptionSecretKey.decrypt(masterEncryptionKey, CryptographicKey.Deserializer).resultOrThrowException().key

                registerUpdateItemViewModelsSignal()

                val decryptedSettings = protectedSettings.decrypt(masterEncryptionKey, UserSettings.Deserializer).resultOrThrowException()
                registerUserSettingObservers(decryptedSettings)
            }

            Success(Unit)
        } catch (exception: Exception) {
            Logger.warn("The sensible data could not be decrypted - clear sensible data to avoid corrupt state")
            clearSensibleData()

            Failure(exception)
        }
    }

    suspend fun decryptMasterEncryptionKey(masterPassword: String): Result<ByteArray> {
        var masterKey: ByteArray? = null

        return try {
            masterKey = Derivation.deriveMasterKey(masterPassword, masterKeyDerivationInformation).resultOrThrowException()

            val decryptedMasterEncryptionKey = protectedMasterEncryptionKey.decrypt(masterKey, CryptographicKey.Deserializer).resultOrThrowException()
            Success(decryptedMasterEncryptionKey.key)
        } catch (exception: Exception) {
            // Wrap the thrown exception to be able to determine that this call failed (used to show concrete error string in UI)
            val wrappedException = DecryptMasterEncryptionKeyFailedException(exception)
            Failure(wrappedException)
        } finally {
            masterKey?.clear()
        }
    }

    private fun registerUpdateItemViewModelsSignal() {
        userManager.itemsOrItemAuthorizationsChanged.addSignal(updateItemViewModelsSignal, true)
    }

    private fun registerUserSettingObservers(decryptedSettings: UserSettings) {
        automaticLockTimeout.value = decryptedSettings.automaticLockTimeout
        hidePasswordsEnabled.value = decryptedSettings.hidePasswords

        // Register observers after field initialisations to avoid initial observer calls
        automaticLockTimeout.addObserver(null, false, automaticLockTimeoutChangedObserver)
        hidePasswordsEnabled.addObserver(null, false, hidePasswordsEnabledChangedObserver)
    }

    fun restoreWebservices(masterPassword: String) {
        Logger.debug("Restore webservices")

        // Restore webservices asynchronously to avoid slow network is blocking the caller
        launch {
            userManager.restoreWebservices(masterPassword)
        }
    }

    fun clearSensibleData() {
        Logger.debug("Clear sensible data")

        // Be sure all observers that uses crypto resources cleared afterwards are unregistered first
        unregisterUpdateItemViewModelsSignal()
        unregisterUserSettingObservers()

        masterEncryptionKey?.clear()
        masterEncryptionKey = null

        itemEncryptionSecretKey?.clear()
        itemEncryptionSecretKey = null

        itemViewModels.value.forEach {
            it.clearSensibleData()
        }
    }

    private fun unregisterUpdateItemViewModelsSignal() {
        userManager.itemsOrItemAuthorizationsChanged.removeSignal(updateItemViewModelsSignal)

        // Finally cancel the (possible) running "update item viewmodels" job
        itemViewModelsUpdateJob?.cancel()
    }

    private fun unregisterUserSettingObservers() {
        // Unregister observers before setting field reset to avoid unnecessary observer calls
        automaticLockTimeout.removeObserver(automaticLockTimeoutChangedObserver)
        hidePasswordsEnabled.removeObserver(hidePasswordsEnabledChangedObserver)

        automaticLockTimeout.value = null
        hidePasswordsEnabled.value = null
    }

    suspend fun registerLocalUser(serverUrlString: String, invitationCode: String, masterPassword: String): Result<Unit> {
        Logger.debug("Register local user")

        val loggedInUser = createModel()
        return userManager.registerLocalUser(loggedInUser, serverUrlString, invitationCode, masterPassword)
    }

    suspend fun synchronizeData(): Result<Unit> {
        Logger.debug("Synchronize data")

        val loggedInUser = createModel()
        return userManager.synchronize(loggedInUser)
    }

    suspend fun updateMasterPassword(oldMasterPassword: String, newMasterPassword: String): Result<Unit> {
        Logger.debug("Update master password")

        val masterEncryptionKey = masterEncryptionKey ?: throw IllegalStateException("The master encryption key is null despite it was tried to update the master password!")
        var newMasterKey: ByteArray? = null

        return try {
            // Test if master password is correct via thrown exception
            decryptMasterEncryptionKey(oldMasterPassword).resultOrThrowException()

            val newLocalMasterPasswordAuthenticationHash = Derivation.deriveLocalAuthenticationHash(username.value, newMasterPassword).resultOrThrowException()
            val newServerMasterPasswordAuthenticationHash = Derivation.deriveServerAuthenticationHash(newLocalMasterPasswordAuthenticationHash).resultOrThrowException()

            newMasterKey = Derivation.deriveMasterKey(newMasterPassword, masterKeyDerivationInformation).resultOrThrowException()
            val newProtectedMasterEncryptionKey = protectedMasterEncryptionKey.update(newMasterKey, CryptographicKey(masterEncryptionKey)).resultOrThrowException()

            val updatedUser = createModel().copy(
                masterPasswordAuthenticationHash = newServerMasterPasswordAuthenticationHash,
                masterEncryptionKey = newProtectedMasterEncryptionKey
            )

            val updateUserResult = userManager.updateUser(updatedUser)

            // Throw wrapped exception to be able to determine that this call failed (used to show concrete error string in UI)
            if (updateUserResult is Failure) {
                throw UpdateUserFailedException(updateUserResult.throwable)
            }

            // Reinitialize webservices to apply the new master password
            if (userType == UserType.REMOTE) {
                userManager.reinitializeWebservices(newMasterPassword)
            }

            // Finally update the locale fields
            masterPasswordAuthenticationHash = newServerMasterPasswordAuthenticationHash
            protectedMasterEncryptionKey = newProtectedMasterEncryptionKey

            // After all mandatory changes, try to disable biometric unlock because master password re-encryption would require complex flow with biometric authentication UI
            val disableBiometricUnlockResult = disableBiometricUnlock()

            if (disableBiometricUnlockResult is Failure) {
                Logger.warn(disableBiometricUnlockResult.throwable, "The biometric unlock could not be disabled")
            }

            Success(Unit)
        } catch (exception: Exception) {
            Failure(exception)
        } finally {
            newMasterKey?.clear()
        }
    }

    suspend fun enableBiometricUnlock(initializedSetupBiometricUnlockCipher: Cipher, masterPassword: String): Result<Unit> {
        Logger.debug("Enable biometric unlock")

        return try {
            // Test if master password is correct via thrown exception
            decryptMasterEncryptionKey(masterPassword).resultOrThrowException()

            val encryptedMasterPasswordInitializationVector = initializedSetupBiometricUnlockCipher.iv
            val encryptedMasterPasswordValue = biometricsProvider.encryptData(initializedSetupBiometricUnlockCipher, masterPassword.toByteArray()).resultOrThrowException()
            val encryptedMasterPassword = EncryptedValue(encryptedMasterPasswordInitializationVector, encryptedMasterPasswordValue)

            userManager.updateLoggedInStateStorage {
                it.encryptedMasterPassword = encryptedMasterPassword
            }

            biometricUnlockEnabled.notifyChange()

            Success(Unit)
        } catch (exception: Exception) {
            Logger.warn("The biometric unlock could not be enabled - disable biometric unlock to avoid corrupt state")
            disableBiometricUnlock()

            Failure(exception)
        }
    }

    suspend fun disableBiometricUnlock(): Result<Unit> {
        Logger.debug("Disable biometric unlock")

        return try {
            biometricsProvider.removeKey(BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME).resultOrThrowException()

            userManager.updateLoggedInStateStorage {
                it.encryptedMasterPassword = null
            }

            biometricUnlockEnabled.notifyChange()

            Success(Unit)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    private fun applyUserSettings() {
        Logger.debug("applyUserSettings()")

        val automaticLockTimeoutValue = automaticLockTimeout.value
        val hidePasswordsEnabledValue = hidePasswordsEnabled.value

        if (automaticLockTimeoutValue != null && hidePasswordsEnabledValue != null) {
            val updatedSettings = UserSettings(automaticLockTimeoutValue, hidePasswordsEnabledValue)

            // Only persist settings if changed
            if (updatedSettings != settings) {
                persistUserSettings(updatedSettings)
                settings = updatedSettings
            }
        }
    }

    private fun persistUserSettings(settings: UserSettings) {
        Logger.debug("persistUserSettings()")

        persistUserSettingsJob?.cancel()
        persistUserSettingsJob = launch {
            val masterEncryptionKey = masterEncryptionKey

            // Only persist if master encryption key is set (user logged-in and state unlocked)
            if (masterEncryptionKey != null) {
                try {
                    protectedSettings = protectedSettings.update(masterEncryptionKey, settings).resultOrThrowException()

                    val user = createModel()
                    localRepository.updateUser(user)
                } catch (exception: Exception) {
                    Logger.warn(exception, "The user settings could not be updated")
                }
            }
        }
    }

    private fun createModel(): User {
        // Only update fields that are allowed to modify (server reject changes on non-allowed field anyway)
        return initialUser.copy(
            username = username.value,
            masterPasswordAuthenticationHash = masterPasswordAuthenticationHash,
            masterKeyDerivationInformation = masterKeyDerivationInformation,
            masterEncryptionKey = protectedMasterEncryptionKey,
            settings = protectedSettings,
            modified = Instant.now()
        )
    }

    private fun updateItemViewModels() {
        itemViewModelsUpdateJob?.cancel()
        itemViewModelsUpdateJob = launch {
            val newItems = localRepository.findAllItems()
            val updatedItemViewModels = createItemViewModels(newItems)
            val decryptedItemViewModels = decryptItemViewModels(updatedItemViewModels)

            Logger.debug("Update item viewmodels: itemViewModels.size = ${decryptedItemViewModels.size}")
            itemViewModels.value = decryptedItemViewModels

            // Always notify observers because if the sensible data of an `ItemViewModel` was cleared and decrypted again, the internal change is not noticeable for observers
            itemViewModels.notifyChange()
        }
    }

    private suspend fun createItemViewModels(newItems: List<Item>): List<ItemViewModel> {
        val existingItemViewModels = itemViewModels.value
        Logger.debug("Create item viewmodels: newItems.size = ${newItems.size}, existingItemViewModels.size = ${existingItemViewModels.size}")

        // Load list once instead query user for every item later
        val usersIdUsernameMapping = localRepository.findAllUsers().associate {
            it.id to it.username
        }

        val newItemViewModels = newItems
            .mapNotNull { item ->
                // Check if the user has a non-deleted item authorization to access the item
                val itemAuthorization = localRepository.findItemAuthorizationForItem(item).firstOrNull {
                    it.userId == id && !it.deleted
                }

                if (itemAuthorization != null) {
                    existingItemViewModels
                        .find {
                            // Try to find an existing (already decrypted) item viewmodel to avoid decrypting again
                            it.id == item.id
                        }
                        ?.takeIf {
                            // Only take existing item viewmodel if model of item and item authorization is the same
                            it.item == item && it.itemAuthorization == itemAuthorization
                        }
                        ?: run {
                            val itemOwnerUserId = item.userId
                            val itemOwnerUsername = usersIdUsernameMapping[itemOwnerUserId]

                            if (itemOwnerUsername != null) {
                                Logger.debug("Create new viewmodel for item '${item.id}' because recycling was not possible")

                                // No existing item viewmodel was found, thus a new must be created for item
                                ItemViewModel(item, itemAuthorization, itemOwnerUsername, this, localRepository)
                            } else {
                                Logger.warn("The owner username could not be mapped for user id = $itemOwnerUserId!")
                                null
                            }
                        }
                } else {
                    Logger.debug("A non-deleted item authorization of user for item '${item.id}' was not found - skip item")
                    null
                }
            }
            .sortedBy { it.created }

        return newItemViewModels
    }

    private suspend fun decryptItemViewModels(itemViewModels: List<ItemViewModel>): List<ItemViewModel> {
        val itemEncryptionSecretKey = itemEncryptionSecretKey ?: throw IllegalStateException("The item encryption key is null despite item decryption was started!")

        return coroutineScope {
            itemViewModels
                .map { itemViewModel ->
                    // Start parallel decryption
                    itemViewModel to async {
                        // Only decrypt if not already decrypted
                        if (itemViewModel.itemData == null) {
                            val itemDecryptionResult = itemViewModel.decryptSensibleData(itemEncryptionSecretKey)

                            when (itemDecryptionResult) {
                                is Success -> Logger.debug("The item viewmodel '${itemViewModel.id}' was decrypted successfully")
                                is Failure -> Logger.warn(itemDecryptionResult.throwable, "The item viewmodel '${itemViewModel.id}' could not be decrypted")
                            }

                            itemDecryptionResult
                        } else {
                            Logger.debug("The item viewmodel '${itemViewModel.id}' is already decrypted")
                            Success(Unit)
                        }
                    }
                }
                .mapNotNull {
                    // Await results afterwards
                    val itemViewModel = it.first
                    val itemDecryptionResult = it.second.await()

                    when (itemDecryptionResult) {
                        is Success -> itemViewModel
                        is Failure -> null
                    }
                }
        }
    }

    companion object {
        const val BIOMETRIC_MASTER_PASSWORD_ENCRYPTION_KEY_NAME = "MasterPasswordEncryptionKey"
    }
}

object LoggedInUserViewModelUninitializedException : IllegalStateException("The logged-in UserViewModel is null!")
class DecryptMasterEncryptionKeyFailedException(cause: Throwable? = null) : Exception(cause)
class UpdateUserFailedException(cause: Throwable? = null) : Exception(cause)
