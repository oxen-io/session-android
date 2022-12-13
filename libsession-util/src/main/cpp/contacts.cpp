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