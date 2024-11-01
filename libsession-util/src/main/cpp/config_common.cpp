#include <jni.h>
#include "util.h"
#include "jni_utils.h"

#include <session/config/contacts.hpp>
#include <session/config/user_groups.hpp>
#include <session/config/user_profile.hpp>
#include <session/config/convo_info_volatile.hpp>

extern "C"
JNIEXPORT jlong JNICALL
Java_network_loki_messenger_libsession_1util_ConfigKt_createConfigObject(
        JNIEnv *env,
        jclass _clazz,
        jstring java_config_name,
        jbyteArray ed25519_secret_key,
        jbyteArray initial_dump) {
    return jni_utils::run_catching_cxx_exception_or_throws<jlong>(env, [=] {
        auto config_name = util::string_from_jstring(env, java_config_name);
        auto secret_key = util::ustring_from_bytes(env, ed25519_secret_key);
        auto initial = initial_dump
                       ? std::optional(util::ustring_from_bytes(env, initial_dump))
                       : std::nullopt;


        std::lock_guard lock{util::util_mutex_};
        if (config_name == "Contacts") {
            return reinterpret_cast<jlong>(new session::config::Contacts(secret_key, initial));
        } else if (config_name == "UserProfile") {
            return reinterpret_cast<jlong>(new session::config::UserProfile(secret_key, initial));
        } else if (config_name == "UserGroups") {
            return reinterpret_cast<jlong>(new session::config::UserGroups(secret_key, initial));
        } else if (config_name == "ConvoInfoVolatile") {
            return reinterpret_cast<jlong>(new session::config::ConvoInfoVolatile(secret_key, initial));
        } else {
            throw std::invalid_argument("Unknown config name: " + config_name);
        }
    });
}