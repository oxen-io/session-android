#include <jni.h>
#include "conversation.h"

#pragma clang diagnostic push

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_util_Conversation_00024OpenGroup_00024Companion_parseFullUrl(
        JNIEnv *env, jobject thiz, jstring full_url) {
    auto bytes = env->GetStringUTFChars(full_url, nullptr);
    auto [base, room, pk] = session::config::convo::open_group::parse_full_url(bytes);
    env->ReleaseStringUTFChars(full_url, bytes);

    jclass clazz = env->FindClass("kotlin/Triple");
    jmethodID constructor = env->GetMethodID(clazz, "<init>", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");

    auto base_j = env->NewStringUTF(base.data());
    auto room_j = env->NewStringUTF(room.data());
    auto pk_jbytes = util::bytes_from_ustring(env, pk);

    jobject triple = env->NewObject(clazz, constructor, base_j, room_j, pk_jbytes);
    return triple;
}

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

extern "C"
JNIEXPORT jint JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_size(JNIEnv *env,
                                                                             jobject thiz) {
    auto config = ptrToConvoInfo(env, thiz);
    return (jint)config->size();
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_empty(JNIEnv *env,
                                                                              jobject thiz) {
    auto config = ptrToConvoInfo(env, thiz);
    return config->empty();
}
extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_set(JNIEnv *env,
                                                                            jobject thiz,
                                                                            jobject to_store) {

    auto convos = ptrToConvoInfo(env, thiz);

    jclass one_to_one = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$OneToOne");
    jclass open_group = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$OpenGroup");
    jclass legacy_closed_group = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$LegacyClosedGroup");

    jclass to_store_class = env->GetObjectClass(to_store);
    if (to_store_class == one_to_one) {
        // store as 1to1
        convos->set(*deserialize_one_to_one(env, to_store));
    } else if (to_store_class == open_group) {
        // store as open_group
        convos->set(*deserialize_open_group(env, to_store));
    } else if (to_store_class == legacy_closed_group) {
        // store as legacy_closed_group
        convos->set(*deserialize_legacy_closed_group(env, to_store));
    }
}