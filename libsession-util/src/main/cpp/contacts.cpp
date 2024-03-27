#include "contacts.h"
#include "util.h"

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_Contacts_get(JNIEnv *env, jobject thiz,
                                                          jstring session_id) {
    std::lock_guard lock{util::util_mutex_};
    auto contacts = ptrToContacts(env, thiz);
    auto session_id_chars = env->GetStringUTFChars(session_id, nullptr);
    auto contact = contacts->get(session_id_chars);
    env->ReleaseStringUTFChars(session_id, session_id_chars);
    if (!contact) return nullptr;
    jobject j_contact = serialize_contact(env, contact.value());
    return j_contact;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_Contacts_getOrConstruct(JNIEnv *env, jobject thiz,
                                                                     jstring session_id) {
    std::lock_guard lock{util::util_mutex_};
    auto contacts = ptrToContacts(env, thiz);
    auto session_id_chars = env->GetStringUTFChars(session_id, nullptr);
    auto contact = contacts->get_or_construct(session_id_chars);
    env->ReleaseStringUTFChars(session_id, session_id_chars);
    return serialize_contact(env, contact);
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_Contacts_set(JNIEnv *env, jobject thiz,
                                                          jobject contact) {
    std::lock_guard lock{util::util_mutex_};
    auto contacts = ptrToContacts(env, thiz);
    auto contact_info = deserialize_contact(env, contact, contacts);
    contacts->set(contact_info);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_Contacts_erase(JNIEnv *env, jobject thiz,
                                                            jstring session_id) {
    std::lock_guard lock{util::util_mutex_};
    auto contacts = ptrToContacts(env, thiz);
    auto session_id_chars = env->GetStringUTFChars(session_id, nullptr);

    bool result = contacts->erase(session_id_chars);
    env->ReleaseStringUTFChars(session_id, session_id_chars);
    return result;
}
extern "C"
#pragma clang diagnostic push
#pragma ide diagnostic ignored "bugprone-reserved-identifier"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_Contacts_00024Companion_newInstance___3B(JNIEnv *env,
                                                                                 jobject thiz,
                                                                                 jbyteArray ed25519_secret_key) {
    std::lock_guard lock{util::util_mutex_};
    auto secret_key = util::ustring_from_bytes(env, ed25519_secret_key);
    auto* contacts = new session::config::Contacts(secret_key, std::nullopt);

    jclass contactsClass = env->FindClass("network/loki/messenger/libsession_util/Contacts");
    jmethodID constructor = env->GetMethodID(contactsClass, "<init>", "(J)V");
    jobject newConfig = env->NewObject(contactsClass, constructor, reinterpret_cast<jlong>(contacts));

    return newConfig;
}
extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_Contacts_00024Companion_newInstance___3B_3B(
        JNIEnv *env, jobject thiz, jbyteArray ed25519_secret_key, jbyteArray initial_dump) {
    std::lock_guard lock{util::util_mutex_};
    auto secret_key = util::ustring_from_bytes(env, ed25519_secret_key);
    auto initial = util::ustring_from_bytes(env, initial_dump);

    auto* contacts = new session::config::Contacts(secret_key, initial);

    jclass contactsClass = env->FindClass("network/loki/messenger/libsession_util/Contacts");
    jmethodID constructor = env->GetMethodID(contactsClass, "<init>", "(J)V");
    jobject newConfig = env->NewObject(contactsClass, constructor, reinterpret_cast<jlong>(contacts));

    return newConfig;
}
#pragma clang diagnostic pop
extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_Contacts_all(JNIEnv *env, jobject thiz) {
    std::lock_guard lock{util::util_mutex_};
    auto contacts = ptrToContacts(env, thiz);
    jclass stack = env->FindClass("java/util/Stack");
    jmethodID init = env->GetMethodID(stack, "<init>", "()V");
    jobject our_stack = env->NewObject(stack, init);
    jmethodID push = env->GetMethodID(stack, "push", "(Ljava/lang/Object;)Ljava/lang/Object;");
    for (const auto& contact : *contacts) {
        auto contact_obj = serialize_contact(env, contact);
        env->CallObjectMethod(our_stack, push, contact_obj);
    }
    return our_stack;
}