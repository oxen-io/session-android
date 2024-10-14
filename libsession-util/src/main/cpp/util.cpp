#include "util.h"
#include "sodium/randombytes.h"
#include <sodium/crypto_sign.h>
#include <session/multi_encrypt.hpp>
#include <string>

#include <android/log.h>

#define  LOG_TAG    "libsession_util"

#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define  LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG,__VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

namespace util {

    std::mutex util_mutex_ = std::mutex();

    jbyteArray bytes_from_ustring(JNIEnv* env, session::ustring_view from_str) {
        size_t length = from_str.length();
        auto jlength = (jsize)length;
        jbyteArray new_array = env->NewByteArray(jlength);
        env->SetByteArrayRegion(new_array, 0, jlength, (jbyte*)from_str.data());
        return new_array;
    }

    session::ustring ustring_from_bytes(JNIEnv* env, jbyteArray byteArray) {
        if (byteArray == nullptr) {
            return {};
        }
        size_t len = env->GetArrayLength(byteArray);
        auto bytes = env->GetByteArrayElements(byteArray, nullptr);

        session::ustring st{reinterpret_cast<const unsigned char *>(bytes), len};
        env->ReleaseByteArrayElements(byteArray, bytes, 0);
        return st;
    }

    std::string string_from_jstring(JNIEnv* env, jstring string) {
        size_t len = env->GetStringUTFLength(string);
        auto chars = env->GetStringUTFChars(string, nullptr);

        std::string st(chars, len);
        env->ReleaseStringUTFChars(string, chars);
        return st;
    }

    jobject serialize_user_pic(JNIEnv *env, session::config::profile_pic pic) {
        jclass returnObjectClass = env->FindClass("network/loki/messenger/libsession_util/util/UserPic");
        jmethodID constructor = env->GetMethodID(returnObjectClass, "<init>", "(Ljava/lang/String;[B)V");
        jstring url = env->NewStringUTF(pic.url.data());
        jbyteArray byteArray = util::bytes_from_ustring(env, pic.key);
        return env->NewObject(returnObjectClass, constructor, url, byteArray);
    }

    std::pair<jstring, jbyteArray> deserialize_user_pic(JNIEnv *env, jobject user_pic) {
        jclass userPicClass = env->FindClass("network/loki/messenger/libsession_util/util/UserPic");
        jfieldID picField = env->GetFieldID(userPicClass, "url", "Ljava/lang/String;");
        jfieldID keyField = env->GetFieldID(userPicClass, "key", "[B");
        auto pic = (jstring)env->GetObjectField(user_pic, picField);
        auto key = (jbyteArray)env->GetObjectField(user_pic, keyField);
        return {pic, key};
    }

    jobject serialize_base_community(JNIEnv *env, const session::config::community& community) {
        jclass base_community_clazz = env->FindClass("network/loki/messenger/libsession_util/util/BaseCommunityInfo");
        jmethodID base_community_constructor = env->GetMethodID(base_community_clazz, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
        auto base_url = env->NewStringUTF(community.base_url().data());
        auto room = env->NewStringUTF(community.room().data());
        auto pubkey_jstring = env->NewStringUTF(community.pubkey_hex().data());
        jobject ret = env->NewObject(base_community_clazz, base_community_constructor, base_url, room, pubkey_jstring);
        return ret;
    }

    session::config::community deserialize_base_community(JNIEnv *env, jobject base_community) {
        jclass base_community_clazz = env->FindClass("network/loki/messenger/libsession_util/util/BaseCommunityInfo");
        jfieldID base_url_field = env->GetFieldID(base_community_clazz, "baseUrl", "Ljava/lang/String;");
        jfieldID room_field = env->GetFieldID(base_community_clazz, "room", "Ljava/lang/String;");
        jfieldID pubkey_hex_field = env->GetFieldID(base_community_clazz, "pubKeyHex", "Ljava/lang/String;");
        auto base_url = (jstring)env->GetObjectField(base_community,base_url_field);
        auto room = (jstring)env->GetObjectField(base_community, room_field);
        auto pub_key_hex = (jstring)env->GetObjectField(base_community, pubkey_hex_field);
        auto base_url_chars = env->GetStringUTFChars(base_url, nullptr);
        auto room_chars = env->GetStringUTFChars(room, nullptr);
        auto pub_key_hex_chars = env->GetStringUTFChars(pub_key_hex, nullptr);

        auto community = session::config::community(base_url_chars, room_chars, pub_key_hex_chars);

        env->ReleaseStringUTFChars(base_url, base_url_chars);
        env->ReleaseStringUTFChars(room, room_chars);
        env->ReleaseStringUTFChars(pub_key_hex, pub_key_hex_chars);
        return community;
    }

    jobject serialize_expiry(JNIEnv *env, const session::config::expiration_mode& mode, const std::chrono::seconds& time_seconds) {
        jclass none = env->FindClass("network/loki/messenger/libsession_util/util/ExpiryMode$NONE");
        jfieldID none_instance = env->GetStaticFieldID(none, "INSTANCE", "Lnetwork/loki/messenger/libsession_util/util/ExpiryMode$NONE;");
        jclass after_send = env->FindClass("network/loki/messenger/libsession_util/util/ExpiryMode$AfterSend");
        jmethodID send_init = env->GetMethodID(after_send, "<init>", "(J)V");
        jclass after_read = env->FindClass("network/loki/messenger/libsession_util/util/ExpiryMode$AfterRead");
        jmethodID read_init = env->GetMethodID(after_read, "<init>", "(J)V");

        if (mode == session::config::expiration_mode::none) {
            return env->GetStaticObjectField(none, none_instance);
        } else if (mode == session::config::expiration_mode::after_send) {
            return env->NewObject(after_send, send_init, time_seconds.count());
        } else if (mode == session::config::expiration_mode::after_read) {
            return env->NewObject(after_read, read_init, time_seconds.count());
        }
        return nullptr;
    }

    std::pair<session::config::expiration_mode, long> deserialize_expiry(JNIEnv *env, jobject expiry_mode) {
        jclass parent = env->FindClass("network/loki/messenger/libsession_util/util/ExpiryMode");
        jclass after_read = env->FindClass("network/loki/messenger/libsession_util/util/ExpiryMode$AfterRead");
        jclass after_send = env->FindClass("network/loki/messenger/libsession_util/util/ExpiryMode$AfterSend");
        jfieldID duration_seconds = env->GetFieldID(parent, "expirySeconds", "J");

        jclass object_class = env->GetObjectClass(expiry_mode);

        if (env->IsSameObject(object_class, after_read)) {
            return std::pair(session::config::expiration_mode::after_read, env->GetLongField(expiry_mode, duration_seconds));
        } else if (env->IsSameObject(object_class, after_send)) {
            return std::pair(session::config::expiration_mode::after_send, env->GetLongField(expiry_mode, duration_seconds));
        }
        return std::pair(session::config::expiration_mode::none, 0);
    }

    jobject build_string_stack(JNIEnv* env, std::vector<std::string> to_add) {
        jclass stack_class = env->FindClass("java/util/Stack");
        jmethodID constructor = env->GetMethodID(stack_class,"<init>", "()V");
        jmethodID add = env->GetMethodID(stack_class, "push", "(Ljava/lang/Object;)Ljava/lang/Object;");
        jobject our_stack = env->NewObject(stack_class, constructor);
        for (std::basic_string_view<char> string: to_add) {
            env->CallObjectMethod(our_stack, add, env->NewStringUTF(string.data()));
        }
        return our_stack;
    }

    jobject serialize_group_member(JNIEnv* env, const session::config::groups::member& member) {
        jclass group_member_class = env->FindClass("network/loki/messenger/libsession_util/util/GroupMember");
        jmethodID constructor = env->GetMethodID(group_member_class, "<init>", "(Ljava/lang/String;Ljava/lang/String;Lnetwork/loki/messenger/libsession_util/util/UserPic;ZZIII)V");
        jobject user_pic = serialize_user_pic(env, member.profile_picture);
        jstring session_id = env->NewStringUTF(member.session_id.data());
        jstring name = env->NewStringUTF(member.name.data());
        return env->NewObject(group_member_class,
                              constructor,
                              session_id,
                              name,
                              user_pic,
                              (jboolean) member.admin,
                              (jboolean) member.supplement,
                              (jint) member.invite_status,
                              (jint) member.promotion_status,
                              (jint) member.removed_status
                          );
    }

    session::config::groups::member deserialize_group_member(JNIEnv* env, jobject member) {
        jclass group_member_class = env->FindClass("network/loki/messenger/libsession_util/util/GroupMember");
        jfieldID session_id_field = env->GetFieldID(group_member_class, "sessionId", "Ljava/lang/String;");
        jfieldID name_field = env->GetFieldID(group_member_class, "name", "Ljava/lang/String;");
        jfieldID user_pic_field = env->GetFieldID(group_member_class,"profilePicture", "Lnetwork/loki/messenger/libsession_util/util/UserPic;");
        jfieldID invite_status_field = env->GetFieldID(group_member_class, "inviteStatus", "I");
        jfieldID admin_field = env->GetFieldID(group_member_class, "admin", "Z");
        jfieldID promotion_status_field = env->GetFieldID(group_member_class, "promotionStatus", "I");
        jfieldID removed_status_field = env->GetFieldID(group_member_class, "removedStatus", "I");
        jfieldID supplement_field = env->GetFieldID(group_member_class, "supplement", "Z");
        auto session_id = (jstring)env->GetObjectField(member, session_id_field);
        auto session_id_bytes = env->GetStringUTFChars(session_id, nullptr);
        auto name = (jstring)env->GetObjectField(member, name_field);
        auto name_bytes = env->GetStringUTFChars(name, nullptr);
        auto user_pic_jobject = env->GetObjectField(member, user_pic_field);
        auto user_pic = deserialize_user_pic(env, user_pic_jobject);
        auto url_bytes = env->GetStringUTFChars(user_pic.first, nullptr);
        auto pic_key = ustring_from_bytes(env, user_pic.second);
        auto invite_status = env->GetIntField(member, invite_status_field);
        auto promotion_status = env->GetIntField(member, promotion_status_field);
        auto removed_status = env->GetIntField(member, removed_status_field);
        auto admin = env->GetBooleanField(member, admin_field);
        auto supplement = env->GetBooleanField(member, supplement_field);

        // set up the object
        session::config::groups::member group_member(session_id_bytes);
        group_member.name = name_bytes;
        group_member.profile_picture.url = url_bytes;
        group_member.profile_picture.set_key(pic_key);
        group_member.invite_status = invite_status;
        group_member.promotion_status = promotion_status;
        group_member.removed_status = removed_status;
        group_member.supplement = supplement;
        group_member.admin = admin;

        env->ReleaseStringUTFChars(user_pic.first, url_bytes);
        env->ReleaseStringUTFChars(session_id, session_id_bytes);
        env->ReleaseStringUTFChars(name, name_bytes);
        return group_member;
    }

    jobject deserialize_swarm_auth(JNIEnv *env, session::config::groups::Keys::swarm_auth auth) {
        jclass swarm_auth_class = env->FindClass("network/loki/messenger/libsession_util/GroupKeysConfig$SwarmAuth");
        jmethodID constructor = env->GetMethodID(swarm_auth_class, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
        jstring sub_account = env->NewStringUTF(auth.subaccount.data());
        jstring sub_account_sig = env->NewStringUTF(auth.subaccount_sig.data());
        jstring signature = env->NewStringUTF(auth.signature.data());

        return env->NewObject(swarm_auth_class, constructor, sub_account, sub_account_sig, signature);
    }

    jobject jlongFromOptional(JNIEnv* env, std::optional<long long> optional) {
        if (!optional) {
            return nullptr;
        }
        jclass longClass = env->FindClass("java/lang/Long");
        jmethodID constructor = env->GetMethodID(longClass, "<init>", "(J)V");
        jobject returned = env->NewObject(longClass, constructor, (jlong)*optional);
        return returned;
    }

    jstring jstringFromOptional(JNIEnv* env, std::optional<std::string_view> optional) {
        if (!optional) {
            return nullptr;
        }
        return env->NewStringUTF(optional->data());
    }

    jobject serialize_account_id(JNIEnv* env, std::string_view session_id) {
        if (session_id.size() != 66) return nullptr;

        jclass id_class = env->FindClass("org/session/libsignal/utilities/AccountId");
        jmethodID session_id_constructor = env->GetMethodID(id_class, "<init>", "(Ljava/lang/String;)V");

        jstring session_id_string = env->NewStringUTF(session_id.data());

        return env->NewObject(id_class, session_id_constructor, session_id_string);
    }

    std::string deserialize_account_id(JNIEnv* env, jobject account_id) {
        jclass session_id_class = env->FindClass("org/session/libsignal/utilities/AccountId");
        jmethodID get_string = env->GetMethodID(session_id_class, "getHexString", "()Ljava/lang/String;");
        auto hex_jstring = (jstring)env->CallObjectMethod(account_id, get_string);
        auto hex_bytes = env->GetStringUTFChars(hex_jstring, nullptr);
        std::string hex_string{hex_bytes};
        env->ReleaseStringUTFChars(hex_jstring, hex_bytes);
        return hex_string;
    }

}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_util_Sodium_ed25519KeyPair(JNIEnv *env, jobject thiz, jbyteArray seed) {
    std::array<unsigned char, 32> ed_pk; // NOLINT(cppcoreguidelines-pro-type-member-init)
    std::array<unsigned char, 64> ed_sk; // NOLINT(cppcoreguidelines-pro-type-member-init)
    auto seed_bytes = util::ustring_from_bytes(env, seed);
    crypto_sign_ed25519_seed_keypair(ed_pk.data(), ed_sk.data(), seed_bytes.data());

    jclass kp_class = env->FindClass("network/loki/messenger/libsession_util/util/KeyPair");
    jmethodID kp_constructor = env->GetMethodID(kp_class, "<init>", "([B[B)V");

    jbyteArray pk_jarray = util::bytes_from_ustring(env, session::ustring_view {ed_pk.data(), ed_pk.size()});
    jbyteArray sk_jarray = util::bytes_from_ustring(env, session::ustring_view {ed_sk.data(), ed_sk.size()});

    jobject return_obj = env->NewObject(kp_class, kp_constructor, pk_jarray, sk_jarray);
    return return_obj;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_util_Sodium_ed25519PkToCurve25519(JNIEnv *env,
                                                                               jobject thiz,
                                                                               jbyteArray pk) {
    auto ed_pk = util::ustring_from_bytes(env, pk);
    std::array<unsigned char, 32> curve_pk; // NOLINT(cppcoreguidelines-pro-type-member-init)
    int success = crypto_sign_ed25519_pk_to_curve25519(curve_pk.data(), ed_pk.data());
    if (success != 0) {
        jclass exception = env->FindClass("java/lang/Exception");
        env->ThrowNew(exception, "Invalid crypto_sign_ed25519_pk_to_curve25519 operation");
        return nullptr;
    }
    jbyteArray curve_pk_jarray = util::bytes_from_ustring(env, session::ustring_view {curve_pk.data(), curve_pk.size()});
    return curve_pk_jarray;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_util_Sodium_encryptForMultipleSimple(
        JNIEnv *env, jobject thiz, jobjectArray messages, jobjectArray recipients,
        jbyteArray ed25519_secret_key, jstring domain) {
    // messages and recipients have to be the same size
    uint size = env->GetArrayLength(messages);
    if (env->GetArrayLength(recipients) != size) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Messages and recipients must be the same size");
        return nullptr;
    }
    std::vector<session::ustring> message_vec{};
    std::vector<session::ustring> recipient_vec{};
    for (int i = 0; i < size; i++) {
        jbyteArray message_j = static_cast<jbyteArray>(env->GetObjectArrayElement(messages, i));
        jbyteArray recipient_j = static_cast<jbyteArray>(env->GetObjectArrayElement(recipients, i));
        session::ustring message = util::ustring_from_bytes(env, message_j);
        session::ustring recipient = util::ustring_from_bytes(env, recipient_j);

        message_vec.emplace_back(session::ustring{message});
        recipient_vec.emplace_back(session::ustring{recipient});
    }

    std::vector<session::ustring_view> message_sv_vec{};
    std::vector<session::ustring_view> recipient_sv_vec{};
    for (int i = 0; i < size; i++) {
        message_sv_vec.emplace_back(session::to_unsigned_sv(message_vec[i]));
        recipient_sv_vec.emplace_back(session::to_unsigned_sv(recipient_vec[i]));
    }

    auto sk = util::ustring_from_bytes(env, ed25519_secret_key);
    std::array<unsigned char, 24> random_nonce;
    randombytes_buf(random_nonce.data(), random_nonce.size());

    auto domain_string = env->GetStringUTFChars(domain, nullptr);

    auto result = session::encrypt_for_multiple_simple(
            message_sv_vec,
            recipient_sv_vec,
            sk,
            domain_string,
            session::ustring_view {random_nonce.data(), 24}
    );

    env->ReleaseStringUTFChars(domain, domain_string);
    auto encoded = util::bytes_from_ustring(env, result);
    return encoded;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_util_Sodium_decryptForMultipleSimple(JNIEnv *env,
                                                                                  jobject thiz,
                                                                                  jbyteArray encoded,
                                                                                  jbyteArray secret_key,
                                                                                  jbyteArray sender_pub_key,
                                                                                  jstring domain) {
    auto sk_ustring = util::ustring_from_bytes(env, secret_key);
    auto encoded_ustring = util::ustring_from_bytes(env, encoded);
    auto pub_ustring = util::ustring_from_bytes(env, sender_pub_key);
    auto domain_bytes = env->GetStringUTFChars(domain, nullptr);
    auto result = session::decrypt_for_multiple_simple(
            encoded_ustring,
            sk_ustring,
            pub_ustring,
            domain_bytes
            );
    env->ReleaseStringUTFChars(domain,domain_bytes);
    if (result) {
        return util::bytes_from_ustring(env, *result);
    } else {
        LOGD("no result from decrypt");
    }
    return nullptr;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_util_BaseCommunityInfo_00024Companion_parseFullUrl(
        JNIEnv *env, jobject thiz, jstring full_url) {
    auto bytes = env->GetStringUTFChars(full_url, nullptr);
    auto [base, room, pk] = session::config::community::parse_full_url(bytes);
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
JNIEXPORT jstring JNICALL
Java_network_loki_messenger_libsession_1util_util_BaseCommunityInfo_fullUrl(JNIEnv *env,
                                                                            jobject thiz) {
    auto deserialized = util::deserialize_base_community(env, thiz);
    auto full_url = deserialized.full_url();
    return env->NewStringUTF(full_url.data());
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_session_libsignal_utilities_Namespace_DEFAULT(JNIEnv *env, jobject thiz) {
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_session_libsignal_utilities_Namespace_USER_1PROFILE(JNIEnv *env, jobject thiz) {
    return (int) session::config::Namespace::UserProfile;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_session_libsignal_utilities_Namespace_CONTACTS(JNIEnv *env, jobject thiz) {
    return (int) session::config::Namespace::Contacts;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_session_libsignal_utilities_Namespace_CONVO_1INFO_1VOLATILE(JNIEnv *env, jobject thiz) {
    return (int) session::config::Namespace::ConvoInfoVolatile;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_session_libsignal_utilities_Namespace_GROUPS(JNIEnv *env, jobject thiz) {
    return (int) session::config::Namespace::UserGroups;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_session_libsignal_utilities_Namespace_CLOSED_1GROUP_1INFO(JNIEnv *env, jobject thiz) {
    return (int) session::config::Namespace::GroupInfo;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_session_libsignal_utilities_Namespace_CLOSED_1GROUP_1MEMBERS(JNIEnv *env, jobject thiz) {
    return (int) session::config::Namespace::GroupMembers;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_session_libsignal_utilities_Namespace_ENCRYPTION_1KEYS(JNIEnv *env, jobject thiz) {
    return (int) session::config::Namespace::GroupKeys;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_session_libsignal_utilities_Namespace_CLOSED_1GROUP_1MESSAGES(JNIEnv *env, jobject thiz) {
    return  (int) session::config::Namespace::GroupMessages;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_session_libsignal_utilities_Namespace_REVOKED_1GROUP_1MESSAGES(JNIEnv *env, jobject thiz) {
    return -11; // we don't have revoked namespace in user configs
}

extern "C"
JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_Config_free(JNIEnv *env, jobject thiz) {
    jclass baseClass = env->FindClass("network/loki/messenger/libsession_util/Config");
    jfieldID pointerField = env->GetFieldID(baseClass, "pointer", "J");
    jclass sig = env->FindClass("network/loki/messenger/libsession_util/ConfigSig");
    jclass base = env->FindClass("network/loki/messenger/libsession_util/ConfigBase");
    jclass ours = env->GetObjectClass(thiz);
    if (env->IsSameObject(sig, ours)) {
        // config sig object
        auto config = (session::config::ConfigSig*) env->GetLongField(thiz, pointerField);
        delete config;
    } else if (env->IsSameObject(base, ours)) {
        auto config = (session::config::ConfigBase*) env->GetLongField(thiz, pointerField);
        delete config;
    }
}