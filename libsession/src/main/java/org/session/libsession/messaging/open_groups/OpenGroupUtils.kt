package org.session.libsession.messaging.open_groups

fun String.migrateLegacyServerUrl() = if (contains(OpenGroupAPIV2.legacyServerIP)) {
    OpenGroupAPIV2.defaultServer
} else {
    this
}