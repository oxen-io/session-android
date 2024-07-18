package org.session.libsignal.utilities

object PublicKeyValidation {
    private val HEX_CHARACTERS = "0123456789ABCDEFabcdef".toSet()
    private val INVALID_PREFIXES = setOf(IdPrefix.GROUP, IdPrefix.BLINDED, IdPrefix.BLINDEDV2)

    fun isValid(candidate: String, isPrefixRequired: Boolean = true): Boolean = hasValidLength(candidate) && isValidHexEncoding(candidate) && (!isPrefixRequired || IdPrefix.fromValue(candidate) != null)
    fun hasValidPrefix(candidate: String) = IdPrefix.fromValue(candidate) !in INVALID_PREFIXES
    private fun hasValidLength(candidate: String) = candidate.length == 66
    private fun isValidHexEncoding(candidate: String) = HEX_CHARACTERS.containsAll(candidate.toSet())
}
