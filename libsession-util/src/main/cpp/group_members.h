#ifndef SESSION_ANDROID_GROUP_MEMBERS_H
#define SESSION_ANDROID_GROUP_MEMBERS_H

#include "util.h"

inline session::config::groups::Members* ptrToMembers(JNIEnv* env, jobject obj) {
    jclass configClass = env->FindClass("network/loki/messenger/libsession_util/GroupMembersConfig");
    jfieldID pointerField = env->GetFieldID(configClass, "pointer", "J");
    return (session::config::groups::Members*) env->GetLongField(obj, pointerField);
}


#endif //SESSION_ANDROID_GROUP_MEMBERS_H
