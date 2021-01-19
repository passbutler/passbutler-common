package de.passbutler.common

import de.passbutler.common.base.EditableViewModel
import de.passbutler.common.base.Failure
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import de.passbutler.common.base.clear
import de.passbutler.common.base.resultOrThrowException
import de.passbutler.common.crypto.models.CryptographicKey
import de.passbutler.common.database.LocalRepository
import de.passbutler.common.database.models.Item
import de.passbutler.common.database.models.ItemAuthorization
import de.passbutler.common.database.models.ItemData

class ItemViewModel(
    val item: Item,
    val itemAuthorization: ItemAuthorization,
    private val itemOwnerUsername: String,
    private val loggedInUserViewModel: UserViewModel,
    private val localRepository: LocalRepository
) : EditableViewModel<ItemEditingViewModel> {

    val id
        get() = item.id

    val title
        get() = itemData?.title

    val deleted
        get() = item.deleted

    val created
        get() = item.created

    var itemData: ItemData? = null
        private set

    var itemKey: ByteArray? = null
        private set

    suspend fun decryptSensibleData(userItemEncryptionSecretKey: ByteArray): Result<Unit> {
        return try {
            val decryptedItemKey = itemAuthorization.itemKey.decrypt(userItemEncryptionSecretKey, CryptographicKey.Deserializer).resultOrThrowException().key
            itemKey = decryptedItemKey

            val decryptedItemData = item.data.decrypt(decryptedItemKey, ItemData.Deserializer).resultOrThrowException()
            itemData = decryptedItemData

            Success(Unit)
        } catch (exception: Exception) {
            Failure(exception)
        }
    }

    fun clearSensibleData() {
        itemKey?.clear()
        itemKey = null

        itemData = null
    }

    override fun createEditingViewModel(): ItemEditingViewModel {
        val itemData = unlockedItemData

        // Pass a copy of the item key to `ItemEditingViewModel` to avoid it get cleared via reference
        val itemKeyCopy = itemKey?.copyOf() ?: throw IllegalStateException("The item key is null despite a editing viewmodel is created!")

        val itemModel = ItemEditingViewModel.ItemModel.Existing(item, itemAuthorization, itemOwnerUsername, itemData, itemKeyCopy)
        return ItemEditingViewModel(itemModel, loggedInUserViewModel, localRepository)
    }

    /**
     * The methods `equals()` and `hashCode()` only check the `item` and `itemAuthorization` fields because only these makes an item unique.
     * The `itemData` and `itemKey` is just a state of the item that should not cause the item list to change.
     */

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ItemViewModel

        if (item != other.item) return false
        if (itemAuthorization != other.itemAuthorization) return false

        return true
    }

    override fun hashCode(): Int {
        var result = item.hashCode()
        result = 31 * result + itemAuthorization.hashCode()
        return result
    }
}

val ItemViewModel.unlockedItemData: ItemData
    get() = itemData ?: throw IllegalStateException("The item data is null despite it was guaranteed to be unlocked!")