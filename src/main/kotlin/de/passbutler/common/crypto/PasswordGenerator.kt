package de.passbutler.common.crypto

object PasswordGenerator {

    /**
     * Generates a password with given length and desired character types.
     */
    @Throws(IllegalArgumentException::class)
    suspend fun generatePassword(length: Int, characterTypes: Set<CharacterType>): String {
        require(length > 0) { "The length of the password must be greater than 0!" }
        require(characterTypes.isNotEmpty()) { "The given character types set must not be empty!" }

        val allowedCharacters = characterTypes.joinToString(separator = "") { it.characters }
        return RandomGenerator.generateRandomString(length, allowedCharacters)
    }

    sealed class CharacterType(val characters: String) {
        object Lowercase : CharacterType("abcdefghijklmnopqrstuvwxyz")
        object Uppercase : CharacterType("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        object Digits : CharacterType("0123456789")
        object Symbols : CharacterType("!\"#\$%&'()*+,-./:;<=>?@[\\]^_{}~")
    }
}
