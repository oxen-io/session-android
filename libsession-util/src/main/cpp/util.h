#ifndef SESSION_ANDROID_UTIL_H
#define SESSION_ANDROID_UTIL_H

#include <jni.h>
#include <array>
#include <optional>
#include "session/types.hpp"
#include "session/config/profile_pic.hpp"

namespace util {
    jbyteArray bytes_from_ustring(JNIEnv* env, session::ustring_view from_str);
    session::ustring ustring_from_bytes(JNIEnv* env, jbyteArray byteArray);
    jobject serialize_user_pic(JNIEnv *env, session::config::profile_pic pic);
    std::pair<jstring, jbyteArray> deserialize_user_pic(JNIEnv *env, jobject user_pic);
}

#endif