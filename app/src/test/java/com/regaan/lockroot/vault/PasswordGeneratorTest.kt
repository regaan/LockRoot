package com.regaan.lockroot.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {
    @Test
    fun generatedPasswordHasRequestedLength() {
        val password = PasswordGenerator().generate(length = 32)

        assertEquals(32, password.length)
    }

    @Test
    fun generatedPasswordsAreNotDeterministic() {
        val generator = PasswordGenerator()

        assertNotEquals(generator.generate(), generator.generate())
    }

    @Test
    fun generatedPasswordIncludesEverySelectedGroup() {
        val password = PasswordGenerator().generate(length = 32)

        assertTrue(password.any { it.isLowerCase() })
        assertTrue(password.any { it.isUpperCase() })
        assertTrue(password.any { it.isDigit() })
        assertTrue(password.any { "!@#$%^&*()-_=+[]{};:,.?".contains(it) })
    }

    @Test
    fun generatedPasswordRespectsSelectedGroups() {
        val password = PasswordGenerator().generate(
            length = 20,
            lowercase = false,
            uppercase = false,
            numbers = true,
            symbols = false,
        )

        assertTrue(password.all { it.isDigit() })
    }

    @Test
    fun generatedPasswordExcludesUnselectedGroups() {
        val password = PasswordGenerator().generate(
            length = 20,
            lowercase = true,
            uppercase = false,
            numbers = false,
            symbols = false,
        )

        assertTrue(password.all { it in 'a'..'z' })
        assertTrue(password.none { it.isUpperCase() })
        assertTrue(password.none { it.isDigit() })
        assertTrue(password.none { "!@#$%^&*()-_=+[]{};:,.?".contains(it) })
    }

    @Test
    fun generatedPasswordDoesNotUseAmbiguousCharacters() {
        val password = PasswordGenerator().generate(length = 128)

        assertTrue(password.none { "lIO01".contains(it) })
    }

    @Test
    fun generatorRejectsEmptyCharacterGroupSelection() {
        assertThrows(IllegalArgumentException::class.java) {
            PasswordGenerator().generate(
                lowercase = false,
                uppercase = false,
                numbers = false,
                symbols = false,
            )
        }
    }

    @Test
    fun generatorRejectsInvalidLength() {
        assertThrows(IllegalArgumentException::class.java) {
            PasswordGenerator().generate(length = 8)
        }
    }
}
