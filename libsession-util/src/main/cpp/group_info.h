#ifndef SESSION_ANDROID_GROUP_INFO_H
#define SESSION_ANDROID_GROUP_INFO_H

#include "util.h"

inline session::config::groups::Info* ptrToInfo(JNIEnv* env, jobject obj) {
    jclass configClass = env->FindClass("network/loki/messenger/libsession_util/GroupInfoConfig");
    jfieldID pointerField = env->GetFieldID(configClass, "pointer", "J");
    return (session::config::groups::Info*) env->GetLongField(obj, pointerField);
}

#endif //SESSION_ANDROID_GROUP_INFO_H
