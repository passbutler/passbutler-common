package de.passbutler.common.crypto

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PasswordGeneratorTest {
    @Test
    fun `Pass a length of zero throws an exception`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { PasswordGenerator.generatePassword(0, setOf(PasswordGenerator.CharacterType.Digits)) }
        }
        assertEquals("The length of the password must be greater than 0!", exception.message)
    }

    @Test
    fun `Pass an empty string as allowed characters throws an exception`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { PasswordGenerator.generatePassword(5, emptySet()) }
        }
        assertEquals("The given character types set must not be empty!", exception.message)
    }

    @Test
    fun `Generate random password`() {
        val randomString = runBlocking { PasswordGenerator.generatePassword(8, setOf(PasswordGenerator.CharacterType.Digits)) }
        assertEquals(8, randomString.length)
    }
}
