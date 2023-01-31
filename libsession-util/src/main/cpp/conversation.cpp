#include <jni.h>
#include "conversation.h"

#pragma clang diagnostic push
extern "C"
#pragma ide diagnostic ignored "bugprone-reserved-identifier"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_00024Companion_newInstance___3B(
        JNIEnv *env, jobject thiz, jbyteArray ed25519_secret_key) {
    auto secret_key = util::ustring_from_bytes(env, ed25519_secret_key);
    auto* convo_info_volatile = new session::config::ConvoInfoVolatile(secret_key, std::nullopt);

    jclass convoClass = env->FindClass("network/loki/messenger/libsession_util/ConversationVolatileConfig");
    jmethodID constructor = env->GetMethodID(convoClass, "<init>", "(J)V");
    jobject newConfig = env->NewObject(convoClass, constructor, reinterpret_cast<jlong>(convo_info_volatile));

    return newConfig;
}
extern "C"
#pragma ide diagnostic ignored "bugprone-reserved-identifier"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_00024Companion_newInstance___3B_3B(
        JNIEnv *env, jobject thiz, jbyteArray ed25519_secret_key, jbyteArray initial_dump) {
    auto secret_key = util::ustring_from_bytes(env, ed25519_secret_key);
    auto initial = util::ustring_from_bytes(env, initial_dump);
    auto* convo_info_volatile = new session::config::ConvoInfoVolatile(secret_key, initial);

    jclass convoClass = env->FindClass("network/loki/messenger/libsession_util/ConversationVolatileConfig");
    jmethodID constructor = env->GetMethodID(convoClass, "<init>", "(J)V");
    jobject newConfig = env->NewObject(convoClass, constructor, reinterpret_cast<jlong>(convo_info_volatile));

    return newConfig;
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_set(JNIEnv *env,
                                                                            jobject thiz,
                                                                            jobject to_store) {
    auto conversations = ptrToConvoInfo(env, thiz);
    auto one_to_one = deserialize_one_to_one(env, to_store);
    conversations->set(*one_to_one);
}

extern "C"
JNIEXPORT jint JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_sizeOneToOnes(JNIEnv *env,
                                                                                      jobject thiz) {
    auto conversations = ptrToConvoInfo(env, thiz);
    return conversations->size_1to1();
}

#pragma clang diagnostic pop
extern "C"
JNIEXPORT jint JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_eraseAll(JNIEnv *env,
                                                                                 jobject thiz,
                                                                                 jobject predicate) {
    auto conversations = ptrToConvoInfo(env, thiz);

    jclass predicate_class = env->FindClass("kotlin/jvm/functions/Function1");
    jmethodID predicate_call = env->GetMethodID(predicate_class, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");

    jclass bool_class = env->FindClass("java/lang/Boolean");
    jfieldID bool_value = env->GetFieldID(bool_class, "value", "Z");

    int removed = 0;

    for (auto it = conversations->begin(); it != conversations->end(); ) {
        auto result = env->CallObjectMethod(predicate, predicate_call, serialize_any(env, *it));
        bool bool_result = env->GetBooleanField(result, bool_value);
        if (bool_result) {
            it = conversations->erase(it);
            removed++;
        } else {
            ++it;
        }
    }

    return removed;
}