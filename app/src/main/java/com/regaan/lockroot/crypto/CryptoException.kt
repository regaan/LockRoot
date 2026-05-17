package com.regaan.lockroot.crypto

sealed class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

class AuthenticationFailedException(cause: Throwable? = null) :
    CryptoException("Wrong password or data authentication failed.", cause)

class UnsupportedVaultFormatException(message: String) : CryptoException(message)
