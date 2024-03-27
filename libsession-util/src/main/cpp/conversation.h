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

inline jobject serialize_open_group(JNIEnv *env, session::config::convo::community community) {
    jclass clazz = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$Community");
    auto base_community = util::serialize_base_community(env, community);
    jmethodID constructor = env->GetMethodID(clazz, "<init>",
                                             "(Lnetwork/loki/messenger/libsession_util/util/BaseCommunityInfo;JZ)V");
    auto last_read = community.last_read;
    auto unread = community.unread;
    jobject serialized = env->NewObject(clazz, constructor, base_community, last_read, unread);
    return serialized;
}

inline jobject serialize_legacy_group(JNIEnv *env, session::config::convo::legacy_group group) {
    jclass clazz = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$LegacyGroup");
    jmethodID constructor = env->GetMethodID(clazz, "<init>", "(Ljava/lang/String;JZ)V");
    auto group_id = env->NewStringUTF(group.id.data());
    auto last_read = group.last_read;
    auto unread = group.unread;
    jobject serialized = env->NewObject(clazz, constructor, group_id, last_read, unread);
    return serialized;
}

inline jobject serialize_any(JNIEnv *env, session::config::convo::any any) {
    if (auto* dm = std::get_if<session::config::convo::one_to_one>(&any)) {
        return serialize_one_to_one(env, *dm);
    } else if (auto* og = std::get_if<session::config::convo::community>(&any)) {
        return serialize_open_group(env, *og);
    } else if (auto* lgc = std::get_if<session::config::convo::legacy_group>(&any)) {
        return serialize_legacy_group(env, *lgc);
    }
    return nullptr;
}

inline session::config::convo::one_to_one deserialize_one_to_one(JNIEnv *env, jobject info, session::config::ConvoInfoVolatile *conf) {
    auto clazz = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$OneToOne");
    auto id_getter = env->GetFieldID(clazz, "sessionId", "Ljava/lang/String;");
    auto last_read_getter = env->GetFieldID(clazz, "lastRead", "J");
    auto unread_getter = env->GetFieldID(clazz, "unread", "Z");
    jstring id = static_cast<jstring>(env->GetObjectField(info, id_getter));
    auto id_chars = env->GetStringUTFChars(id, nullptr);
    std::string id_string = std::string{id_chars};
    auto deserialized = conf->get_or_construct_1to1(id_string);
    deserialized.last_read = env->GetLongField(info, last_read_getter);
    deserialized.unread = env->GetBooleanField(info, unread_getter);
    env->ReleaseStringUTFChars(id, id_chars);
    return deserialized;
}

inline session::config::convo::community deserialize_community(JNIEnv *env, jobject info, session::config::ConvoInfoVolatile *conf) {
    auto clazz = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$Community");
    auto base_community_getter = env->GetFieldID(clazz, "baseCommunityInfo", "Lnetwork/loki/messenger/libsession_util/util/BaseCommunityInfo;");
    auto last_read_getter = env->GetFieldID(clazz, "lastRead", "J");
    auto unread_getter = env->GetFieldID(clazz, "unread", "Z");

    auto base_community_info = env->GetObjectField(info, base_community_getter);

    auto base_community_deserialized = util::deserialize_base_community(env, base_community_info);
    auto deserialized = conf->get_or_construct_community(
        base_community_deserialized.base_url(),
        base_community_deserialized.room(),
        base_community_deserialized.pubkey()
    );

    deserialized.last_read = env->GetLongField(info, last_read_getter);
    deserialized.unread = env->GetBooleanField(info, unread_getter);

    return deserialized;
}

inline session::config::convo::legacy_group deserialize_legacy_closed_group(JNIEnv *env, jobject info, session::config::ConvoInfoVolatile *conf) {
    auto clazz = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$LegacyGroup");
    auto group_id_getter = env->GetFieldID(clazz, "groupId", "Ljava/lang/String;");
    auto last_read_getter = env->GetFieldID(clazz, "lastRead", "J");
    auto unread_getter = env->GetFieldID(clazz, "unread", "Z");
    auto group_id = static_cast<jstring>(env->GetObjectField(info, group_id_getter));
    auto group_id_bytes = env->GetStringUTFChars(group_id, nullptr);
    auto group_id_string = std::string{group_id_bytes};
    auto deserialized = conf->get_or_construct_legacy_group(group_id_string);
    deserialized.last_read = env->GetLongField(info, last_read_getter);
    deserialized.unread = env->GetBooleanField(info, unread_getter);
    env->ReleaseStringUTFChars(group_id, group_id_bytes);
    return deserialized;
}

inline std::optional<session::config::convo::any> deserialize_any(JNIEnv *env, jobject convo, session::config::ConvoInfoVolatile *conf) {
    auto oto_class = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$OneToOne");
    auto og_class = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$Community");
    auto lgc_class = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$LegacyGroup");
    auto object_class = env->GetObjectClass(convo);
    if (env->IsSameObject(object_class, oto_class)) {
        return session::config::convo::any{deserialize_one_to_one(env, convo, conf)};
    } else if (env->IsSameObject(object_class, og_class)) {
        return session::config::convo::any{deserialize_community(env, convo, conf)};
    } else if (env->IsSameObject(object_class, lgc_class)) {
        return session::config::convo::any{deserialize_legacy_closed_group(env, convo, conf)};
    }
    return std::nullopt;
}

#endif //SESSION_ANDROID_CONVERSATION_H