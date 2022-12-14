#include "contacts.h"
#include "util.h"

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_Contacts_get(JNIEnv *env, jobject thiz,
                                                          jstring session_id) {
    auto contacts = ptrToContacts(env, thiz);
    auto contact = contacts->get(env->GetStringUTFChars(session_id, nullptr));
    if (!contact) return nullptr;
    jobject j_contact = serialize_contact(env, contact.value());
    return j_contact;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_Contacts_getOrCreate(JNIEnv *env, jobject thiz,
                                                                  jstring session_id) {
    auto contacts = ptrToContacts(env, thiz);
    auto contact = contacts->get_or_create(env->GetStringUTFChars(session_id, nullptr));
    return serialize_contact(env, contact);
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_Contacts_set(JNIEnv *env, jobject thiz,
                                                          jobject contact) {
    auto contacts = ptrToContacts(env, thiz);
    auto contact_info = deserialize_contact(env, contact);
    contacts->set(contact_info);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_Contacts_erase(JNIEnv *env, jobject thiz,
                                                            jstring session_id) {
    auto contacts = ptrToContacts(env, thiz);
    return contacts->erase(env->GetStringUTFChars(session_id, nullptr));
}
extern "C"
#pragma clang diagnostic push
#pragma ide diagnostic ignored "bugprone-reserved-identifier"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_Contacts_00024Companion_newInstance___3B(JNIEnv *env,
                                                                                 jobject thiz,
                                                                                 jbyteArray ed25519_secret_key) {
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
    auto secret_key = util::ustring_from_bytes(env, ed25519_secret_key);
    auto initial = util::ustring_from_bytes(env, initial_dump);

    auto* contacts = new session::config::Contacts(secret_key, initial);

    jclass contactsClass = env->FindClass("network/loki/messenger/libsession_util/Contacts");
    jmethodID constructor = env->GetMethodID(contactsClass, "<init>", "(J)V");
    jobject newConfig = env->NewObject(contactsClass, constructor, reinterpret_cast<jlong>(contacts));

    return newConfig;
}
#pragma clang diagnostic pop