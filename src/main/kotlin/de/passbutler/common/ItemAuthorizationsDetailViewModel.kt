package de.passbutler.common

import de.passbutler.common.base.Bindable
import de.passbutler.common.base.BindableObserver
import de.passbutler.common.base.Failure
import de.passbutler.common.base.MutableBindable
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import de.passbutler.common.base.ValueGetterBindable
import de.passbutler.common.base.contains
import de.passbutler.common.database.LocalRepository
import de.passbutler.common.database.models.Item
import org.tinylog.kotlin.Logger
import java.util.*

class ItemAuthorizationsDetailViewModel(
    private val itemId: String,
    private val loggedInUserViewModel: UserViewModel,
    private val localRepository: LocalRepository
) {
    val itemAuthorizationEditingViewModels: Bindable<List<ItemAuthorizationEditingViewModel>>
        get() = _itemAuthorizationEditingViewModels

    val itemAuthorizationEditingViewModelsModified = ValueGetterBindable {
        _itemAuthorizationEditingViewModels.value.any { it.isReadAllowed.isModified || it.isWriteAllowed.isModified }
    }

    private val _itemAuthorizationEditingViewModels = MutableBindable<List<ItemAuthorizationEditingViewModel>>(emptyList())

    private val itemAuthorizationEditingViewModelsModifiedObserver: BindableObserver<Boolean> = {
        itemAuthorizationEditingViewModelsModified.notifyChange()
    }

    suspend fun initializeItemAuthorizationEditingViewModels() {
        val itemViewModel = loggedInUserViewModel.itemViewModels.value.find { it.id == itemId }

        if (itemViewModel != null) {
            val item = itemViewModel.item

            // Make a copy of the item key to avoid it can be cleared via reference
            val itemKeyCopy = itemViewModel.itemKey?.copyOf() ?: throw IllegalStateException("The item key is null despite the item authorizations should be edited!")

            val existingItemAuthorizationEditingViewModels = createExistingItemAuthorizationEditingViewModels(item)
            val provisionalItemAuthorizationEditingViewModels = createProvisionalItemAuthorizationEditingViewModels(existingItemAuthorizationEditingViewModels, item, itemKeyCopy)
            val newItemAuthorizationEditingViewModels = existingItemAuthorizationEditingViewModels + provisionalItemAuthorizationEditingViewModels

            Logger.debug(
                "existingItemAuthorizationEditingViewModels = $existingItemAuthorizationEditingViewModels, " +
                "provisionalItemAuthorizationEditingViewModels = $provisionalItemAuthorizationEditingViewModels"
            )

            _itemAuthorizationEditingViewModels.value.forEach {
                it.isReadAllowed.removeObserver(itemAuthorizationEditingViewModelsModifiedObserver)
                it.isWriteAllowed.removeObserver(itemAuthorizationEditingViewModelsModifiedObserver)
            }

            _itemAuthorizationEditingViewModels.value = newItemAuthorizationEditingViewModels

            _itemAuthorizationEditingViewModels.value.forEach {
                it.isReadAllowed.addObserver(null, false, itemAuthorizationEditingViewModelsModifiedObserver)
                it.isWriteAllowed.addObserver(null, false, itemAuthorizationEditingViewModelsModifiedObserver)
            }

            // Notify bindable to be sure view is triggered after re-initialization
            itemAuthorizationEditingViewModelsModified.notifyChange()
        } else {
            // This edge-case should only happens on Android app if the `Activity` was restored by Android, so the items in `UserViewModel` are still not created
            Logger.warn("The ItemViewModel for id = $itemId was not found!")
        }
    }

    private suspend fun createExistingItemAuthorizationEditingViewModels(item: Item): List<ItemAuthorizationEditingViewModel> {
        val users = localRepository.findAllUsers()
        return localRepository.findItemAuthorizationForItem(item)
            .filter { itemAuthorization ->
                // Do not show item authorization of logged-in user
                itemAuthorization.userId != loggedInUserViewModel.id
            }
            .map { itemAuthorization ->
                val user = users.find { it.id == itemAuthorization.userId } ?: throw IllegalStateException("The user of the item authorization was not found!")
                ItemAuthorizationEditingViewModel(ItemAuthorizationEditingViewModel.ItemAuthorizationModel.Existing(user.username, itemAuthorization))
            }
    }

    private suspend fun createProvisionalItemAuthorizationEditingViewModels(
        existingItemAuthorizationEditingViewModels: List<ItemAuthorizationEditingViewModel>,
        item: Item,
        itemKey: ByteArray
    ): List<ItemAuthorizationEditingViewModel> {
        return localRepository.findAllUsers()
            .filter { user ->
                val userId = user.id

                // Do not show item authorization of logged-in user
                val itemAuthorizationOfLoggedInUser = userId == loggedInUserViewModel.id

                // Do not create provisional item authorization for existing item authorizations
                val itemAuthorizationAlreadyExists = existingItemAuthorizationEditingViewModels.contains {
                    val itemAuthorizationUserId = (it.itemAuthorizationModel as ItemAuthorizationEditingViewModel.ItemAuthorizationModel.Existing).itemAuthorization.userId
                    userId == itemAuthorizationUserId
                }

                !itemAuthorizationOfLoggedInUser && !itemAuthorizationAlreadyExists
            }
            .map { user ->
                val itemAuthorizationId = UUID.randomUUID().toString()
                ItemAuthorizationEditingViewModel(
                    ItemAuthorizationEditingViewModel.ItemAuthorizationModel.Provisional(
                        user.id,
                        user.username,
                        user.itemEncryptionPublicKey.key,
                        item,
                        itemKey,
                        itemAuthorizationId
                    )
                )
            }
    }

    suspend fun save(): Result<Unit> {
        val changedItemAuthorizationEditingViewModels = _itemAuthorizationEditingViewModels.value
            .filter { it.isReadAllowed.isModified || it.isWriteAllowed.isModified }

        val saveResults = listOf(
            saveExistingItemAuthorizations(changedItemAuthorizationEditingViewModels),
            saveProvisionalItemAuthorizations(changedItemAuthorizationEditingViewModels)
        )

        val firstFailure = saveResults.filterIsInstance(Failure::class.java).firstOrNull()

        return if (firstFailure != null) {
            Failure(firstFailure.throwable)
        } else {
            // Reinitialize list to be sure the new states are applied
            initializeItemAuthorizationEditingViewModels()

            Success(Unit)
        }
    }

    private suspend fun saveExistingItemAuthorizations(changedItemAuthorizationEditingViewModels: List<ItemAuthorizationEditingViewModel>): Result<Unit> {
        var failedResultException: Throwable? = null
        val changedExistingItemAuthorizations = changedItemAuthorizationEditingViewModels.mapNotNull { itemAuthorizationViewModel ->
            (itemAuthorizationViewModel.itemAuthorizationModel as? ItemAuthorizationEditingViewModel.ItemAuthorizationModel.Existing)?.let { itemAuthorizationModel ->
                val isReadAllowed = itemAuthorizationViewModel.isReadAllowed.value
                val isWriteAllowed = itemAuthorizationViewModel.isWriteAllowed.value
                val updateItemAuthorizationResult = itemAuthorizationModel.createItemAuthorization(isReadAllowed, isWriteAllowed)

                when (updateItemAuthorizationResult) {
                    is Success -> updateItemAuthorizationResult.result
                    is Failure -> {
                        failedResultException = updateItemAuthorizationResult.throwable
                        null
                    }
                }
            }
        }

        return failedResultException?.let {
            Failure(it)
        } ?: run {
            Logger.debug("changedExistingItemAuthorizations = $changedExistingItemAuthorizations")
            localRepository.updateItemAuthorization(*changedExistingItemAuthorizations.toTypedArray())

            Success(Unit)
        }
    }

    private suspend fun saveProvisionalItemAuthorizations(changedItemAuthorizationEditingViewModels: List<ItemAuthorizationEditingViewModel>): Result<Unit> {
        var failedResultException: Throwable? = null
        val changedProvisionalItemAuthorizations = changedItemAuthorizationEditingViewModels.mapNotNull { itemAuthorizationViewModel ->
            (itemAuthorizationViewModel.itemAuthorizationModel as? ItemAuthorizationEditingViewModel.ItemAuthorizationModel.Provisional)?.let { itemAuthorizationModel ->
                val isReadAllowed = itemAuthorizationViewModel.isReadAllowed.value
                val isWriteAllowed = itemAuthorizationViewModel.isWriteAllowed.value
                val createItemAuthorizationResult = itemAuthorizationModel.createItemAuthorization(isReadAllowed, isWriteAllowed)

                when (createItemAuthorizationResult) {
                    is Success -> createItemAuthorizationResult.result
                    is Failure -> {
                        failedResultException = createItemAuthorizationResult.throwable
                        null
                    }
                }
            }
        }

        return failedResultException?.let {
            Failure(it)
        } ?: run {
            Logger.debug("changedProvisionalItemAuthorizations = $changedProvisionalItemAuthorizations")
            localRepository.insertItemAuthorization(*changedProvisionalItemAuthorizations.toTypedArray())

            Success(Unit)
        }
    }
}
