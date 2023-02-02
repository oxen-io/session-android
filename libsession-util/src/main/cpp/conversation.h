#ifndef SESSION_ANDROID_CONVERSATION_H
#define SESSION_ANDROID_CONVERSATION_H

#include <jni.h>
#include "util.h"
#include "session/config/convo_info_volatile.hpp"

inline session::config::ConvoInfoVolatile *ptrToConvoInfo(JNIEnv *env, jobject obj) {
    jclass contactsClass = env->FindClass("network/loki/messenger/libsession_util/ConversationVolatileConfig");
    jfieldID pointerField = env->GetFieldID(contactsClass, "pointer", "J");
    return (session::config::ConvoInfoVolatile *) env->GetLongField(obj, pointerField);
}

inline jobject serialize_one_to_one(JNIEnv *env, session::config::convo::one_to_one one_to_one) {
    jclass clazz = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$OneToOne");
    jmethodID constructor = env->GetMethodID(clazz, "<init>", "(Ljava/lang/String;JZ)V");
    auto session_id = env->NewStringUTF(one_to_one.session_id.data());
    auto last_read = one_to_one.last_read;
    auto unread = one_to_one.unread;
    jobject serialized = env->NewObject(clazz, constructor, session_id, last_read, unread);
    return serialized;
}

inline jobject serialize_open_group(JNIEnv *env, session::config::convo::open_group open_group) {
    jclass clazz = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$OpenGroup");
    jmethodID constructor = env->GetMethodID(clazz, "<init>", "(Ljava/lang/String;Ljava/lang/String;[BJZ)V");
    auto base_url = env->NewStringUTF(open_group.base_url().data());
    auto room = env->NewStringUTF(open_group.room().data());
    auto pubkey_ustring = open_group.pubkey();
    auto pubkey_jarray = util::bytes_from_ustring(env, session::ustring_view {pubkey_ustring.data(), pubkey_ustring.size()});
    auto last_read = open_group.last_read;
    auto unread = open_group.unread;
    jobject serialized = env->NewObject(clazz, constructor, base_url, room, pubkey_jarray, last_read, unread);
    return serialized;
}

inline jobject serialize_legacy_group(JNIEnv *env, session::config::convo::legacy_closed_group legacy_group) {
    jclass clazz = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$LegacyClosedGroup");
    jmethodID constructor = env->GetMethodID(clazz, "<init>", "(Ljava/lang/String;JZ)V");
    auto group_id = env->NewStringUTF(legacy_group.id.data());
    auto last_read = legacy_group.last_read;
    auto unread = legacy_group.unread;
    jobject serialized = env->NewObject(clazz, constructor, group_id, last_read, unread);
    return serialized;
}

inline jobject serialize_any(JNIEnv *env, session::config::convo::any any) {
    if (auto* dm = std::get_if<session::config::convo::one_to_one>(&any)) {
        return serialize_one_to_one(env, *dm);
    } else if (auto* og = std::get_if<session::config::convo::open_group>(&any)) {
        return serialize_open_group(env, *og);
    } else if (auto* lgc = std::get_if<session::config::convo::legacy_closed_group>(&any)) {
        return serialize_legacy_group(env, *lgc);
    }
    return nullptr;
}

inline session::config::convo::one_to_one* deserialize_one_to_one(JNIEnv *env, jobject info) {
    auto clazz = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$OneToOne");
    auto id_getter = env->GetFieldID(clazz, "sessionId", "Ljava/lang/String;");
    auto last_read_getter = env->GetFieldID(clazz, "lastRead", "J");
    auto unread_getter = env->GetFieldID(clazz, "unread", "Z");
    jstring id = static_cast<jstring>(env->GetObjectField(info, id_getter));
    auto id_chars = env->GetStringUTFChars(id, nullptr);
    std::string id_string = std::string{id_chars};
    auto deserialized = new session::config::convo::one_to_one(id_string);
    deserialized->last_read = env->GetLongField(info, last_read_getter);
    deserialized->unread = env->GetBooleanField(info, unread_getter);
    env->ReleaseStringUTFChars(id, id_chars);
    return deserialized;
}

inline session::config::convo::open_group* deserialize_open_group(JNIEnv *env, jobject info) {
    auto clazz = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$OpenGroup");
    auto url_getter = env->GetFieldID(clazz, "baseUrl", "Ljava/lang/String;");
    auto room_getter = env->GetFieldID(clazz, "room", "Ljava/lang/String;");
    auto pub_key_getter = env->GetFieldID(clazz, "pubKey", "[B");
    auto last_read_getter = env->GetFieldID(clazz, "lastRead", "J");
    auto unread_getter = env->GetFieldID(clazz, "unread", "Z");
    jstring base_url = static_cast<jstring>(env->GetObjectField(info, url_getter));
    jstring room = static_cast<jstring>(env->GetObjectField(info, room_getter));
    jbyteArray pub_key = (jbyteArray)env->GetObjectField(info, pub_key_getter);
    auto base_bytes = env->GetStringUTFChars(base_url, nullptr);
    auto base_string = std::string {base_bytes};
    auto room_bytes = env->GetStringUTFChars(room, nullptr);
    auto room_string = std::string {room_bytes};
    auto pub_key_ustring = util::ustring_from_bytes(env, pub_key);

    auto deserialized = new session::config::convo::open_group(base_string, room_string,pub_key_ustring);
    deserialized->last_read = env->GetLongField(info, last_read_getter);
    deserialized->unread = env->GetBooleanField(info, unread_getter);
    env->ReleaseStringUTFChars(base_url, base_bytes);
    env->ReleaseStringUTFChars(room, room_bytes);
    return deserialized;
}

inline session::config::convo::legacy_closed_group* deserialize_legacy_closed_group(JNIEnv *env, jobject info) {
    auto clazz = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$LegacyClosedGroup");
    auto group_id_getter = env->GetFieldID(clazz, "groupId", "Ljava/lang/String;");
    auto last_read_getter = env->GetFieldID(clazz, "lastRead", "J");
    auto unread_getter = env->GetFieldID(clazz, "unread", "Z");
    auto group_id = static_cast<jstring>(env->GetObjectField(info, group_id_getter));
    auto group_id_bytes = env->GetStringUTFChars(group_id, nullptr);
    auto group_id_string = std::string{group_id_bytes};
    auto deserialized = new session::config::convo::legacy_closed_group(group_id_string);
    deserialized->last_read = env->GetLongField(info, last_read_getter);
    deserialized->unread = env->GetBooleanField(info, unread_getter);
    env->ReleaseStringUTFChars(group_id, group_id_bytes);
    return deserialized;
}

inline session::config::convo::any* deserialize_any(JNIEnv *env, jobject convo) {
    auto oto_class = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$OneToOne");
    auto og_class = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$OpenGroup");
    auto lgc_class = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$LegacyClosedGroup");
    auto object_class = env->GetObjectClass(convo);
    if (env->IsSameObject(object_class, oto_class)) {
        return new session::config::convo::any{*deserialize_one_to_one(env, convo)};
    } else if (env->IsSameObject(object_class, og_class)) {
        return new session::config::convo::any{*deserialize_open_group(env, convo)};
    } else if (env->IsSameObject(object_class, lgc_class)) {
        return new session::config::convo::any{*deserialize_legacy_closed_group(env, convo)};
    }
    return nullptr;
}

#endif //SESSION_ANDROID_CONVERSATION_H