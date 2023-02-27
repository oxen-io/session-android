#ifndef SESSION_ANDROID_CONFIG_BASE_H
#define SESSION_ANDROID_CONFIG_BASE_H

#include "session/config/base.hpp"
#include "util.h"
#include <jni.h>
#include <string>

inline session::config::ConfigBase* ptrToConfigBase(JNIEnv *env, jobject obj) {
    jclass baseClass = env->FindClass("network/loki/messenger/libsession_util/ConfigBase");
    jfieldID pointerField = env->GetFieldID(baseClass, "pointer", "J");
    return (session::config::ConfigBase*) env->GetLongField(obj, pointerField);
}

inline std::pair<std::string, session::ustring> extractHashAndData(JNIEnv *env, jobject kotlin_pair) {
    jclass pair = env->FindClass("kotlin/Pair");
    jfieldID first = env->GetFieldID(pair, "first", "Ljava/lang/Object;");
    jfieldID second = env->GetFieldID(pair, "second", "Ljava/lang/Object;");
    jstring hash_as_jstring = static_cast<jstring>(env->GetObjectField(kotlin_pair, first));
    jbyteArray data_as_jbytes = static_cast<jbyteArray>(env->GetObjectField(kotlin_pair, second));
    auto hash_as_string = env->GetStringUTFChars(hash_as_jstring, nullptr);
    auto data_as_ustring = util::ustring_from_bytes(env, data_as_jbytes);
    auto ret_pair = std::pair<std::string, session::ustring>{hash_as_string, data_as_ustring};
    env->ReleaseStringUTFChars(hash_as_jstring, hash_as_string);
    return ret_pair;
}

#endif