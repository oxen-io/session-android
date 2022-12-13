#include "config_base.h"
#include "util.h"

extern "C" {
JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_dirty(JNIEnv *env, jobject thiz) {
    auto* configBase = ptrToConfigBase(env, thiz);
    return configBase->is_dirty();
}

JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_needsPush(JNIEnv *env, jobject thiz) {
    auto config = ptrToConfigBase(env, thiz);
    return config->needs_push();
}

JNIEXPORT jboolean JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_needsDump(JNIEnv *env, jobject thiz) {
    auto config = ptrToConfigBase(env, thiz);
    return config->needs_dump();
}

JNIEXPORT jobject JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_push(JNIEnv *env, jobject thiz) {
    auto config = ptrToConfigBase(env, thiz);
    auto pair = config->push();
    session::ustring to_push_str = pair.first;
    jbyteArray returnByteArray = util::bytes_from_ustring(env, to_push_str);
    jlong seqNo = pair.second;
    jclass returnObjectClass = env->FindClass("network/loki/messenger/libsession_util/util/ConfigWithSeqNo");
    jmethodID methodId = env->GetMethodID(returnObjectClass, "<init>", "([BJ)V");
    jobject returnObject = env->NewObject(returnObjectClass, methodId, returnByteArray, seqNo);
    return returnObject;
}

JNIEXPORT jbyteArray JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_dump(JNIEnv *env, jobject thiz) {
    auto config = ptrToConfigBase(env, thiz);
    auto dumped = config->dump();
    jbyteArray bytes = util::bytes_from_ustring(env, dumped);
    return bytes;
}

JNIEXPORT jstring JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_encryptionDomain(JNIEnv *env,
                                                                         jobject thiz) {
    auto conf = ptrToConfigBase(env, thiz);
    return env->NewStringUTF(conf->encryption_domain());
}

JNIEXPORT void JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_confirmPushed(JNIEnv *env, jobject thiz,
                                                                      jlong seq_no) {
    auto conf = ptrToConfigBase(env, thiz);
    conf->confirm_pushed(seq_no);
}

JNIEXPORT jint JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_merge___3_3B(JNIEnv *env, jobject thiz,
                                                                     jobjectArray to_merge) {
    auto conf = ptrToConfigBase(env, thiz);
    size_t number = env->GetArrayLength(to_merge);
    std::vector<session::ustring> configs = {};
    for (int i = 0; i < number; i++) {
        auto jArr = (jbyteArray) env->GetObjectArrayElement(to_merge, i);
        auto bytes = util::ustring_from_bytes(env, jArr);
        configs.push_back(bytes);
    }
    return conf->merge(configs);
}

JNIEXPORT jint JNICALL
Java_network_loki_messenger_libsession_1util_ConfigBase_merge___3B(JNIEnv *env, jobject thiz,
                                                                   jbyteArray to_merge) {
    auto conf = ptrToConfigBase(env, thiz);
    std::vector<session::ustring> configs = {util::ustring_from_bytes(env, to_merge)};
    return conf->merge(configs);
}

}