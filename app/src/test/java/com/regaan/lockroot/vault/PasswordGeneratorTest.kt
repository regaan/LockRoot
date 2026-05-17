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
