package com.regaan.lockroot.vault

import java.security.SecureRandom

class PasswordGenerator(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun generate(
        length: Int = 24,
        lowercase: Boolean = true,
        uppercase: Boolean = true,
        numbers: Boolean = true,
        symbols: Boolean = true,
    ): String {
        require(length in 12..128) { "Password length must be between 12 and 128." }

        val groups = buildList {
            if (lowercase) add(LOWERCASE)
            if (uppercase) add(UPPERCASE)
            if (numbers) add(NUMBERS)
            if (symbols) add(SYMBOLS)
        }
        require(groups.isNotEmpty()) { "At least one character group is required." }
        require(length >= groups.size) { "Length must fit all selected groups." }

        val result = mutableListOf<Char>()
        groups.forEach { result.add(randomChar(it)) }

        val allChars = groups.joinToString("")
        while (result.size < length) {
            result.add(randomChar(allChars))
        }

        for (index in result.indices.reversed()) {
            val swapIndex = secureRandom.nextInt(index + 1)
            val tmp = result[index]
            result[index] = result[swapIndex]
            result[swapIndex] = tmp
        }

        return result.joinToString("")
    }

    private fun randomChar(chars: String): Char {
        val bound = chars.length
        val max = Int.MAX_VALUE - (Int.MAX_VALUE % bound)
        var value: Int
        do {
            value = secureRandom.nextInt() and Int.MAX_VALUE
        } while (value >= max)
        return chars[value % bound]
    }

    companion object {
        private const val LOWERCASE = "abcdefghijkmnopqrstuvwxyz"
        private const val UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        private const val NUMBERS = "23456789"
        private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?"
    }
}
