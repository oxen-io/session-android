#ifndef SESSION_ANDROID_CONTACTS_H
#define SESSION_ANDROID_CONTACTS_H

#include <jni.h>
#include "session/config/contacts.hpp"

inline session::config::Contacts *ptrToContacts(JNIEnv *env, jobject obj) {
    jclass contactsClass = env->FindClass("network/loki/messenger/libsession_util/Contacts");
    jfieldID pointerField = env->GetFieldID(contactsClass, "pointer", "J");
    return (session::config::Contacts *) env->GetLongField(obj, pointerField);
}

inline jobject serialize_contact(JNIEnv *env, session::config::contact_info info) {
    jclass contactClass = env->FindClass("network/loki/messenger/libsession_util/util/Contact");
    jmethodID constructor = env->GetMethodID(contactClass, "<init>",
                                             "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZZLnetwork/loki/messenger/libsession_util/util/UserPic;)V");
    // val id: String,
    // val name: String,
    // val nickname: String,
    // val approved: Boolean,
    // val approvedMe: Boolean,
    // val blocked: Boolean,
    // val profilePicture: UserPic
    return nullptr;
}

inline session::config::contact_info deserialize_contact(JNIEnv *env, jobject info) {

}


#endif //SESSION_ANDROID_CONTACTS_H
