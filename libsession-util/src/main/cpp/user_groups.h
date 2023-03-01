#ifndef SESSION_ANDROID_USER_GROUPS_H
#define SESSION_ANDROID_USER_GROUPS_H

#include "jni.h"
#include "util.h"
#include "conversation.h"
#include "session/config/user_groups.hpp"

inline session::config::UserGroups* ptrToUserGroups(JNIEnv *env, jobject obj) {
    jclass configClass = env->FindClass("network/loki/messenger/libsession_util/UserGroupsConfig");
    jfieldID pointerField = env->GetFieldID(configClass, "pointer", "J");
    return (session::config::UserGroups*) env->GetLongField(obj, pointerField);
}

/**
val sessionId: String,
val name: String,
val members: Map<String, Boolean>,
val hidden: Boolean,
val encPubKey: String,
val encSecKey: String,
val priority: Int
 */
inline session::config::legacy_group_info* deserialize_legacy_group_info(JNIEnv *env, jobject info) {
    auto clazz = env->FindClass("network/loki/messenger/libsession_util/util/GroupInfo$LegacyGroupInfo");
    auto id_field = env->GetFieldID(clazz, "sessionId", "Ljava/lang/String;");
    auto name_field = env->GetFieldID(clazz, "name", "Ljava/lang/String;");
    auto members_field = env->GetFieldID(clazz, "members", "Ljava/util/Map;");
    auto hidden_field = env->GetFieldID(clazz, "hidden", "Z");
    auto enc_pub_key_field = env->GetFieldID(clazz, "encPubKey", "[B");
    auto enc_sec_key_field = env->GetFieldID(clazz, "encSecKey", "[B");
    auto priority_field = env->GetFieldID(clazz, "priority", "I");
    jstring id = static_cast<jstring>(env->GetObjectField(info, id_field));
    jstring name = static_cast<jstring>(env->GetObjectField(info, name_field));
    jobject members_map = env->GetObjectField(info, members_field);
    bool hidden = env->GetBooleanField(info, hidden_field);
    jbyteArray enc_pub_key = static_cast<jbyteArray>(env->GetObjectField(info, enc_pub_key_field));
    jbyteArray enc_sec_key = static_cast<jbyteArray>(env->GetObjectField(info, enc_sec_key_field));
    int priority = env->GetIntField(info, priority_field);

    auto id_bytes = env->GetStringUTFChars(id, nullptr);
    auto name_bytes = env->GetStringUTFChars(name, nullptr);
    auto enc_pub_key_bytes = util::ustring_from_bytes(env, enc_pub_key);
    auto enc_sec_key_bytes = util::ustring_from_bytes(env, enc_sec_key);

    auto info_deserialized = new session::config::legacy_group_info(id_bytes);

    info_deserialized->priority = priority;
    // TODO: iterate over map and insert as admins
    info_deserialized->enc_pubkey = enc_pub_key_bytes;
    info_deserialized->enc_seckey = enc_sec_key_bytes;
    env->ReleaseStringUTFChars(id, id_bytes);
    env->ReleaseStringUTFChars(name, name_bytes);
    return info_deserialized;
}

inline std::map<std::string, bool> deserialize_members(JNIEnv *env, jobject members_map) {

}

inline jobject serialize_legacy_group_info(JNIEnv *env, session::config::legacy_group_info info) {
    return nullptr;
}

inline jobject serialize_members(JNIEnv *env, std::map<std::string, bool> members_map) {
    return nullptr;
}

inline jobject serialize_community_info(JNIEnv *env, session::config::community_info info) {
    auto priority = info.priority;
    auto serialized_community = util::serialize_base_community(env, info);
    auto clazz = env->FindClass("network/loki/messenger/libsession_util/util/GroupInfo$CommunityGroupInfo");

    return serialized_community;
}

#endif //SESSION_ANDROID_USER_GROUPS_H
