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

    jobject serialize_user_pic(JNIEnv *env, session::config::profile_pic pic) {
        jclass returnObjectClass = env->FindClass("network/loki/messenger/libsession_util/util/UserPic");
        jmethodID constructor = env->GetMethodID(returnObjectClass, "<init>", "(Ljava/lang/String;[B)V");
        jstring url = env->NewStringUTF(pic.url.data());
        jbyteArray byteArray = util::bytes_from_ustring(env, pic.key);
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

    jobject serialize_base_community(JNIEnv *env, const session::config::community& community) {
        jclass base_community_clazz = env->FindClass("network/loki/messenger/libsession_util/util/BaseCommunityInfo");
        jmethodID base_community_constructor = env->GetMethodID(base_community_clazz, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
        auto base_url = env->NewStringUTF(community.base_url().data());
        auto room = env->NewStringUTF(community.room().data());
        auto pubkey_jstring = env->NewStringUTF(community.pubkey_hex().data());
        jobject ret = env->NewObject(base_community_clazz, base_community_constructor, base_url, room, pubkey_jstring);
        return ret;
    }

    session::config::community deserialize_base_community(JNIEnv *env, jobject base_community) {
        jclass base_community_clazz = env->FindClass("network/loki/messenger/libsession_util/util/BaseCommunityInfo");
        jfieldID base_url_field = env->GetFieldID(base_community_clazz, "baseUrl", "Ljava/lang/String;");
        jfieldID room_field = env->GetFieldID(base_community_clazz, "room", "Ljava/lang/String;");
        jfieldID pubkey_hex_field = env->GetFieldID(base_community_clazz, "pubKeyHex", "Ljava/lang/String;");
        auto base_url = (jstring)env->GetObjectField(base_community,base_url_field);
        auto room = (jstring)env->GetObjectField(base_community, room_field);
        auto pub_key_hex = (jstring)env->GetObjectField(base_community, pubkey_hex_field);
        auto base_url_chars = env->GetStringUTFChars(base_url, nullptr);
        auto room_chars = env->GetStringUTFChars(room, nullptr);
        auto pub_key_hex_chars = env->GetStringUTFChars(pub_key_hex, nullptr);

        auto community = session::config::community(base_url_chars, room_chars, pub_key_hex_chars);

        env->ReleaseStringUTFChars(base_url, base_url_chars);
        env->ReleaseStringUTFChars(room, room_chars);
        env->ReleaseStringUTFChars(pub_key_hex, pub_key_hex_chars);
        return community;
    }

}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_util_Sodium_ed25519KeyPair(JNIEnv *env, jobject thiz, jbyteArray seed) {
    std::array<unsigned char, 32> ed_pk; // NOLINT(cppcoreguidelines-pro-type-member-init)
    std::array<unsigned char, 64> ed_sk; // NOLINT(cppcoreguidelines-pro-type-member-init)
    auto seed_bytes = util::ustring_from_bytes(env, seed);
    crypto_sign_ed25519_seed_keypair(ed_pk.data(), ed_sk.data(), seed_bytes.data());

    jclass kp_class = env->FindClass("network/loki/messenger/libsession_util/util/KeyPair");
    jmethodID kp_constructor = env->GetMethodID(kp_class, "<init>", "([B[B)V");

    jbyteArray pk_jarray = util::bytes_from_ustring(env, session::ustring_view {ed_pk.data(), ed_pk.size()});
    jbyteArray sk_jarray = util::bytes_from_ustring(env, session::ustring_view {ed_sk.data(), ed_sk.size()});

    jobject return_obj = env->NewObject(kp_class, kp_constructor, pk_jarray, sk_jarray);
    return return_obj;
}
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_util_Sodium_ed25519PkToCurve25519(JNIEnv *env,
                                                                               jobject thiz,
                                                                               jbyteArray pk) {
    auto ed_pk = util::ustring_from_bytes(env, pk);
    std::array<unsigned char, 32> curve_pk; // NOLINT(cppcoreguidelines-pro-type-member-init)
    int success = crypto_sign_ed25519_pk_to_curve25519(curve_pk.data(), ed_pk.data());
    if (success != 0) {
        jclass exception = env->FindClass("java/lang/Exception");
        env->ThrowNew(exception, "Invalid crypto_sign_ed25519_pk_to_curve25519 operation");
        return nullptr;
    }
    jbyteArray curve_pk_jarray = util::bytes_from_ustring(env, session::ustring_view {curve_pk.data(), curve_pk.size()});
    return curve_pk_jarray;
}