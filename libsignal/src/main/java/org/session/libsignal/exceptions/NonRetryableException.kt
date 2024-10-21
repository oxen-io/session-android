package org.session.libsignal.exceptions

class NonRetryableException(message: String? = null, cause: Throwable? = null): RuntimeException(message, cause)