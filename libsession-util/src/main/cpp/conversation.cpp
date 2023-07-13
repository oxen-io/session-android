#include <jni.h>
#include "conversation.h"

#pragma clang diagnostic push

extern "C"
#pragma ide diagnostic ignored "bugprone-reserved-identifier"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_00024Companion_newInstance___3B(
        JNIEnv *env, jobject thiz, jbyteArray ed25519_secret_key) {
    std::lock_guard lock{util::util_mutex_};
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
    std::lock_guard lock{util::util_mutex_};
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
    std::lock_guard lock{util::util_mutex_};
    auto conversations = ptrToConvoInfo(env, thiz);
    return conversations->size_1to1();
}

#pragma clang diagnostic pop
extern "C"
JNIEXPORT jint JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_eraseAll(JNIEnv *env,
                                                                                 jobject thiz,
                                                                                 jobject predicate) {
    std::lock_guard lock{util::util_mutex_};
    auto conversations = ptrToConvoInfo(env, thiz);

    jclass predicate_class = env->FindClass("kotlin/jvm/functions/Function1");
    jmethodID predicate_call = env->GetMethodID(predicate_class, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");

    jclass bool_class = env->FindClass("java/lang/Boolean");
    jmethodID bool_get = env->GetMethodID(bool_class, "booleanValue", "()Z");

    int removed = 0;
    auto to_erase = std::vector<session::config::convo::any>();

    for (auto it = conversations->begin(); it != conversations->end(); ++it) {
        auto result = env->CallObjectMethod(predicate, predicate_call, serialize_any(env, *it));
        bool bool_result = env->CallBooleanMethod(result, bool_get);
        if (bool_result) {
            to_erase.push_back(*it);
        }
    }

    for (auto & entry : to_erase) {
        if (conversations->erase(entry)) {
            removed++;
        }
    }

    return removed;
}

extern "C"
JNIEXPORT jint JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_size(JNIEnv *env,
                                                                             jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto config = ptrToConvoInfo(env, thiz);
    return (jint)config->size();
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_empty(JNIEnv *env,
                                                                              jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto config = ptrToConvoInfo(env, thiz);
    return config->empty();
}
extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_set(JNIEnv *env,
                                                                            jobject thiz,
                                                                            jobject to_store) {
    std::lock_guard lock{util::util_mutex_};

    auto convos = ptrToConvoInfo(env, thiz);

    jclass one_to_one = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$OneToOne");
    jclass open_group = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$Community");
    jclass legacy_closed_group = env->FindClass("network/loki/messenger/libsession_util/util/Conversation$LegacyGroup");

    jclass to_store_class = env->GetObjectClass(to_store);
    if (env->IsSameObject(to_store_class, one_to_one)) {
        // store as 1to1
        convos->set(deserialize_one_to_one(env, to_store, convos));
    } else if (env->IsSameObject(to_store_class,open_group)) {
        // store as open_group
        convos->set(deserialize_community(env, to_store, convos));
    } else if (env->IsSameObject(to_store_class,legacy_closed_group)) {
        // store as legacy_closed_group
        convos->set(deserialize_legacy_closed_group(env, to_store, convos));
    }
}
extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_getOneToOne(JNIEnv *env,
                                                                                    jobject thiz,
                                                                                    jstring pub_key_hex) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    auto param = env->GetStringUTFChars(pub_key_hex, nullptr);
    auto internal = convos->get_1to1(param);
    env->ReleaseStringUTFChars(pub_key_hex, param);
    if (internal) {
        return serialize_one_to_one(env, *internal);
    }
    return nullptr;
}
extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_getOrConstructOneToOne(
        JNIEnv *env, jobject thiz, jstring pub_key_hex) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    auto param = env->GetStringUTFChars(pub_key_hex, nullptr);
    auto internal = convos->get_or_construct_1to1(param);
    env->ReleaseStringUTFChars(pub_key_hex, param);
    return serialize_one_to_one(env, internal);
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_eraseOneToOne(JNIEnv *env,
                                                                                      jobject thiz,
                                                                                      jstring pub_key_hex) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    auto param = env->GetStringUTFChars(pub_key_hex, nullptr);
    auto result = convos->erase_1to1(param);
    env->ReleaseStringUTFChars(pub_key_hex, param);
    return result;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_getCommunity__Ljava_lang_String_2Ljava_lang_String_2(
        JNIEnv *env, jobject thiz, jstring base_url, jstring room) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    auto base_url_chars = env->GetStringUTFChars(base_url, nullptr);
    auto room_chars = env->GetStringUTFChars(room, nullptr);
    auto open = convos->get_community(base_url_chars, room_chars);
    if (open) {
        auto serialized = serialize_open_group(env, *open);
        return serialized;
    }
    return nullptr;
}
extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_getOrConstructCommunity__Ljava_lang_String_2Ljava_lang_String_2_3B(
        JNIEnv *env, jobject thiz, jstring base_url, jstring room, jbyteArray pub_key) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    auto base_url_chars = env->GetStringUTFChars(base_url, nullptr);
    auto room_chars = env->GetStringUTFChars(room, nullptr);
    auto pub_key_ustring = util::ustring_from_bytes(env, pub_key);
    auto open = convos->get_or_construct_community(base_url_chars, room_chars, pub_key_ustring);
    auto serialized = serialize_open_group(env, open);
    return serialized;
}
extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_getOrConstructCommunity__Ljava_lang_String_2Ljava_lang_String_2Ljava_lang_String_2(
        JNIEnv *env, jobject thiz, jstring base_url, jstring room, jstring pub_key_hex) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    auto base_url_chars = env->GetStringUTFChars(base_url, nullptr);
    auto room_chars = env->GetStringUTFChars(room, nullptr);
    auto hex_chars = env->GetStringUTFChars(pub_key_hex, nullptr);
    auto open = convos->get_or_construct_community(base_url_chars, room_chars, hex_chars);
    env->ReleaseStringUTFChars(base_url, base_url_chars);
    env->ReleaseStringUTFChars(room, room_chars);
    env->ReleaseStringUTFChars(pub_key_hex, hex_chars);
    auto serialized = serialize_open_group(env, open);
    return serialized;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_eraseCommunity__Lnetwork_loki_messenger_libsession_1util_util_Conversation_Community_2(JNIEnv *env,
                                                                                       jobject thiz,
                                                                                       jobject open_group) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    auto deserialized = deserialize_community(env, open_group, convos);
    return convos->erase(deserialized);
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_eraseCommunity__Ljava_lang_String_2Ljava_lang_String_2(
        JNIEnv *env, jobject thiz, jstring base_url, jstring room) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    auto base_url_chars = env->GetStringUTFChars(base_url, nullptr);
    auto room_chars = env->GetStringUTFChars(room, nullptr);
    auto result = convos->erase_community(base_url_chars, room_chars);
    env->ReleaseStringUTFChars(base_url, base_url_chars);
    env->ReleaseStringUTFChars(room, room_chars);
    return result;
}
extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_getLegacyClosedGroup(
        JNIEnv *env, jobject thiz, jstring group_id) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    auto id_chars = env->GetStringUTFChars(group_id, nullptr);
    auto lgc = convos->get_legacy_group(id_chars);
    env->ReleaseStringUTFChars(group_id, id_chars);
    if (lgc) {
        auto serialized = serialize_legacy_group(env, *lgc);
        return serialized;
    }
    return nullptr;
}
extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_getOrConstructLegacyGroup(
        JNIEnv *env, jobject thiz, jstring group_id) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    auto id_chars = env->GetStringUTFChars(group_id, nullptr);
    auto lgc = convos->get_or_construct_legacy_group(id_chars);
    env->ReleaseStringUTFChars(group_id, id_chars);
    return serialize_legacy_group(env, lgc);
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_eraseLegacyClosedGroup(
        JNIEnv *env, jobject thiz, jstring group_id) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    auto id_chars = env->GetStringUTFChars(group_id, nullptr);
    auto result = convos->erase_legacy_group(id_chars);
    env->ReleaseStringUTFChars(group_id, id_chars);
    return result;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_erase(JNIEnv *env,
                                                                              jobject thiz,
                                                                              jobject conversation) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    auto deserialized = deserialize_any(env, conversation, convos);
    if (!deserialized.has_value()) return false;
    return convos->erase(*deserialized);
}
extern "C"
JNIEXPORT jint JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_sizeCommunities(JNIEnv *env,
                                                                                       jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    return convos->size_communities();
}
extern "C"
JNIEXPORT jint JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_sizeLegacyClosedGroups(
        JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    return convos->size_legacy_groups();
}
extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_all(JNIEnv *env,
                                                                            jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    jclass stack = env->FindClass("java/util/Stack");
    jmethodID init = env->GetMethodID(stack, "<init>", "()V");
    jobject our_stack = env->NewObject(stack, init);
    jmethodID push = env->GetMethodID(stack, "push", "(Ljava/lang/Object;)Ljava/lang/Object;");
    for (const auto& convo : *convos) {
        auto contact_obj = serialize_any(env, convo);
        env->CallObjectMethod(our_stack, push, contact_obj);
    }
    return our_stack;
}
extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_allOneToOnes(JNIEnv *env,
                                                                                     jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    jclass stack = env->FindClass("java/util/Stack");
    jmethodID init = env->GetMethodID(stack, "<init>", "()V");
    jobject our_stack = env->NewObject(stack, init);
    jmethodID push = env->GetMethodID(stack, "push", "(Ljava/lang/Object;)Ljava/lang/Object;");
    for (auto contact = convos->begin_1to1(); contact != convos->end(); ++contact)
        env->CallObjectMethod(our_stack, push, serialize_one_to_one(env, *contact));
    return our_stack;
}
extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_allCommunities(JNIEnv *env,
                                                                                      jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    jclass stack = env->FindClass("java/util/Stack");
    jmethodID init = env->GetMethodID(stack, "<init>", "()V");
    jobject our_stack = env->NewObject(stack, init);
    jmethodID push = env->GetMethodID(stack, "push", "(Ljava/lang/Object;)Ljava/lang/Object;");
    for (auto contact = convos->begin_communities(); contact != convos->end(); ++contact)
        env->CallObjectMethod(our_stack, push, serialize_open_group(env, *contact));
    return our_stack;
}
extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConversationVolatileConfig_allLegacyClosedGroups(
        JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto convos = ptrToConvoInfo(env, thiz);
    jclass stack = env->FindClass("java/util/Stack");
    jmethodID init = env->GetMethodID(stack, "<init>", "()V");
    jobject our_stack = env->NewObject(stack, init);
    jmethodID push = env->GetMethodID(stack, "push", "(Ljava/lang/Object;)Ljava/lang/Object;");
    for (auto contact = convos->begin_legacy_groups(); contact != convos->end(); ++contact)
        env->CallObjectMethod(our_stack, push, serialize_legacy_group(env, *contact));
    return our_stack;
}