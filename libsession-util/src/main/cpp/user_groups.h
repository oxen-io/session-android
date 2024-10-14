
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

inline void deserialize_members_into(JNIEnv *env, jobject members_map, session::config::legacy_group_info& to_append_group) {
    jclass map_class = env->FindClass("java/util/Map");
    jclass map_entry_class = env->FindClass("java/util/Map$Entry");
    jclass set_class = env->FindClass("java/util/Set");
    jclass iterator_class = env->FindClass("java/util/Iterator");
    jclass boxed_bool = env->FindClass("java/lang/Boolean");

    jmethodID get_entry_set = env->GetMethodID(map_class, "entrySet", "()Ljava/util/Set;");
    jmethodID get_at = env->GetMethodID(set_class, "iterator", "()Ljava/util/Iterator;");
    jmethodID has_next = env->GetMethodID(iterator_class, "hasNext", "()Z");
    jmethodID next = env->GetMethodID(iterator_class, "next", "()Ljava/lang/Object;");
    jmethodID get_key = env->GetMethodID(map_entry_class, "getKey", "()Ljava/lang/Object;");
    jmethodID get_value = env->GetMethodID(map_entry_class, "getValue", "()Ljava/lang/Object;");
    jmethodID get_bool_value = env->GetMethodID(boxed_bool, "booleanValue", "()Z");

    jobject entry_set = env->CallObjectMethod(members_map, get_entry_set);
    jobject iterator = env->CallObjectMethod(entry_set, get_at);

    while (env->CallBooleanMethod(iterator, has_next)) {
        jobject entry = env->CallObjectMethod(iterator, next);
        jstring key = static_cast<jstring>(env->CallObjectMethod(entry, get_key));
        jobject boxed = env->CallObjectMethod(entry, get_value);
        bool is_admin = env->CallBooleanMethod(boxed, get_bool_value);
        auto member_string = env->GetStringUTFChars(key, nullptr);
        to_append_group.insert(member_string, is_admin);
        env->ReleaseStringUTFChars(key, member_string);
    }
}

inline session::config::legacy_group_info deserialize_legacy_group_info(JNIEnv *env, jobject info, session::config::UserGroups* conf) {
    auto clazz = env->FindClass("network/loki/messenger/libsession_util/util/GroupInfo$LegacyGroupInfo");
    auto id_field = env->GetFieldID(clazz, "accountId", "Ljava/lang/String;");
    auto name_field = env->GetFieldID(clazz, "name", "Ljava/lang/String;");
    auto members_field = env->GetFieldID(clazz, "members", "Ljava/util/Map;");
    auto enc_pub_key_field = env->GetFieldID(clazz, "encPubKey", "[B");
    auto enc_sec_key_field = env->GetFieldID(clazz, "encSecKey", "[B");
    auto priority_field = env->GetFieldID(clazz, "priority", "J");
    auto disappearing_timer_field = env->GetFieldID(clazz, "disappearingTimer", "J");
    auto joined_at_field = env->GetFieldID(clazz, "joinedAt", "J");
    auto id = static_cast<jstring>(env->GetObjectField(info, id_field));
    jstring name = static_cast<jstring>(env->GetObjectField(info, name_field));
    jobject members_map = env->GetObjectField(info, members_field);
    jbyteArray enc_pub_key = static_cast<jbyteArray>(env->GetObjectField(info, enc_pub_key_field));
    jbyteArray enc_sec_key = static_cast<jbyteArray>(env->GetObjectField(info, enc_sec_key_field));
    int priority = env->GetLongField(info, priority_field);
    long joined_at = env->GetLongField(info, joined_at_field);

    auto id_bytes = util::string_from_jstring(env, id);
    auto name_bytes = env->GetStringUTFChars(name, nullptr);
    auto enc_pub_key_bytes = util::ustring_from_bytes(env, enc_pub_key);
    auto enc_sec_key_bytes = util::ustring_from_bytes(env, enc_sec_key);

    auto info_deserialized = conf->get_or_construct_legacy_group(id_bytes);

    auto current_members = info_deserialized.members();
    for (auto member = current_members.begin(); member != current_members.end(); ++member) {
        info_deserialized.erase(member->first);
    }
    deserialize_members_into(env, members_map, info_deserialized);
    info_deserialized.name = name_bytes;
    info_deserialized.enc_pubkey = enc_pub_key_bytes;
    info_deserialized.enc_seckey = enc_sec_key_bytes;
    info_deserialized.priority = priority;
    info_deserialized.disappearing_timer = std::chrono::seconds(env->GetLongField(info, disappearing_timer_field));
    info_deserialized.joined_at = joined_at;
    env->ReleaseStringUTFChars(name, name_bytes);
    return info_deserialized;
}

inline session::config::community_info deserialize_community_info(JNIEnv *env, jobject info, session::config::UserGroups* conf) {
    auto clazz = env->FindClass("network/loki/messenger/libsession_util/util/GroupInfo$CommunityGroupInfo");
    auto base_info = env->GetFieldID(clazz, "community", "Lnetwork/loki/messenger/libsession_util/util/BaseCommunityInfo;");
    auto priority = env->GetFieldID(clazz, "priority", "J");
    jobject base_community_info = env->GetObjectField(info, base_info);
    auto deserialized_base_info = util::deserialize_base_community(env, base_community_info);
    int deserialized_priority = env->GetLongField(info, priority);
    auto community_info = conf->get_or_construct_community(deserialized_base_info.base_url(), deserialized_base_info.room(), deserialized_base_info.pubkey_hex());
    community_info.priority = deserialized_priority;
    return community_info;
}

inline jobject serialize_members(JNIEnv *env, std::map<std::string, bool> members_map) {
    jclass map_class = env->FindClass("java/util/HashMap");
    jclass boxed_bool = env->FindClass("java/lang/Boolean");
    jmethodID map_constructor = env->GetMethodID(map_class, "<init>", "()V");
    jmethodID insert = env->GetMethodID(map_class, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    jmethodID new_bool = env->GetMethodID(boxed_bool, "<init>", "(Z)V");

    jobject new_map = env->NewObject(map_class, map_constructor);
    for (auto it = members_map.begin(); it != members_map.end(); it++) {
        auto account_id = env->NewStringUTF(it->first.data());
        bool is_admin = it->second;
        auto jbool = env->NewObject(boxed_bool, new_bool, is_admin);
        env->CallObjectMethod(new_map, insert, account_id, jbool);
    }
    return new_map;
}

inline jobject serialize_legacy_group_info(JNIEnv *env, session::config::legacy_group_info info) {
    jstring account_id = env->NewStringUTF(info.session_id.data());
    jstring name = env->NewStringUTF(info.name.data());
    jobject members = serialize_members(env, info.members());
    jbyteArray enc_pubkey = util::bytes_from_ustring(env, info.enc_pubkey);
    jbyteArray enc_seckey = util::bytes_from_ustring(env, info.enc_seckey);
    long long priority = info.priority;
    long long joined_at = info.joined_at;

    jclass legacy_group_class = env->FindClass("network/loki/messenger/libsession_util/util/GroupInfo$LegacyGroupInfo");
    jmethodID constructor = env->GetMethodID(legacy_group_class, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;[B[BJJJ)V");
    jobject serialized = env->NewObject(legacy_group_class, constructor, account_id, name, members, enc_pubkey, enc_seckey, priority, (jlong) info.disappearing_timer.count(), joined_at);
    return serialized;
}

inline jobject serialize_closed_group_info(JNIEnv* env, session::config::group_info info) {
    auto session_id = util::serialize_account_id(env, info.id);
    jbyteArray admin_bytes = info.secretkey.empty() ? nullptr : util::bytes_from_ustring(env, info.secretkey);
    jbyteArray auth_bytes = info.auth_data.empty() ? nullptr : util::bytes_from_ustring(env, info.auth_data);
    jstring name = util::jstringFromOptional(env, info.name);

    jclass group_info_class = env->FindClass("network/loki/messenger/libsession_util/util/GroupInfo$ClosedGroupInfo");
    jmethodID constructor = env->GetMethodID(group_info_class, "<init>","(Lorg/session/libsignal/utilities/AccountId;[B[BJZLjava/lang/String;)V");
    jobject return_object = env->NewObject(group_info_class,constructor,
                                           session_id, admin_bytes, auth_bytes, (jlong)info.priority, info.invited, name);
    return return_object;
}

inline session::config::group_info deserialize_closed_group_info(JNIEnv* env, jobject info_serialized) {
    jclass closed_group_class = env->FindClass("network/loki/messenger/libsession_util/util/GroupInfo$ClosedGroupInfo");
    jfieldID id_field = env->GetFieldID(closed_group_class, "groupAccountId", "Lorg/session/libsignal/utilities/AccountId;");
    jfieldID secret_field = env->GetFieldID(closed_group_class, "adminKey", "[B");
    jfieldID auth_field = env->GetFieldID(closed_group_class, "authData", "[B");
    jfieldID priority_field = env->GetFieldID(closed_group_class, "priority", "J");
    jfieldID invited_field = env->GetFieldID(closed_group_class, "invited", "Z");
    jfieldID name_field = env->GetFieldID(closed_group_class, "name", "Ljava/lang/String;");


    jobject id_jobject = env->GetObjectField(info_serialized, id_field);
    jbyteArray secret_jBytes = (jbyteArray)env->GetObjectField(info_serialized, secret_field);
    jbyteArray auth_jBytes = (jbyteArray)env->GetObjectField(info_serialized, auth_field);
    jstring name_jstring = (jstring)env->GetObjectField(info_serialized, name_field);

    auto id_bytes = util::deserialize_account_id(env, id_jobject);
    auto secret_bytes = util::ustring_from_bytes(env, secret_jBytes);
    auto auth_bytes = util::ustring_from_bytes(env, auth_jBytes);
    auto name = util::string_from_jstring(env, name_jstring);

    session::config::group_info group_info(id_bytes);
    group_info.auth_data = auth_bytes;
    group_info.secretkey = secret_bytes;
    group_info.priority = env->GetLongField(info_serialized, priority_field);
    group_info.invited = env->GetBooleanField(info_serialized, invited_field);
    group_info.name = name;

    return group_info;
}

inline jobject serialize_community_info(JNIEnv *env, session::config::community_info info) {
    auto priority = (long long)info.priority;
    auto serialized_info = util::serialize_base_community(env, info);
    auto clazz = env->FindClass("network/loki/messenger/libsession_util/util/GroupInfo$CommunityGroupInfo");
    jmethodID constructor = env->GetMethodID(clazz, "<init>", "(Lnetwork/loki/messenger/libsession_util/util/BaseCommunityInfo;J)V");
    jobject serialized = env->NewObject(clazz, constructor, serialized_info, priority);
    return serialized;
}

#endif //SESSION_ANDROID_USER_GROUPS_H
