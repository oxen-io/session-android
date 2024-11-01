#include "user_profile.h"
#include "util.h"

extern "C" {
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_setName(
        JNIEnv* env,
        jobject thiz,
        jstring newName) {
    std::lock_guard lock{util::util_mutex_};
    auto profile = ptrToProfile(env, thiz);
    auto name_chars = env->GetStringUTFChars(newName, nullptr);
    profile->set_name(name_chars);
    env->ReleaseStringUTFChars(newName, name_chars);
}

JNIEXPORT jstring JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_getName(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto profile = ptrToProfile(env, thiz);
    auto name = profile->get_name();
    if (name == std::nullopt) return nullptr;
    jstring returnString = env->NewStringUTF(name->data());
    return returnString;
}

JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_getPic(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto profile = ptrToProfile(env, thiz);
    auto pic = profile->get_profile_pic();

    jobject returnObject = util::serialize_user_pic(env, pic);

    return returnObject;
}

JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_setPic(JNIEnv *env, jobject thiz,
                                                                jobject user_pic) {
    std::lock_guard lock{util::util_mutex_};
    auto profile = ptrToProfile(env, thiz);
    auto pic = util::deserialize_user_pic(env, user_pic);
    auto url = env->GetStringUTFChars(pic.first, nullptr);
    auto key = util::ustring_from_bytes(env, pic.second);
    profile->set_profile_pic(url, key);
    env->ReleaseStringUTFChars(pic.first, url);
}

}
extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_setNtsPriority(JNIEnv *env, jobject thiz,
                                                                        jlong priority) {
    std::lock_guard lock{util::util_mutex_};
    auto profile = ptrToProfile(env, thiz);
    profile->set_nts_priority(priority);
}
extern "C"
JNIEXPORT jlong JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_getNtsPriority(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto profile = ptrToProfile(env, thiz);
    return profile->get_nts_priority();
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_setNtsExpiry(JNIEnv *env, jobject thiz,
                                                                      jobject expiry_mode) {
    std::lock_guard lock{util::util_mutex_};
    auto profile = ptrToProfile(env, thiz);
    auto expiry = util::deserialize_expiry(env, expiry_mode);
    profile->set_nts_expiry(std::chrono::seconds (expiry.second));
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_getNtsExpiry(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto profile = ptrToProfile(env, thiz);
    auto nts_expiry = profile->get_nts_expiry();
    if (nts_expiry == std::nullopt) {
        auto expiry = util::serialize_expiry(env, session::config::expiration_mode::none, std::chrono::seconds(0));
        return expiry;
    }
    auto expiry = util::serialize_expiry(env, session::config::expiration_mode::after_send, std::chrono::seconds(*nts_expiry));
    return expiry;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_getCommunityMessageRequests(
        JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto profile = ptrToProfile(env, thiz);
    auto blinded_msg_requests = profile->get_blinded_msgreqs();
    if (blinded_msg_requests.has_value()) {
        return *blinded_msg_requests;
    }
    return true;
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_setCommunityMessageRequests(
        JNIEnv *env, jobject thiz, jboolean blocks) {
    std::lock_guard lock{util::util_mutex_};
    auto profile = ptrToProfile(env, thiz);
    profile->set_blinded_msgreqs(std::optional{(bool)blocks});
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_UserProfile_isBlockCommunityMessageRequestsSet(
        JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto profile = ptrToProfile(env, thiz);
    return profile->get_blinded_msgreqs().has_value();
}
