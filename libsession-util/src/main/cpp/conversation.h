#ifndef SESSION_ANDROID_CONVERSATION_H
#define SESSION_ANDROID_CONVERSATION_H

#include <jni.h>
#include "util.h"
#include "session/config/convo_info_volatile.hpp"

inline session::config::ConvoInfoVolatile *ptrToContacts(JNIEnv *env, jobject obj) {
    jclass contactsClass = env->FindClass("network/loki/messenger/libsession_util/ConversationVolatileConfig");
    jfieldID pointerField = env->GetFieldID(contactsClass, "pointer", "J");
    return (session::config::ConvoInfoVolatile *) env->GetLongField(obj, pointerField);
}

inline jobject serialize_one_on_one(JNIEnv *env, session::config::convo::one_to_one one_to_one) {
    // TODO serialize function
}

inline jobject serialize_open_group(JNIEnv *env, session::config::convo::open_group open_group) {
    // TODO
}

inline jobject serialize_legacy_group(JNIEnv *env, session::config::convo::legacy_closed_group legacy_group) {

}

inline jobject serialize_any(JNIEnv *env, session::config::convo::any)

inline session::config::ConvoInfoVolatile* deserialize_convo_info(JNIEnv *env, jobject info) {
    // TODO deserialize function
}

#endif //SESSION_ANDROID_CONVERSATION_H