#include <jni.h>
#include <string>
#include "session/config/user_profile.hpp"

session::config::ConfigBase* ptrToConfigBase(JNIEnv *env, jobject obj) {
    jclass baseClass = env->FindClass("network/loki/messenger/libsession_util/ConfigBase");
    jfieldID pointerField = env->GetFieldID(baseClass, "pointer", "J");
    return (session::config::ConfigBase*) env->GetLongField(obj, pointerField);
}

session::config::UserProfile* ptrToProfile(JNIEnv* env, jobject obj) {
    jclass configClass = env->FindClass("network/loki/messenger/libsession_util/UserProfile");
    jfieldID pointerField = env->GetFieldID(configClass, "pointer", "J");
    return (session::config::UserProfile*) env->GetLongField(obj, pointerField);
}

extern "C" JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_00024Companion_newInstance(
        JNIEnv* env,
        jobject) {

    auto* profile = new session::config::UserProfile();

    jclass userClass = env->FindClass("network/loki/messenger/libsession_util/UserProfile");
    jmethodID constructor = env->GetMethodID(userClass, "<init>", "(J)V");
    jobject newConfig = env->NewObject(userClass, constructor, reinterpret_cast<jlong>(profile));

    return newConfig;
}

extern "C" JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_setName(
        JNIEnv* env,
        jobject obj,
        jstring newName) {
    auto profile = ptrToProfile(env, obj);
    profile->set_name(env->GetStringUTFChars(newName, nullptr));
}