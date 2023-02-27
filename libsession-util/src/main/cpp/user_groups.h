#ifndef SESSION_ANDROID_USER_GROUPS_H
#define SESSION_ANDROID_USER_GROUPS_H

#include "jni.h"
#include "util.h"
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
inline session::config::legacy_group_info deserialize_legacy_group_info(JNIEnv *env, jobject info) {
    auto clazz = env->FindClass("network/loki/messenger/libsession_util/util/GroupInfo$LegacyGroupInfo");
    auto id_field = env->GetFieldID(clazz, "sessionId", "Ljava/lang/String;");
    auto name_field = env->GetFieldID(clazz, "name", "Ljava/lang/String;");
    auto members_field = env->GetFieldID(clazz, "members", "Ljava/util/Map;");
    auto hidden_field = env->GetFieldID(clazz, "hidden", "Z");
    auto enc_pub_key_field = env->GetFieldID(clazz, "encPubKey", "Ljava/lang/String;");
    auto enc_sec_key_field = env->GetFieldID(clazz, "encSecKey", "Ljava/lang/String;");
    auto priority_field = env->GetFieldID(clazz, "priority", "I");
    jstring id = static_cast<jstring>(env->GetObjectField(info, id_field));
    jstring name = static_cast<jstring>(env->GetObjectField(info, name_field));
    jobject members_map = env->GetObjectField(info, members_field);
    bool hidden = env->GetBooleanField(info, hidden_field);
    jstring enc_pub_key = static_cast<jstring>(env->GetObjectField(info, enc_pub_key_field));
    jstring enc_sec_key = static_cast<jstring>(env->GetObjectField(info, enc_sec_key_field));
    int priority = env->GetIntField(info, priority_field);

}

inline std::map<std::string, bool> deserialize_members(JNIEnv *env, jobject members_map) {

}

inline jobject serialize_legacy_group_info(JNIEnv *env, session::config::legacy_group_info info) {
    return nullptr;
}

inline jobject serialize_members(JNIEnv *env, std::map<std::string, bool> members_map) {
    return nullptr;
}

#endif //SESSION_ANDROID_USER_GROUPS_H
