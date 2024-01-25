#include "onion_request_builder.h"
#include "util.h"

extern "C" {
#pragma clang diagnostic push
#pragma ide diagnostic ignored "bugprone-reserved-identifier"
JNIEXPORT jobject
JNICALL
Java_network_loki_messenger_libsession_1util_OnionRequestBuilder_00024Companion_newInstance(
        JNIEnv *env, jobject thiz, jobject enc_type_) {
    jclass encTypeClass = env->FindClass("network/loki/messenger/libsession_util/OnionRequestEncryptionType");
    jmethodID encTypeOrdinal = env->GetMethodID(encTypeClass, "ordinal", "()I");
    jint enc_type_raw = env->CallIntMethod(enc_type_, encTypeOrdinal);
    auto enc_type = static_cast<session::onionreq::EncryptType>(enc_type_raw);

    auto *builder = new session::onionreq::Builder(enc_type);
    jclass userClass = env->FindClass("network/loki/messenger/libsession_util/OnionRequestBuilder");
    jmethodID constructor = env->GetMethodID(userClass, "<init>", "(J)V");
    jobject newConfig = env->NewObject(userClass, constructor, reinterpret_cast<jlong>(builder));

    return newConfig;
}

JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_OnionRequestBuilder_free(JNIEnv *env, jobject thiz) {
    auto builder = ptrToBuilder(env, thiz);
    delete builder;
}

JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_OnionRequestBuilder_setSnodeDestination(
        JNIEnv *env,
        jobject thiz,
        jstring ed25519_pub_key_hex,
        jstring x25519_pub_key_hex) {
    auto builder = ptrToBuilder(env, thiz);
    auto ed_pub_key_hex_bytes = env->GetStringUTFChars(ed25519_pub_key_hex, nullptr);
    auto x_pub_key_hex_bytes = env->GetStringUTFChars(x25519_pub_key_hex, nullptr);
    auto ed_pub_key = session::onionreq::ed25519_pubkey::from_hex(ed_pub_key_hex_bytes);
    auto x_pub_key = session::onionreq::x25519_pubkey::from_hex(x_pub_key_hex_bytes);
    builder->set_snode_destination(ed_pub_key, x_pub_key);
    env->ReleaseStringUTFChars(ed25519_pub_key_hex, ed_pub_key_hex_bytes);
    env->ReleaseStringUTFChars(x25519_pub_key_hex, x_pub_key_hex_bytes);
}

JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_OnionRequestBuilder_setServerDestination(
        JNIEnv *env,
        jobject thiz,
        jstring host,
        jstring target,
        jstring scheme,
        jint port,
        jstring x25519_pub_key_hex) {
    auto builder = ptrToBuilder(env, thiz);
    auto host_bytes = env->GetStringUTFChars(host, nullptr);
    auto target_bytes = env->GetStringUTFChars(target, nullptr);
    auto scheme_bytes = env->GetStringUTFChars(scheme, nullptr);
    auto x_pub_key_hex_bytes = env->GetStringUTFChars(x25519_pub_key_hex, nullptr);
    auto x_pub_key = session::onionreq::x25519_pubkey::from_hex(x_pub_key_hex_bytes);
    builder->set_server_destination(host_bytes, target_bytes, scheme_bytes, port, x_pub_key);
    env->ReleaseStringUTFChars(host, host_bytes);
    env->ReleaseStringUTFChars(target, target_bytes);
    env->ReleaseStringUTFChars(scheme, scheme_bytes);
    env->ReleaseStringUTFChars(x25519_pub_key_hex, x_pub_key_hex_bytes);
}

JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_OnionRequestBuilder_addHop(
        JNIEnv *env,
        jobject thiz,
        jstring ed25519_pub_key_hex,
        jstring x25519_pub_key_hex) {
    auto builder = ptrToBuilder(env, thiz);
    auto ed_pub_key_hex_bytes = env->GetStringUTFChars(ed25519_pub_key_hex, nullptr);
    auto x_pub_key_hex_bytes = env->GetStringUTFChars(x25519_pub_key_hex, nullptr);
    auto ed_pub_key = session::onionreq::ed25519_pubkey::from_hex(ed_pub_key_hex_bytes);
    auto x_pub_key = session::onionreq::x25519_pubkey::from_hex(x_pub_key_hex_bytes);
    builder->add_hop(std::pair(ed_pub_key, x_pub_key));
    env->ReleaseStringUTFChars(ed25519_pub_key_hex, ed_pub_key_hex_bytes);
    env->ReleaseStringUTFChars(x25519_pub_key_hex, x_pub_key_hex_bytes);
}

JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_OnionRequestBuilder_build(
        JNIEnv *env,
        jobject thiz,
        jbyteArray payload) {
    auto builder = ptrToBuilder(env, thiz);
    auto payload_ustring = util::ustring_from_bytes(env, payload);
    auto result = builder->build(payload_ustring);
    jbyteArray bytes = util::bytes_from_ustring(env, result);
    return bytes;
}

JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_OnionRequestBuilder_decrypt(
        JNIEnv *env,
        jobject thiz,
        jbyteArray ciphertext) {
    auto builder = ptrToBuilder(env, thiz);
    auto parser = new session::onionreq::ResponseParser(*builder);
    auto ciphertext_ustring = util::ustring_from_bytes(env, ciphertext);
    auto result = parser->decrypt(ciphertext_ustring);
    delete parser;

    return util::bytes_from_ustring(env, result);
}
}