#ifndef SESSION_ANDROID_USER_PROFILE_H
#define SESSION_ANDROID_USER_PROFILE_H

#include "session/config/user_profile.hpp"
#include <jni.h>
#include <string>

inline session::config::UserProfile* ptrToProfile(JNIEnv* env, jobject obj) {
    jclass configClass = env->FindClass("network/loki/messenger/libsession_util/UserProfile");
    jfieldID pointerField = env->GetFieldID(configClass, "pointer", "J");
    return (session::config::UserProfile*) env->GetLongField(obj, pointerField);
}

#endif