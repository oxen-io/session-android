#ifndef SESSION_ANDROID_UTIL_H
#define SESSION_ANDROID_UTIL_H

#include <jni.h>
#include <array>
#include <optional>
#include "session/types.hpp"
#include "session/config/profile_pic.hpp"
#include "session/config/user_groups.hpp"
#include "session/config/expiring.hpp"

namespace util {
    extern std::mutex util_mutex_;
    jbyteArray bytes_from_ustring(JNIEnv* env, session::ustring_view from_str);
    session::ustring ustring_from_bytes(JNIEnv* env, jbyteArray byteArray);
    jobject serialize_user_pic(JNIEnv *env, session::config::profile_pic pic);
    std::pair<jstring, jbyteArray> deserialize_user_pic(JNIEnv *env, jobject user_pic);
    jobject serialize_base_community(JNIEnv *env, const session::config::community& base_community);
    session::config::community deserialize_base_community(JNIEnv *env, jobject base_community);
    jobject serialize_expiry(JNIEnv *env, const session::config::expiration_mode& mode, const std::chrono::seconds& time_seconds);
    std::pair<session::config::expiration_mode, long> deserialize_expiry(JNIEnv *env, jobject expiry_mode);
}

#endif