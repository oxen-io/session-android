package org.session.libsession.messaging.file_server

data class VersionData(
    val statusCode: Int, // The value 200. Included for backwards compatibility, and may be removed someday.
    val version: String, // The Session version.
    val updated: Double // The unix timestamp when this version was retrieved from Github; this can be up to 24 hours ago in case of consistent fetch errors, though normally will be within the last 30 minutes.
)