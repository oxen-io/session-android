#include <jni.h>
#include <string>
#include "session/config/user_profile.hpp"


session::config::UserProfile* ptrToProfile(JNIEnv* env, jobject obj) {
    jclass configClass = env->FindClass("network/loki/messenger/libsession_util/Config");
    jfieldID pointerField = env->GetFieldID(configClass, "pointer", "J");
    return (session::config::UserProfile*) reinterpret_cast<long>(env->GetLongField(obj, pointerField));
}

extern "C" JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_Config_00024Companion_newInstance(
        JNIEnv* env,
        jobject /*this*/) {

    auto* profile = new session::config::UserProfile();

    jclass configClass = env->FindClass("network/loki/messenger/libsession_util/Config");
    jobject newConfig = env->AllocObject(configClass);

    jfieldID pointerField = env->GetFieldID(configClass, "pointer", "J");
    env->SetLongField(newConfig, pointerField, reinterpret_cast<jlong>(profile));

    return newConfig;
}

extern "C" JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_Config_setName(
        JNIEnv* env,
        jobject obj,
        jstring newName) {
    auto profile = ptrToProfile(env, obj);
    profile->set_name(env->GetStringUTFChars(newName, nullptr));
}