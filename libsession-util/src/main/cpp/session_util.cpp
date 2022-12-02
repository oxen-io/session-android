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

jbyteArray bytes_from_string(JNIEnv* env, std::string_view from_str) {
    size_t length = from_str.length();
    jsize jlength = (jsize)length;
    jbyteArray new_array = env->NewByteArray(jlength);
    env->SetByteArrayRegion(new_array, 0, jlength, reinterpret_cast<const jbyte *>(from_str.data()));
    return new_array;
}

std::string string_from_bytes(JNIEnv* env, jbyteArray byteArray) {
    size_t len = env->GetArrayLength(byteArray);
    jbyte* bytes = env->GetByteArrayElements(byteArray, nullptr);
    std::string newSt((char*)bytes, len);
    env->ReleaseByteArrayElements(byteArray, bytes, 0);
    return newSt;
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
        jobject thiz,
        jstring newName) {
    auto profile = ptrToProfile(env, thiz);
    profile->set_name(env->GetStringUTFChars(newName, nullptr));
}

extern "C"
JNIEXPORT jstring JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_getName(JNIEnv *env, jobject thiz) {
    auto profile = ptrToProfile(env, thiz);
    auto name = profile->get_name();
    if (name == nullptr) return nullptr;
    jstring returnString = env->NewStringUTF(name->c_str());
    return returnString;
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_free(JNIEnv *env, jobject thiz) {
    auto profile = ptrToProfile(env, thiz);
    delete profile;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_dirty(JNIEnv *env, jobject thiz) {
    auto* configBase = ptrToConfigBase(env, thiz);
    return configBase->is_dirty();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_needsPush(JNIEnv *env, jobject thiz) {
    auto config = ptrToConfigBase(env, thiz);
    return config->needs_push();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_needsDump(JNIEnv *env, jobject thiz) {
    auto config = ptrToConfigBase(env, thiz);
    return config->needs_dump();
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_push(JNIEnv *env, jobject thiz) {
    auto config = ptrToConfigBase(env, thiz);
    auto pair = config->push();
    std::string to_push_str = pair.first;
    jbyteArray returnByteArray = bytes_from_string(env, to_push_str);
    jlong seqNo = pair.second;
    jclass returnObjectClass = env->FindClass("network/loki/messenger/libsession_util/util/ConfigWithSeqNo");
    jmethodID methodId = env->GetMethodID(returnObjectClass, "<init>", "([BJ)V");
    jobject returnObject = env->NewObject(returnObjectClass, methodId, returnByteArray, seqNo);
    return returnObject;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_getPic(JNIEnv *env, jobject thiz) {
    auto profile = ptrToProfile(env, thiz);
    auto pair = profile->get_profile_pic();
    // return nullptr if either parameter is null as per profile class
    if (pair.first == nullptr || pair.second == nullptr) return nullptr;
    const std::string* pic_url = pair.first;
    const std::string* pic_key = pair.second;
    jclass returnObjectClass = env->FindClass("network/loki/messenger/libsession_util/UserPic");
    jmethodID constructor = env->GetMethodID(returnObjectClass, "<init>", "(Ljava/lang/String;[B)V");
    jstring url = env->NewStringUTF(pic_url->c_str());
    jbyteArray byteArray = bytes_from_string(env, *pic_key);
    jobject returnObject = env->NewObject(returnObjectClass, constructor, url, byteArray);
    return returnObject;
}
extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_setPic(JNIEnv *env, jobject thiz,
                                                                jobject user_pic) {
    auto profile = ptrToProfile(env, thiz);
    jclass userPicClass = env->FindClass("network/loki/messenger/libsession_util/UserPic");
    jfieldID picField = env->GetFieldID(userPicClass, "url", "Ljava/lang/String;");
    jfieldID keyField = env->GetFieldID(userPicClass, "key", "[B");
    auto pic = (jstring)env->GetObjectField(user_pic, picField);
    auto key = (jbyteArray)env->GetObjectField(user_pic, keyField);

    const char* pic_chars = env->GetStringUTFChars(pic, nullptr);
    const std::string key_str = string_from_bytes(env, key);
    auto* pic_string = new std::string(pic_chars);

    profile->set_profile_pic(*pic_string, key_str);
}