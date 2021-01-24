package de.passbutler.common

import de.passbutler.common.base.DiscardableMutableBindable
import de.passbutler.common.base.Failure
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import de.passbutler.common.base.resultOrThrowException
import de.passbutler.common.crypto.EncryptionAlgorithm
import de.passbutler.common.crypto.models.CryptographicKey
import de.passbutler.common.crypto.models.ProtectedValue
import de.passbutler.common.database.models.Item
import de.passbutler.common.database.models.ItemAuthorization
import java.time.Instant

class ItemAuthorizationEditingViewModel(val itemAuthorizationModel: ItemAuthorizationModel) {

    val username: String
        get() = when (itemAuthorizationModel) {
            is ItemAuthorizationModel.Provisional -> itemAuthorizationModel.username
            is ItemAuthorizationModel.Existing -> itemAuthorizationModel.username
        }

    val isReadAllowed = DiscardableMutableBindable(determineInitialIsReadAllowed())
    val isWriteAllowed = DiscardableMutableBindable(determineInitialIsWriteAllowed())

    private fun determineInitialIsReadAllowed(): Boolean {
        return when (itemAuthorizationModel) {
            is ItemAuthorizationModel.Provisional -> false
            is ItemAuthorizationModel.Existing -> {
                !itemAuthorizationModel.itemAuthorization.deleted
            }
        }
    }

    private fun determineInitialIsWriteAllowed(): Boolean {
        return when (itemAuthorizationModel) {
            is ItemAuthorizationModel.Provisional -> false
            is ItemAuthorizationModel.Existing -> {
                !itemAuthorizationModel.itemAuthorization.deleted && !itemAuthorizationModel.itemAuthorization.readOnly
            }
        }
    }

    sealed class ItemAuthorizationModel {
        abstract suspend fun createItemAuthorization(isReadAllowed: Boolean, isWriteAllowed: Boolean): Result<ItemAuthorization>

        class Provisional(
            val userId: String,
            val username: String,
            val userItemEncryptionPublicKey: ByteArray,
            val item: Item,
            val itemKey: ByteArray,
            val itemAuthorizationId: String
        ) : ItemAuthorizationModel() {
            override suspend fun createItemAuthorization(isReadAllowed: Boolean, isWriteAllowed: Boolean): Result<ItemAuthorization> {
                val asymmetricEncryptionAlgorithm = EncryptionAlgorithm.Asymmetric.RSA2048OAEP

                return try {
                    val protectedItemKey = ProtectedValue.create(asymmetricEncryptionAlgorithm, userItemEncryptionPublicKey, CryptographicKey(itemKey)).resultOrThrowException()
                    val currentDate = Instant.now()
                    val createdItemAuthorization = ItemAuthorization(
                        id = itemAuthorizationId,
                        userId = userId,
                        itemId = item.id,
                        itemKey = protectedItemKey,
                        readOnly = !isWriteAllowed,
                        deleted = !isReadAllowed,
                        modified = currentDate,
                        created = currentDate
                    )

                    Success(createdItemAuthorization)
                } catch (exception: Exception) {
                    Failure(exception)
                }
            }
        }

        class Existing(
            val username: String,
            val itemAuthorization: ItemAuthorization
        ) : ItemAuthorizationModel() {
            override suspend fun createItemAuthorization(isReadAllowed: Boolean, isWriteAllowed: Boolean): Result<ItemAuthorization> {
                val currentDate = Instant.now()
                val updatedItemAuthorization = itemAuthorization.copy(
                    readOnly = !isWriteAllowed,
                    deleted = !isReadAllowed,
                    modified = currentDate
                )

                return Success(updatedItemAuthorization)
            }
        }
    }
}
