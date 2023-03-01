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

extern "C"
JNIEXPORT jint JNICALL
Java_network_loki_messenger_libsession_1util_util_GroupInfo_00024LegacyGroupInfo_00024Companion_NAME_1MAX_1LENGTH(
        JNIEnv *env, jobject thiz) {
    return session::config::legacy_group_info::NAME_MAX_LENGTH;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_UserGroupsConfig_getCommunityInfo(JNIEnv *env,
                                                                               jobject thiz,
                                                                               jstring base_url,
                                                                               jstring room) {
    auto conf = ptrToUserGroups(env, thiz);
    auto base_url_bytes = env->GetStringUTFChars(base_url, nullptr);
    auto room_bytes = env->GetStringUTFChars(room, nullptr);

    auto community = conf->get_community(base_url_bytes, room_bytes);

    jobject community_info = nullptr;

    if (community) {
        community_info = serialize_community_info(env, *community);
    }
    env->ReleaseStringUTFChars(base_url, base_url_bytes);
    env->ReleaseStringUTFChars(room, room_bytes);
    return community_info;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_UserGroupsConfig_getLegacyGroupInfo(JNIEnv *env,
                                                                                 jobject thiz,
                                                                                 jstring session_id) {
    auto conf = ptrToUserGroups(env, thiz);
    auto id_bytes = env->GetStringUTFChars(session_id, nullptr);
    auto legacy_group = conf->get_legacy_group(id_bytes);
    jobject return_group = nullptr;
    if (legacy_group) {
        return_group = serialize_legacy_group_info(env, *legacy_group);
    }
    env->ReleaseStringUTFChars(session_id, id_bytes);
    return return_group;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_UserGroupsConfig_getOrConstructCommunityInfo(
        JNIEnv *env, jobject thiz, jstring base_url, jstring room, jstring pub_key_hex) {
    auto conf = ptrToUserGroups(env, thiz);
    auto base_url_bytes = env->GetStringUTFChars(base_url, nullptr);
    auto room_bytes = env->GetStringUTFChars(room, nullptr);
    auto pub_hex_bytes = env->GetStringUTFChars(pub_key_hex, nullptr);

    auto group = conf->get_or_construct_community(base_url_bytes, room_bytes, pub_hex_bytes);

    env->ReleaseStringUTFChars(base_url, base_url_bytes);
    env->ReleaseStringUTFChars(room, room_bytes);
    env->ReleaseStringUTFChars(pub_key_hex, pub_hex_bytes);
    return serialize_community_info(env, group);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_UserGroupsConfig_getOrConstructLegacyGroupInfo(
        JNIEnv *env, jobject thiz, jstring session_id) {
    auto conf = ptrToUserGroups(env, thiz);
    auto id_bytes = env->GetStringUTFChars(session_id, nullptr);
    auto group = conf->get_or_construct_legacy_group(id_bytes);
    env->ReleaseStringUTFChars(session_id, id_bytes);
    return serialize_legacy_group_info(env, group);
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_UserGroupsConfig_set__Lnetwork_loki_messenger_libsession_1util_util_GroupInfo_CommunityGroupInfo_2(
        JNIEnv *env, jobject thiz, jobject community_info) {
    auto conf = ptrToUserGroups(env, thiz);
    auto deserialized = (env, community_info);
    conf->set(deserialized);
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_UserGroupsConfig_set__Lnetwork_loki_messenger_libsession_1util_util_GroupInfo_LegacyGroupInfo_2(
        JNIEnv *env, jobject thiz, jobject legacy_group_info) {
    auto conf = ptrToUserGroups(env, thiz);
    auto deserialized = deserialize_legacy_group_info(env, legacy_group_info);
    conf->set(*deserialized);
}