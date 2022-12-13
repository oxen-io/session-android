#include "util.h"
#include <string>
#include <sodium/crypto_sign.h>

namespace util {
    jbyteArray bytes_from_ustring(JNIEnv* env, session::ustring_view from_str) {
        size_t length = from_str.length();
        auto jlength = (jsize)length;
        jbyteArray new_array = env->NewByteArray(jlength);
        env->SetByteArrayRegion(new_array, 0, jlength, (jbyte*)from_str.data());
        return new_array;
    }

    session::ustring ustring_from_bytes(JNIEnv* env, jbyteArray byteArray) {
        size_t len = env->GetArrayLength(byteArray);
        auto bytes = env->GetByteArrayElements(byteArray, nullptr);

        session::ustring st{reinterpret_cast<const unsigned char *>(bytes), len};
        env->ReleaseByteArrayElements(byteArray, bytes, 0);
        return st;
    }

    jobject serialize_user_pic(JNIEnv *env, std::optional<session::config::profile_pic> pic) {
        if (!pic) return nullptr;

        jclass returnObjectClass = env->FindClass("network/loki/messenger/libsession_util/util/UserPic");
        jmethodID constructor = env->GetMethodID(returnObjectClass, "<init>", "(Ljava/lang/String;[B)V");
        jstring url = env->NewStringUTF(pic->url.data());
        jbyteArray byteArray = util::bytes_from_ustring(env, pic->key);
        return env->NewObject(returnObjectClass, constructor, url, byteArray);
    }

    std::pair<jstring, jbyteArray> deserialize_user_pic(JNIEnv *env, jobject user_pic) {
        jclass userPicClass = env->FindClass("network/loki/messenger/libsession_util/util/UserPic");
        jfieldID picField = env->GetFieldID(userPicClass, "url", "Ljava/lang/String;");
        jfieldID keyField = env->GetFieldID(userPicClass, "key", "[B");
        auto pic = (jstring)env->GetObjectField(user_pic, picField);
        auto key = (jbyteArray)env->GetObjectField(user_pic, keyField);
        return {pic, key};
    }

}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_util_Sodium_ed25519KeyPair(JNIEnv *env, jobject thiz, jbyteArray seed) {
    std::array<unsigned char, 32> ed_pk;
    std::array<unsigned char, 64> ed_sk;
    auto seed_bytes = util::ustring_from_bytes(env, seed);
    crypto_sign_ed25519_seed_keypair(ed_pk.data(), ed_sk.data(), seed_bytes.data());

    jclass kp_class = env->FindClass("network/loki/messenger/libsession_util/util/KeyPair");
    jmethodID kp_constructor = env->GetMethodID(kp_class, "<init>", "([B[B)V");

    jbyteArray pk_jarray = util::bytes_from_ustring(env, session::ustring_view {ed_pk.data(), ed_pk.size()});
    jbyteArray sk_jarray = util::bytes_from_ustring(env, session::ustring_view {ed_sk.data(), ed_sk.size()});

    jobject return_obj = env->NewObject(kp_class, kp_constructor, pk_jarray, sk_jarray);
    return return_obj;
}