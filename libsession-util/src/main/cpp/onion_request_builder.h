//
// Created by Morgan Pretty on 29/11/2023.
//

#ifndef SESSION_ANDROID_ONION_REQUEST_BUILDER_H
#define SESSION_ANDROID_ONION_REQUEST_BUILDER_H

#include <jni.h>
#include "util.h"
#include "session/onionreq/builder.hpp"
#include "session/onionreq/response_parser.hpp"

inline session::onionreq::Builder *ptrToBuilder(JNIEnv *env, jobject obj) {
    jclass builderClass = env->FindClass("network/loki/messenger/libsession_util/OnionRequestBuilder");
    jfieldID pointerField = env->GetFieldID(builderClass, "pointer", "J");
    return (session::onionreq::Builder *) env->GetLongField(obj, pointerField);
}


#endif //SESSION_ANDROID_ONION_REQUEST_BUILDER_H
