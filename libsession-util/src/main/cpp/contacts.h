#ifndef SESSION_ANDROID_CONTACTS_H
#define SESSION_ANDROID_CONTACTS_H

#include <jni.h>
#include "session/config/contacts.hpp"
#include "util.h"

inline session::config::Contacts *ptrToContacts(JNIEnv *env, jobject obj) {
    jclass contactsClass = env->FindClass("network/loki/messenger/libsession_util/Contacts");
    jfieldID pointerField = env->GetFieldID(contactsClass, "pointer", "J");
    return (session::config::Contacts *) env->GetLongField(obj, pointerField);
}

inline jobject serialize_contact(JNIEnv *env, session::config::contact_info info) {
    jclass contactClass = env->FindClass("network/loki/messenger/libsession_util/util/Contact");
    jmethodID constructor = env->GetMethodID(contactClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZZLnetwork/loki/messenger/libsession_util/util/UserPic;ILnetwork/loki/messenger/libsession_util/util/ExpiryMode;)V");
    jstring id = env->NewStringUTF(info.session_id.data());
    jstring name = env->NewStringUTF(info.name.data());
    jstring nickname = env->NewStringUTF(info.nickname.data());
    jboolean approved, approvedMe, blocked;
    approved = info.approved;
    approvedMe = info.approved_me;
    blocked = info.blocked;
    auto created = info.created;
    jobject profilePic = util::serialize_user_pic(env, info.profile_picture);
    jobject returnObj = env->NewObject(contactClass, constructor, id, name, nickname, approved,
                                       approvedMe, blocked, profilePic, info.priority,
                                       util::serialize_expiry(env, info.exp_mode, info.exp_timer));
    return returnObj;
}

inline session::config::contact_info
deserialize_contact(JNIEnv *env, jobject info, session::config::Contacts *conf) {
    jclass contactClass = env->FindClass("network/loki/messenger/libsession_util/util/Contact");

    jfieldID getId, getName, getNick, getApproved, getApprovedMe, getBlocked, getUserPic, getPriority, getExpiry, getHidden;
    getId = env->GetFieldID(contactClass, "id", "Ljava/lang/String;");
    getName = env->GetFieldID(contactClass, "name", "Ljava/lang/String;");
    getNick = env->GetFieldID(contactClass, "nickname", "Ljava/lang/String;");
    getApproved = env->GetFieldID(contactClass, "approved", "Z");
    getApprovedMe = env->GetFieldID(contactClass, "approvedMe", "Z");
    getBlocked = env->GetFieldID(contactClass, "blocked", "Z");
    getUserPic = env->GetFieldID(contactClass, "profilePicture",
                                 "Lnetwork/loki/messenger/libsession_util/util/UserPic;");
    getPriority = env->GetFieldID(contactClass, "priority", "I");
    getExpiry = env->GetFieldID(contactClass, "expiryMode", "Lnetwork/loki/messenger/libsession_util/util/ExpiryMode;");
    jstring name, nickname, session_id;
    session_id = static_cast<jstring>(env->GetObjectField(info, getId));
    name = static_cast<jstring>(env->GetObjectField(info, getName));
    nickname = static_cast<jstring>(env->GetObjectField(info, getNick));
    bool approved, approvedMe, blocked, hidden;
    int priority = env->GetIntField(info, getPriority);
    approved = env->GetBooleanField(info, getApproved);
    approvedMe = env->GetBooleanField(info, getApprovedMe);
    blocked = env->GetBooleanField(info, getBlocked);
    jobject user_pic = env->GetObjectField(info, getUserPic);
    jobject expiry_mode = env->GetObjectField(info, getExpiry);

    auto expiry_pair = util::deserialize_expiry(env, expiry_mode);

    std::string url;
    session::ustring key;

    if (user_pic != nullptr) {
        auto deserialized_pic = util::deserialize_user_pic(env, user_pic);
        auto url_jstring = deserialized_pic.first;
        auto url_bytes = env->GetStringUTFChars(url_jstring, nullptr);
        url = std::string(url_bytes);
        env->ReleaseStringUTFChars(url_jstring, url_bytes);
        key = util::ustring_from_bytes(env, deserialized_pic.second);
    }

    auto session_id_bytes = env->GetStringUTFChars(session_id, nullptr);
    auto name_bytes = name ? env->GetStringUTFChars(name, nullptr) : nullptr;
    auto nickname_bytes = nickname ? env->GetStringUTFChars(nickname, nullptr) : nullptr;

    auto contact_info = conf->get_or_construct(session_id_bytes);
    if (name_bytes) {
        contact_info.name = name_bytes;
    }
    if (nickname_bytes) {
        contact_info.nickname = nickname_bytes;
    }
    contact_info.approved = approved;
    contact_info.approved_me = approvedMe;
    contact_info.blocked = blocked;
    if (!url.empty() && !key.empty()) {
        contact_info.profile_picture = session::config::profile_pic(url, key);
    } else {
        contact_info.profile_picture = session::config::profile_pic();
    }

    env->ReleaseStringUTFChars(session_id, session_id_bytes);
    if (name_bytes) {
        env->ReleaseStringUTFChars(name, name_bytes);
    }
    if (nickname_bytes) {
        env->ReleaseStringUTFChars(nickname, nickname_bytes);
    }

    contact_info.priority = priority;
    contact_info.exp_mode = expiry_pair.first;
    contact_info.exp_timer = std::chrono::seconds(expiry_pair.second);

    return contact_info;
}


#endif //SESSION_ANDROID_CONTACTS_H
