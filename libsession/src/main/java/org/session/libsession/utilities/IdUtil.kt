package org.session.libsession.utilities

/**
 * This function takes an educated guess that a name with length over 60 containing no whitespace is
 * probably a session id. If one is received it will be truncated.
 *
 * @return the name of the user or a truncated id if their name has been set to their id.
 */
fun maybeTruncateIdForDisplay(nameOrId: String): String =
    nameOrId.takeIf { it.length < 60 || it.contains(' ') } ?: truncateIdForDisplay(nameOrId)

/**
 * @return a truncated user id containing the first 4 and last 4 chars.
 */
fun truncateIdForDisplay(id: String): String =
    id.takeIf { it.length > 8 }?.run{ "${take(4)}…${takeLast(4)}" } ?: id
