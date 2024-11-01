#include "group_keys.h"
#include "group_info.h"
#include "group_members.h"

#include "jni_utils.h"

extern "C"
JNIEXPORT jint JNICALL
        Java_network_loki_messenger_libsession_1util_GroupKeysConfig_00024Companion_storageNamespace(JNIEnv* env,
                                                                                                     jobject thiz) {
    return (jint)session::config::Namespace::GroupKeys;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_00024Companion_newInstance(JNIEnv *env,
                                                                                        jobject thiz,
                                                                                        jbyteArray user_secret_key,
                                                                                        jbyteArray group_public_key,
                                                                                        jbyteArray group_secret_key,
                                                                                        jbyteArray initial_dump,
                                                                                        jlong info_pointer,
                                                                                        jlong members_pointer) {
    std::lock_guard lock{util::util_mutex_};
    auto user_key_bytes = util::ustring_from_bytes(env, user_secret_key);
    auto pub_key_bytes = util::ustring_from_bytes(env, group_public_key);
    std::optional<session::ustring> secret_key_optional{std::nullopt};
    std::optional<session::ustring> initial_dump_optional{std::nullopt};

    if (group_secret_key && env->GetArrayLength(group_secret_key) > 0) {
        auto secret_key_bytes = util::ustring_from_bytes(env, group_secret_key);
        secret_key_optional = secret_key_bytes;
    }

    if (initial_dump && env->GetArrayLength(initial_dump) > 0) {
        auto initial_dump_bytes = util::ustring_from_bytes(env, initial_dump);
        initial_dump_optional = initial_dump_bytes;
    }

    auto info = reinterpret_cast<session::config::groups::Info*>(info_pointer);
    auto members = reinterpret_cast<session::config::groups::Members*>(members_pointer);

    auto* keys = new session::config::groups::Keys(user_key_bytes,
                                                   pub_key_bytes,
                                                   secret_key_optional,
                                                   initial_dump_optional,
                                                   *info,
                                                   *members);

    return reinterpret_cast<jlong>(keys);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_groupKeys(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto config = ptrToKeys(env, thiz);
    auto keys = config->group_keys();
    jclass stack = env->FindClass("java/util/Stack");
    jmethodID init = env->GetMethodID(stack, "<init>", "()V");
    jobject our_stack = env->NewObject(stack, init);
    jmethodID push = env->GetMethodID(stack, "push", "(Ljava/lang/Object;)Ljava/lang/Object;");
    for (auto& key : keys) {
        auto key_bytes = util::bytes_from_ustring(env, key.data());
        env->CallObjectMethod(our_stack, push, key_bytes);
    }
    return our_stack;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_loadKey(JNIEnv *env, jobject thiz,
                                                                     jbyteArray message,
                                                                     jstring hash,
                                                                     jlong timestamp_ms,
                                                                     jlong info_ptr,
                                                                     jlong members_ptr) {
    std::lock_guard lock{util::util_mutex_};
    auto keys = ptrToKeys(env, thiz);
    auto message_bytes = util::ustring_from_bytes(env, message);
    auto hash_bytes = env->GetStringUTFChars(hash, nullptr);
    auto info = reinterpret_cast<session::config::groups::Info*>(info_ptr);
    auto members = reinterpret_cast<session::config::groups::Members*>(members_ptr);
    bool processed = keys->load_key_message(hash_bytes, message_bytes, timestamp_ms, *info, *members);

    env->ReleaseStringUTFChars(hash, hash_bytes);
    return processed;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_needsRekey(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto keys = ptrToKeys(env, thiz);
    return keys->needs_rekey();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_needsDump(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto keys = ptrToKeys(env, thiz);
    return keys->needs_dump();
}



extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_pendingKey(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto keys = ptrToKeys(env, thiz);
    auto pending = keys->pending_key();
    if (!pending) {
        return nullptr;
    }
    auto pending_bytes = util::bytes_from_ustring(env, *pending);
    return pending_bytes;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_pendingConfig(JNIEnv *env,
                                                                           jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto keys = ptrToKeys(env, thiz);
    auto pending = keys->pending_config();
    if (!pending) {
        return nullptr;
    }
    auto pending_bytes = util::bytes_from_ustring(env, *pending);
    return pending_bytes;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_rekey(JNIEnv *env, jobject thiz,
                                                                   jlong info_ptr, jlong members_ptr) {
    std::lock_guard lock{util::util_mutex_};
    auto keys = ptrToKeys(env, thiz);
    auto info = reinterpret_cast<session::config::groups::Info*>(info_ptr);
    auto members = reinterpret_cast<session::config::groups::Members*>(members_ptr);
    auto rekey = keys->rekey(*info, *members);
    auto rekey_bytes = util::bytes_from_ustring(env, rekey.data());
    return rekey_bytes;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_dump(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto keys = ptrToKeys(env, thiz);
    auto dump = keys->dump();
    auto byte_array = util::bytes_from_ustring(env, dump);
    return byte_array;
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_free(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto ptr = ptrToKeys(env, thiz);
    delete ptr;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_encrypt(JNIEnv *env, jobject thiz,
                                                                     jbyteArray plaintext) {
    return jni_utils::run_catching_cxx_exception_or_throws<jbyteArray>(env, [=] {
        std::lock_guard lock{util::util_mutex_};
        auto ptr = ptrToKeys(env, thiz);
        auto plaintext_ustring = util::ustring_from_bytes(env, plaintext);
        auto enc = ptr->encrypt_message(plaintext_ustring);
        return util::bytes_from_ustring(env, enc);
    });
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_decrypt(JNIEnv *env, jobject thiz,
                                                                     jbyteArray ciphertext) {
    return jni_utils::run_catching_cxx_exception_or_throws<jobject>(env, [=] {
        std::lock_guard lock{util::util_mutex_};
        auto ptr = ptrToKeys(env, thiz);
        auto ciphertext_ustring = util::ustring_from_bytes(env, ciphertext);
        auto decrypted = ptr->decrypt_message(ciphertext_ustring);
        auto sender = decrypted.first;
        auto plaintext = decrypted.second;
        auto plaintext_bytes = util::bytes_from_ustring(env, plaintext);
        auto sender_session_id = util::serialize_account_id(env, sender.data());
        auto pair_class = env->FindClass("kotlin/Pair");
        auto pair_constructor = env->GetMethodID(pair_class, "<init>", "(Ljava/lang/Object;Ljava/lang/Object;)V");
        auto pair_obj = env->NewObject(pair_class, pair_constructor, plaintext_bytes, sender_session_id);
        return pair_obj;
    });
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_keys(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto ptr = ptrToKeys(env, thiz);
    auto keys = ptr->group_keys();
    jclass stack = env->FindClass("java/util/Stack");
    jmethodID init = env->GetMethodID(stack, "<init>", "()V");
    jobject our_stack = env->NewObject(stack, init);
    jmethodID push = env->GetMethodID(stack, "push", "(Ljava/lang/Object;)Ljava/lang/Object;");
    for (auto& key : keys) {
        auto key_bytes = util::bytes_from_ustring(env, key);
        env->CallObjectMethod(our_stack, push, key_bytes);
    }
    return our_stack;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_currentHashes(JNIEnv *env,
                                                                           jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto ptr = ptrToKeys(env, thiz);
    auto existing = ptr->current_hashes();
    jclass stack = env->FindClass("java/util/Stack");
    jmethodID init = env->GetMethodID(stack, "<init>", "()V");
    jobject our_list = env->NewObject(stack, init);
    jmethodID push = env->GetMethodID(stack, "push", "(Ljava/lang/Object;)Ljava/lang/Object;");
    for (auto& hash : existing) {
        auto hash_bytes = env->NewStringUTF(hash.data());
        env->CallObjectMethod(our_list, push, hash_bytes);
    }
    return our_list;
}
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_makeSubAccount(JNIEnv *env,
                                                                            jobject thiz,
                                                                            jobject session_id,
                                                                            jboolean can_write,
                                                                            jboolean can_delete) {
    std::lock_guard lock{util::util_mutex_};
    auto ptr = ptrToKeys(env, thiz);
    auto deserialized_id = util::deserialize_account_id(env, session_id);
    auto new_subaccount_key = ptr->swarm_make_subaccount(deserialized_id.data(), can_write, can_delete);
    auto jbytes = util::bytes_from_ustring(env, new_subaccount_key);
    return jbytes;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_getSubAccountToken(JNIEnv *env,
                                                                                jobject thiz,
                                                                                jobject session_id,
                                                                                jboolean can_write,
                                                                                jboolean can_delete) {
    std::lock_guard lock{util::util_mutex_};
    auto ptr = ptrToKeys(env, thiz);
    auto deserialized_id = util::deserialize_account_id(env, session_id);
    auto token = ptr->swarm_subaccount_token(deserialized_id, can_write, can_delete);
    auto jbytes = util::bytes_from_ustring(env, token);
    return jbytes;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_subAccountSign(JNIEnv *env,
                                                                            jobject thiz,
                                                                            jbyteArray message,
                                                                            jbyteArray signing_value) {
    std::lock_guard lock{util::util_mutex_};
    auto ptr = ptrToKeys(env, thiz);
    auto message_ustring = util::ustring_from_bytes(env, message);
    auto signing_value_ustring = util::ustring_from_bytes(env, signing_value);
    auto swarm_auth = ptr->swarm_subaccount_sign(message_ustring, signing_value_ustring, false);
    return util::deserialize_swarm_auth(env, swarm_auth);
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_supplementFor(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jobjectArray j_user_session_ids) {
    std::lock_guard lock{util::util_mutex_};
    auto ptr = ptrToKeys(env, thiz);
    std::vector<std::string> user_session_ids;
    for (int i = 0, size = env->GetArrayLength(j_user_session_ids); i < size; i++) {
        user_session_ids.push_back(util::string_from_jstring(env, (jstring)(env->GetObjectArrayElement(j_user_session_ids, i))));
    }
    auto supplement = ptr->key_supplement(user_session_ids);
    return util::bytes_from_ustring(env, supplement);
}
extern "C"
JNIEXPORT jint JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_currentGeneration(JNIEnv *env,
                                                                               jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto ptr = ptrToKeys(env, thiz);
    return ptr->current_generation();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_admin(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto ptr = ptrToKeys(env, thiz);
    return ptr->admin();
}