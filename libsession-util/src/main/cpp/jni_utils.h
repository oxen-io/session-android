#ifndef SESSION_ANDROID_JNI_UTILS_H
#define SESSION_ANDROID_JNI_UTILS_H

#include <jni.h>
#include <exception>

namespace jni_utils {
    /**
     * Run a C++ function and catch any exceptions, throwing a Java exception if one is caught,
     * and returning a default-constructed value of the specified type.
     *
     * @tparam RetT The return type of the function
     * @tparam Func The function type
     * @param f The function to run
     * @param fallbackRun The function to run if an exception is caught. The optional exception message reference will be passed to this function.
     * @return The return value of the function, or the return value of the fallback function if an exception was caught
     */
    template<class RetT, class Func, class FallbackRun>
    RetT run_catching_cxx_exception_or(Func f, FallbackRun fallbackRun) {
        try {
            return f();
        } catch (const std::exception &e) {
            return fallbackRun(e.what());
        } catch (...) {
            return fallbackRun(nullptr);
        }
    }

    /**
     * Run a C++ function and catch any exceptions, throwing a Java exception if one is caught.
     *
     * @tparam RetT The return type of the function
     * @tparam Func The function type
     * @param env The JNI environment
     * @param f The function to run
     * @return The return value of the function, or a default-constructed value of the specified type if an exception was caught
     */
    template<class RetT, class Func>
    RetT run_catching_cxx_exception_or_throws(JNIEnv *env, Func f) {
        return run_catching_cxx_exception_or<RetT>(f, [env](const char *msg) {
            jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
            if (msg) {
                auto formatted_message = std::string("libsession: C++ exception: ") + msg;
                env->ThrowNew(exceptionClass, formatted_message.c_str());
            } else {
                env->ThrowNew(exceptionClass, "libsession: Unknown C++ exception");
            }

            return RetT();
        });
    }
}

#endif //SESSION_ANDROID_JNI_UTILS_H
