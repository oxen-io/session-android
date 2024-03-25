package org.session.libsignal.utilities

enum class IdPrefix(val value: String) {
    STANDARD("05"), BLINDED("15"), UN_BLINDED("00"), BLINDEDV2("25");

    fun isBlinded() = value == BLINDED.value || value == BLINDEDV2.value

    companion object {
        fun fromValue(rawValue: String): IdPrefix? = when(rawValue.take(2)) {
            STANDARD.value -> STANDARD
            BLINDED.value -> BLINDED
            BLINDEDV2.value -> BLINDEDV2
            UN_BLINDED.value -> UN_BLINDED
            else -> null
        }
    }

}