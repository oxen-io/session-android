#ifndef SESSION_ANDROID_USER_GROUPS_H
#define SESSION_ANDROID_USER_GROUPS_H

#include "jni.h"
#include "util.h"
#include "session/config/user_groups.hpp"

inline session::config::UserGroups* ptrToUserGroups(JNIEnv *env, jobject obj) {
    jclass configClass = env->FindClass("network/loki/messenger/libsession_util/UserGroupsConfig");
    jfieldID pointerField = env->GetFieldID(configClass, "pointer", "J");
    return (session::config::UserGroups*) env->GetLongField(obj, pointerField);
}

#endif //SESSION_ANDROID_USER_GROUPS_H
