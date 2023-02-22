#include "user_groups.h"


#pragma clang diagnostic push
#pragma ide diagnostic ignored "bugprone-reserved-identifier"
extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_UserGroupsConfig_00024Companion_newInstance___3B(
        JNIEnv *env, jobject thiz, jbyteArray ed25519_secret_key) {
    auto secret_key = util::ustring_from_bytes(env, ed25519_secret_key);

    auto* user_groups = new session::config::UserGroups(secret_key, std::nullopt);

    jclass contactsClass = env->FindClass("network/loki/messenger/libsession_util/UserGroupsConfig");
    jmethodID constructor = env->GetMethodID(contactsClass, "<init>", "(J)V");
    jobject newConfig = env->NewObject(contactsClass, constructor, reinterpret_cast<jlong>(user_groups));

    return newConfig;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_UserGroupsConfig_00024Companion_newInstance___3B_3B(
        JNIEnv *env, jobject thiz, jbyteArray ed25519_secret_key, jbyteArray initial_dump) {
    auto secret_key = util::ustring_from_bytes(env, ed25519_secret_key);
    auto initial = util::ustring_from_bytes(env, initial_dump);

    auto* user_groups = new session::config::UserGroups(secret_key, initial);

    jclass contactsClass = env->FindClass("network/loki/messenger/libsession_util/Contacts");
    jmethodID constructor = env->GetMethodID(contactsClass, "<init>", "(J)V");
    jobject newConfig = env->NewObject(contactsClass, constructor, reinterpret_cast<jlong>(user_groups));

    return newConfig;
}
#pragma clang diagnostic pop
