#ifndef SESSION_ANDROID_CONFIG_BASE_H
#define SESSION_ANDROID_CONFIG_BASE_H

#include "session/config/base.hpp"
#include <jni.h>
#include <string>

inline session::config::ConfigBase* ptrToConfigBase(JNIEnv *env, jobject obj) {
    jclass baseClass = env->FindClass("network/loki/messenger/libsession_util/ConfigBase");
    jfieldID pointerField = env->GetFieldID(baseClass, "pointer", "J");
    return (session::config::ConfigBase*) env->GetLongField(obj, pointerField);
}

#endif