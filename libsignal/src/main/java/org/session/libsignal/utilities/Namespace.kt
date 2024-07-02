package org.session.libsignal.utilities

object Namespace {
    fun ALL() = "all"
    fun UNAUTHENTICATED_CLOSED_GROUP() = -10
    external fun DEFAULT(): Int
    external fun USER_PROFILE(): Int
    external fun CONTACTS(): Int
    external fun CONVO_INFO_VOLATILE(): Int
    external fun GROUPS(): Int
    external fun CLOSED_GROUP_INFO(): Int
    external fun CLOSED_GROUP_MEMBERS(): Int
    external fun ENCRYPTION_KEYS(): Int
    external fun CLOSED_GROUP_MESSAGES(): Int
    external fun REVOKED_GROUP_MESSAGES(): Int
}