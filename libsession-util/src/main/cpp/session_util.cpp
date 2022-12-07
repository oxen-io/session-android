#include <jni.h>
#include <string>
#include <sodium/crypto_sign_ed25519.h>
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

jbyteArray bytes_from_ustring(JNIEnv* env, session::ustring_view from_str) {
    size_t length = from_str.length();
    jsize jlength = (jsize)length;
    jbyteArray new_array = env->NewByteArray(jlength);
    env->SetByteArrayRegion(new_array, 0, jlength, (jbyte*)from_str.data());
    return new_array;
}

session::ustring ustring_from_bytes(JNIEnv* env, jbyteArray byteArray) {
    size_t len = env->GetArrayLength(byteArray);
    jbyte* bytes = env->GetByteArrayElements(byteArray, nullptr);
    session::ustring newSt((u_char*)bytes, len);
    env->ReleaseByteArrayElements(byteArray, bytes, 0);
    return newSt;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_00024Companion_newInstance___3B_3B(
        JNIEnv *env, jobject thiz, jbyteArray ed25519_secret_key, jbyteArray initial_dump) {
    auto secret_key = ustring_from_bytes(env, ed25519_secret_key);
    auto initial = ustring_from_bytes(env, initial_dump);
    auto* profile = new session::config::UserProfile(secret_key, std::optional(initial));

    jclass userClass = env->FindClass("network/loki/messenger/libsession_util/UserProfile");
    jmethodID constructor = env->GetMethodID(userClass, "<init>", "(J)V");
    jobject newConfig = env->NewObject(userClass, constructor, reinterpret_cast<jlong>(profile));

    return newConfig;
}

extern "C" JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_00024Companion_newInstance___3B(
        JNIEnv* env,
        jobject,
        jbyteArray secretKey) {

    auto* profile = new session::config::UserProfile(ustring_from_bytes(env, secretKey), std::nullopt);

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
    if (name == std::nullopt) return nullptr;
    jstring returnString = env->NewStringUTF(name->data());
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
    session::ustring to_push_str = pair.first;
    jbyteArray returnByteArray = bytes_from_ustring(env, to_push_str);
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
    auto pic = profile->get_profile_pic();
    if (pic == std::nullopt) return nullptr;
    // return nullptr if either parameter is null as per profile class
    jclass returnObjectClass = env->FindClass("network/loki/messenger/libsession_util/util/UserPic");
    jmethodID constructor = env->GetMethodID(returnObjectClass, "<init>", "(Ljava/lang/String;[B)V");
    jstring url = env->NewStringUTF(pic->url.data());
    jbyteArray byteArray = bytes_from_ustring(env, pic->key);
    jobject returnObject = env->NewObject(returnObjectClass, constructor, url, byteArray);
    return returnObject;
}
extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_setPic(JNIEnv *env, jobject thiz,
                                                                jobject user_pic) {
    auto profile = ptrToProfile(env, thiz);
    jclass userPicClass = env->FindClass("network/loki/messenger/libsession_util/util/UserPic");
    jfieldID picField = env->GetFieldID(userPicClass, "url", "Ljava/lang/String;");
    jfieldID keyField = env->GetFieldID(userPicClass, "key", "[B");
    auto pic = (jstring)env->GetObjectField(user_pic, picField);
    auto key = (jbyteArray)env->GetObjectField(user_pic, keyField);

    const char* pic_chars = env->GetStringUTFChars(pic, nullptr);
    auto key_str = ustring_from_bytes(env, key);
    auto* pic_string = new std::string(pic_chars);

    profile->set_profile_pic(*pic_string, key_str);
}
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_dump(JNIEnv *env, jobject thiz) {
    auto config = ptrToConfigBase(env, thiz);
    auto dumped = config->dump();
    jbyteArray bytes = bytes_from_ustring(env, dumped);
    return bytes;
}
extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_util_Sodium_ed25519KeyPair(JNIEnv *env, jobject thiz, jbyteArray seed) {
    std::array<unsigned char, 32> ed_pk;
    std::array<unsigned char, 64> ed_sk;
    auto seed_bytes = ustring_from_bytes(env, seed);
    crypto_sign_ed25519_seed_keypair(ed_pk.data(), ed_sk.data(), seed_bytes.data());

    jclass kp_class = env->FindClass("network/loki/messenger/libsession_util/util/KeyPair");
    jmethodID kp_constructor = env->GetMethodID(kp_class, "<init>", "([B[B)V");

    jbyteArray pk_jarray = bytes_from_ustring(env, session::ustring_view {ed_pk.data(), ed_pk.size()});
    jbyteArray sk_jarray = bytes_from_ustring(env, session::ustring_view {ed_sk.data(), ed_sk.size()});

    jobject return_obj = env->NewObject(kp_class, kp_constructor, pk_jarray, sk_jarray);
    return return_obj;
}
extern "C"
JNIEXPORT jstring JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_encryptionDomain(JNIEnv *env,
                                                                         jobject thiz) {
    auto conf = ptrToConfigBase(env, thiz);
    return env->NewStringUTF(conf->encryption_domain());
}
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_decrypt(JNIEnv *env, jobject thiz,
                                                                jbyteArray encrypted) {
    auto profile = ptrToProfile(env, thiz);
    auto encrypted_bytes = ustring_from_bytes(env, encrypted);
}