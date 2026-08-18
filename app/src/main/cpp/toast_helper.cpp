/**
 * OnyxBridge - ToastHelper implementation
 * Calls android.widget.Toast.makeText().show() via JNI.
 */

#include "toast_helper.h"
#include <android/log.h>
#include <jni.h>

#define LOG_TAG "OnyxBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace onyxbridge {

void ToastHelper::showToast(JNIEnv *env, jobject context, const std::string &message,
                            jboolean is_long) {
    if (env == nullptr || context == nullptr) {
        LOGW("showToast: invalid env or context");
        return;
    }

    /* Look up the Toast class */
    jclass toast_class = env->FindClass("android/widget/Toast");
    if (toast_class == nullptr) {
        LOGE("showToast: Toast class not found");
        return;
    }

    /* Toast.makeText(Context, CharSequence, int) */
    jmethodID make_text = env->GetStaticMethodID(toast_class, "makeText",
        "(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;");
    if (make_text == nullptr) {
        LOGE("showToast: Toast.makeText not found");
        env->DeleteLocalRef(toast_class);
        return;
    }

    /* Toast.show() */
    jmethodID show = env->GetMethodID(toast_class, "show", "()V");
    if (show == nullptr) {
        LOGE("showToast: Toast.show not found");
        env->DeleteLocalRef(toast_class);
        return;
    }

    /* Toast.LENGTH_SHORT = 0, Toast.LENGTH_LONG = 1 */
    jint duration = is_long == JNI_TRUE ? 1 : 0;

    jstring msg_str = env->NewStringUTF(message.c_str());
    if (msg_str == nullptr) {
        LOGE("showToast: NewStringUTF failed");
        env->DeleteLocalRef(toast_class);
        return;
    }

    jobject toast = env->CallStaticObjectMethod(toast_class, make_text,
                                                context, msg_str, duration);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        env->DeleteLocalRef(msg_str);
        env->DeleteLocalRef(toast_class);
        LOGE("showToast: makeText threw");
        return;
    }

    if (toast == nullptr) {
        LOGE("showToast: makeText returned null");
        env->DeleteLocalRef(msg_str);
        env->DeleteLocalRef(toast_class);
        return;
    }

    env->CallVoidMethod(toast, show);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    env->DeleteLocalRef(toast);
    env->DeleteLocalRef(msg_str);
    env->DeleteLocalRef(toast_class);

    LOGI("Toast shown: %s (duration=%s)", message.c_str(),
         is_long == JNI_TRUE ? "LONG" : "SHORT");
}

void ToastHelper::showShort(JNIEnv *env, jobject context, const std::string &message) {
    showToast(env, context, message, JNI_FALSE);
}

void ToastHelper::showLong(JNIEnv *env, jobject context, const std::string &message) {
    showToast(env, context, message, JNI_TRUE);
}

} // namespace onyxbridge
