#ifndef SESSION_ANDROID_GROUP_KEYS_H
#define SESSION_ANDROID_GROUP_KEYS_H

#include "util.h"

inline session::config::groups::Keys* ptrToKeys(JNIEnv* env, jobject obj) {
    jclass configClass = env->FindClass("network/loki/messenger/libsession_util/GroupKeysConfig");
    jfieldID pointerField = env->GetFieldID(configClass, "pointer", "J");
    return (session::config::groups::Keys*) env->GetLongField(obj, pointerField);
}

#endif //SESSION_ANDROID_GROUP_KEYS_H
