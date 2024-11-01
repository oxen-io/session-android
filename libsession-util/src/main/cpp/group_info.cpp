#include <jni.h>
#include "group_info.h"
#include "session/config/groups/info.hpp"

extern "C"
JNIEXPORT jlong JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_00024Companion_newInstance(JNIEnv *env,
                                                                                        jobject thiz,
                                                                                        jbyteArray pub_key,
                                                                                        jbyteArray secret_key,
                                                                                        jbyteArray initial_dump) {
    std::lock_guard guard{util::util_mutex_};
    std::optional<session::ustring> secret_key_optional{std::nullopt};
    std::optional<session::ustring> initial_dump_optional{std::nullopt};
    auto pub_key_bytes = util::ustring_from_bytes(env, pub_key);
    if (secret_key && env->GetArrayLength(secret_key) > 0) {
        auto secret_key_bytes = util::ustring_from_bytes(env, secret_key);
        secret_key_optional = secret_key_bytes;
    }
    if (initial_dump && env->GetArrayLength(initial_dump) > 0) {
        auto initial_dump_bytes = util::ustring_from_bytes(env, initial_dump);
        initial_dump_optional = initial_dump_bytes;
    }

    auto* group_info = new session::config::groups::Info(pub_key_bytes, secret_key_optional, initial_dump_optional);
    return reinterpret_cast<jlong>(group_info);
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_destroyGroup(JNIEnv *env,
                                                                          jobject thiz) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    group_info->destroy_group();
}


extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_getCreated(JNIEnv *env, jobject thiz) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    return util::jlongFromOptional(env, group_info->get_created());
}
extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_getDeleteAttachmentsBefore(JNIEnv *env,
                                                                                        jobject thiz) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    return util::jlongFromOptional(env, group_info->get_delete_attach_before());
}
extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_getDeleteBefore(JNIEnv *env,
                                                                             jobject thiz) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    return util::jlongFromOptional(env, group_info->get_delete_before());
}

extern "C"
JNIEXPORT jlong JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_getExpiryTimer(JNIEnv *env,
                                                                            jobject thiz) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    auto timer = group_info->get_expiry_timer();
    if (!timer) {
        return 0;
    }
    long long in_seconds = timer->count();
    return in_seconds;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_getName(JNIEnv *env, jobject thiz) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    return util::jstringFromOptional(env, group_info->get_name());
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_getProfilePic(JNIEnv *env,
                                                                           jobject thiz) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    return util::serialize_user_pic(env, group_info->get_profile_pic());
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_isDestroyed(JNIEnv *env,
                                                                         jobject thiz) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    return group_info->is_destroyed();
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_setCreated(JNIEnv *env, jobject thiz,
                                                                        jlong created_at) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    group_info->set_created(created_at);
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_setDeleteAttachmentsBefore(JNIEnv *env,
                                                                                        jobject thiz,
                                                                                        jlong delete_before) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    group_info->set_delete_attach_before(delete_before);
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_setDeleteBefore(JNIEnv *env,
                                                                             jobject thiz,
                                                                             jlong delete_before) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    group_info->set_delete_before(delete_before);
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_setExpiryTimer(JNIEnv *env,
                                                                            jobject thiz,
                                                                            jlong  expire_seconds) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    group_info->set_expiry_timer(std::chrono::seconds{expire_seconds});
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_setName(JNIEnv *env, jobject thiz,
                                                                     jstring new_name) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    auto bytes = env->GetStringUTFChars(new_name, nullptr);
    group_info->set_name(bytes);
    env->ReleaseStringUTFChars(new_name, bytes);
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_setProfilePic(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jobject new_profile_pic) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    auto user_pic = util::deserialize_user_pic(env, new_profile_pic);
    auto url = env->GetStringUTFChars(user_pic.first, nullptr);
    auto key = util::ustring_from_bytes(env, user_pic.second);
    group_info->set_profile_pic(url, key);
    env->ReleaseStringUTFChars(user_pic.first, url);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_storageNamespace(JNIEnv *env,
                                                                              jobject thiz) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    return static_cast<jlong>(group_info->storage_namespace());
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_id(JNIEnv *env, jobject thiz) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    return util::serialize_account_id(env, group_info->id);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_getDescription(JNIEnv *env,
                                                                            jobject thiz) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    auto description = group_info->get_description();
    if (!description) {
        return nullptr;
    }
    auto jstring = env->NewStringUTF(description->data());
    return jstring;
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_GroupInfoConfig_setDescription(JNIEnv *env,
                                                                            jobject thiz,
                                                                            jstring new_description) {
    std::lock_guard guard{util::util_mutex_};
    auto group_info = ptrToInfo(env, thiz);
    auto description = env->GetStringUTFChars(new_description, nullptr);
    group_info->set_description(description);
    env->ReleaseStringUTFChars(new_description, description);
}