#include "group_keys.h"
#include "group_info.h"
#include "group_members.h"

extern "C"
JNIEXPORT jint JNICALL
        Java_network_loki_messenger_libsession_1util_GroupKeysConfig_00024Companion_storageNamespace(JNIEnv* env,
                                                                                                     jobject thiz) {
    return (jint)session::config::Namespace::GroupKeys;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_00024Companion_newInstance(JNIEnv *env,
                                                                                        jobject thiz,
                                                                                        jbyteArray user_secret_key,
                                                                                        jbyteArray group_public_key,
                                                                                        jbyteArray group_secret_key,
                                                                                        jbyteArray initial_dump,
                                                                                        jobject info_jobject,
                                                                                        jobject members_jobject) {
    std::lock_guard lock{util::util_mutex_};
    auto user_key_bytes = util::ustring_from_bytes(env, user_secret_key);
    auto pub_key_bytes = util::ustring_from_bytes(env, group_public_key);
    std::optional<session::ustring> secret_key_optional{std::nullopt};
    std::optional<session::ustring> initial_dump_optional{std::nullopt};

    if (group_secret_key && env->GetArrayLength(group_secret_key) > 0) {
        auto secret_key_bytes = util::ustring_from_bytes(env, group_secret_key);
        secret_key_optional = secret_key_bytes;
    }

    if (env->GetArrayLength(initial_dump) > 0) {
        auto initial_dump_bytes = util::ustring_from_bytes(env, initial_dump);
        initial_dump_optional = initial_dump_bytes;
    }

    auto info = ptrToInfo(env, info_jobject);
    auto members = ptrToMembers(env, members_jobject);

    auto* keys = new session::config::groups::Keys(user_key_bytes,
                                                   pub_key_bytes,
                                                   secret_key_optional,
                                                   initial_dump_optional,
                                                   *info,
                                                   *members);

    jclass groupKeysConfig = env->FindClass("network/loki/messenger/libsession_util/GroupKeysConfig");
    jmethodID constructor = env->GetMethodID(groupKeysConfig, "<init>", "(J)V");
    jobject newConfig = env->NewObject(groupKeysConfig, constructor, reinterpret_cast<jlong>(keys));

    return newConfig;
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
                                                                     jobject info_jobject,
                                                                     jobject members_jobject) {
    std::lock_guard lock{util::util_mutex_};
    auto keys = ptrToKeys(env, thiz);
    auto message_bytes = util::ustring_from_bytes(env, message);
    auto hash_bytes = env->GetStringUTFChars(hash, nullptr);
    auto info = ptrToInfo(env, info_jobject);
    auto members = ptrToMembers(env, members_jobject);
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
                                                                   jobject info_jobject, jobject members_jobject) {
    std::lock_guard lock{util::util_mutex_};
    auto keys = ptrToKeys(env, thiz);
    auto info = ptrToInfo(env, info_jobject);
    auto members = ptrToMembers(env, members_jobject);
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
    std::lock_guard lock{util::util_mutex_};
    auto ptr = ptrToKeys(env, thiz);
    auto plaintext_ustring = util::ustring_from_bytes(env, plaintext);
    auto enc = ptr->encrypt_message(plaintext_ustring);
    return util::bytes_from_ustring(env, enc);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_decrypt(JNIEnv *env, jobject thiz,
                                                                     jbyteArray ciphertext) {
    std::lock_guard lock{util::util_mutex_};
    auto ptr = ptrToKeys(env, thiz);
    auto ciphertext_ustring = util::ustring_from_bytes(env, ciphertext);
    try {
        auto decrypted = ptr->decrypt_message(ciphertext_ustring);
        auto sender = decrypted.first;
        auto plaintext = decrypted.second;
        auto plaintext_bytes = util::bytes_from_ustring(env, plaintext);
        auto sender_session_id = util::serialize_account_id(env, sender.data());
        auto pair_class = env->FindClass("kotlin/Pair");
        auto pair_constructor = env->GetMethodID(pair_class, "<init>", "(Ljava/lang/Object;Ljava/lang/Object;)V");
        auto pair_obj = env->NewObject(pair_class, pair_constructor, plaintext_bytes, sender_session_id);
        return pair_obj;
    } catch (std::exception& e) {
        // TODO: maybe log here
    }

    return nullptr;
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
                                                                           jstring user_session_id) {
    std::lock_guard lock{util::util_mutex_};
    auto ptr = ptrToKeys(env, thiz);
    auto string = env->GetStringUTFChars(user_session_id, nullptr);
    auto supplement = ptr->key_supplement(string);
    auto supplement_jbytearray = util::bytes_from_ustring(env, supplement);
    env->ReleaseStringUTFChars(user_session_id, string);
    return supplement_jbytearray;
}
extern "C"
JNIEXPORT jint JNICALL
Java_network_loki_messenger_libsession_1util_GroupKeysConfig_currentGeneration(JNIEnv *env,
                                                                               jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto ptr = ptrToKeys(env, thiz);
    return ptr->current_generation();
}