#include "config_base.h"
#include "util.h"

extern "C" {
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_dirty(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto* configBase = ptrToConfigBase(env, thiz);
    return configBase->is_dirty();
}

JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_needsPush(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto config = ptrToConfigBase(env, thiz);
    return config->needs_push();
}

JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_needsDump(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto config = ptrToConfigBase(env, thiz);
    return config->needs_dump();
}

JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_push(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto config = ptrToConfigBase(env, thiz);
    auto push_tuple = config->push();
    auto to_push_str = std::get<1>(push_tuple);
    auto to_delete = std::get<2>(push_tuple);

    jbyteArray returnByteArray = util::bytes_from_ustring(env, to_push_str);
    jlong seqNo = std::get<0>(push_tuple);
    jclass returnObjectClass = env->FindClass("network/loki/messenger/libsession_util/util/ConfigPush");
    jclass stackClass = env->FindClass("java/util/Stack");
    jmethodID methodId = env->GetMethodID(returnObjectClass, "<init>", "([BJLjava/util/List;)V");
    jmethodID stack_init = env->GetMethodID(stackClass, "<init>", "()V");
    jobject our_stack = env->NewObject(stackClass, stack_init);
    jmethodID push_stack = env->GetMethodID(stackClass, "push", "(Ljava/lang/Object;)Ljava/lang/Object;");
    for (auto entry : to_delete) {
        auto entry_jstring = env->NewStringUTF(entry.data());
        env->CallObjectMethod(our_stack, push_stack, entry_jstring);
    }
    jobject returnObject = env->NewObject(returnObjectClass, methodId, returnByteArray, seqNo, our_stack);
    return returnObject;
}

JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_free(JNIEnv *env, jobject thiz) {
    auto config = ptrToConfigBase(env, thiz);
    delete config;
}

JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_dump(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto config = ptrToConfigBase(env, thiz);
    auto dumped = config->dump();
    jbyteArray bytes = util::bytes_from_ustring(env, dumped);
    return bytes;
}

JNIEXPORT jstring JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_encryptionDomain(JNIEnv *env,
                                                                         jobject thiz) {
    auto conf = ptrToConfigBase(env, thiz);
    return env->NewStringUTF(conf->encryption_domain());
}

JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_confirmPushed(JNIEnv *env, jobject thiz,
                                                                      jlong seq_no,
                                                                      jstring new_hash_jstring) {
    std::lock_guard lock{util::util_mutex_};
    auto conf = ptrToConfigBase(env, thiz);
    auto new_hash = env->GetStringUTFChars(new_hash_jstring, nullptr);
    conf->confirm_pushed(seq_no, new_hash);
    env->ReleaseStringUTFChars(new_hash_jstring, new_hash);
}

#pragma clang diagnostic push
#pragma ide diagnostic ignored "bugprone-reserved-identifier"
JNIEXPORT jint JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_merge___3Lkotlin_Pair_2(JNIEnv *env, jobject thiz,
                                                                     jobjectArray to_merge) {
    std::lock_guard lock{util::util_mutex_};
    auto conf = ptrToConfigBase(env, thiz);
    size_t number = env->GetArrayLength(to_merge);
    std::vector<std::pair<std::string,session::ustring>> configs = {};
    for (int i = 0; i < number; i++) {
        auto jElement = (jobject) env->GetObjectArrayElement(to_merge, i);
        auto pair = extractHashAndData(env, jElement);
        configs.push_back(pair);
    }
    return conf->merge(configs);
}

JNIEXPORT jint JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_merge__Lkotlin_Pair_2(JNIEnv *env, jobject thiz,
                                                                   jobject to_merge) {
    std::lock_guard lock{util::util_mutex_};
    auto conf = ptrToConfigBase(env, thiz);
    std::vector<std::pair<std::string, session::ustring>> configs = {extractHashAndData(env, to_merge)};
    return conf->merge(configs);
}

#pragma clang diagnostic pop
}
extern "C"
JNIEXPORT jint JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_configNamespace(JNIEnv *env, jobject thiz) {
    auto conf = ptrToConfigBase(env, thiz);
    return (std::int16_t) conf->storage_namespace();
}
extern "C"
JNIEXPORT jclass JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_00024Companion_kindFor(JNIEnv *env,
                                                                               jobject thiz,
                                                                               jint config_namespace) {
    auto user_class = env->FindClass("network/loki/messenger/libsession_util/UserProfile");
    auto contact_class = env->FindClass("network/loki/messenger/libsession_util/Contacts");
    auto convo_volatile_class = env->FindClass("network/loki/messenger/libsession_util/ConversationVolatileConfig");
    auto group_list_class = env->FindClass("network/loki/messenger/libsession_util/UserGroupsConfig");
    switch (config_namespace) {
        case (int)session::config::Namespace::UserProfile:
            return user_class;
        case (int)session::config::Namespace::Contacts:
            return contact_class;
        case (int)session::config::Namespace::ConvoInfoVolatile:
            return convo_volatile_class;
        case (int)session::config::Namespace::UserGroups:
            return group_list_class;
        default:
            return nullptr;
    }
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_currentHashes(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto conf = ptrToConfigBase(env, thiz);
    jclass stack = env->FindClass("java/util/Stack");
    jmethodID init = env->GetMethodID(stack, "<init>", "()V");
    jobject our_stack = env->NewObject(stack, init);
    jmethodID push = env->GetMethodID(stack, "push", "(Ljava/lang/Object;)Ljava/lang/Object;");
    auto vec = conf->current_hashes();
    for (std::string element: vec) {
        env->CallObjectMethod(our_stack, push, env->NewStringUTF(element.data()));
    }
    return our_stack;
}