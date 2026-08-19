/**
 * OnyxBridge - PermissionManager implementation
 * Bridges Android runtime permission API to native C++.
 */

#include "permission_manager.h"
#include "toast_helper.h"
#include <android/log.h>
#include <jni.h>

#define LOG_TAG "OnyxBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace onyxbridge {

jobject PermissionManager::findActivity(JNIEnv *env, jobject context) {
    if (context == nullptr) return nullptr;

    /* Try to walk up the inheritance chain until we hit an Activity.
     * This works for Application contexts, ContextWrappers, etc.
     */
    jclass context_class = env->GetObjectClass(context);
    jclass activity_class = env->FindClass("android/app/Activity");
    if (activity_class == nullptr) {
        LOGE("findActivity: Activity class not found");
        env->DeleteLocalRef(context_class);
        return nullptr;
    }

    if (env->IsInstanceOf(context, activity_class)) {
        env->DeleteLocalRef(context_class);
        env->DeleteLocalRef(activity_class);
        /* Return same ref, caller is responsible for global-ifying if needed */
        return context;
    }

    /* Not an Activity — try ContextWrapper.getContext() */
    jclass context_wrapper = env->FindClass("android/content/ContextWrapper");
    if (context_wrapper != nullptr && env->IsInstanceOf(context, context_wrapper)) {
        jmethodID get_context_m = env->GetMethodID(context_wrapper, "getBaseContext",
                                                    "()Landroid/content/Context;");
        if (get_context_m != nullptr) {
            jobject inner = env->CallObjectMethod(context, get_context_m);
            env->DeleteLocalRef(context_wrapper);
            env->DeleteLocalRef(context_class);
            env->DeleteLocalRef(activity_class);
            return findActivity(env, inner);
        }
    }
    env->DeleteLocalRef(context_wrapper);
    env->DeleteLocalRef(context_class);
    env->DeleteLocalRef(activity_class);
    return nullptr;
}

bool PermissionManager::checkPermission(JNIEnv *env, jobject context, const std::string &permission) {
    if (env == nullptr || context == nullptr || permission.empty()) {
        return false;
    }

    jclass context_class = env->GetObjectClass(context);
    if (context_class == nullptr) {
        LOGE("checkPermission: cannot resolve Context class");
        return false;
    }

    jmethodID check_method = env->GetMethodID(context_class, "checkSelfPermission",
                                              "(Ljava/lang/String;)I");
    if (check_method == nullptr) {
        LOGE("checkPermission: checkSelfPermission not found on Context");
        env->DeleteLocalRef(context_class);
        return false;
    }

    jstring perm_str = env->NewStringUTF(permission.c_str());
    if (perm_str == nullptr) {
        env->DeleteLocalRef(context_class);
        return false;
    }

    jint result = env->CallIntMethod(context, check_method, perm_str);
    env->DeleteLocalRef(perm_str);
    env->DeleteLocalRef(context_class);

    /* PackageManager.PERMISSION_GRANTED == 0 */
    return (result == 0);
}

void PermissionManager::requestSmsPermissions(JNIEnv *env, jobject thiz, jobject context,
                                             int request_code) {
    std::vector<std::string> perms = {
        PERMISSION_SEND_SMS,
        PERMISSION_RECEIVE_SMS,
        PERMISSION_READ_SMS,
    };

    jobject activity = findActivity(env, context);
    if (activity == nullptr) {
        LOGE("requestSmsPermissions: cannot resolve Activity from context");
        ToastHelper::showToast(env, context,
                               "OnyxBridge: Activity context required", JNI_TRUE);
        return;
    }

    requestPermissions(env, activity, perms, request_code);
    env->DeleteLocalRef(activity);
}

void PermissionManager::requestPermissions(JNIEnv *env, jobject context,
                                          const std::vector<std::string> &permissions,
                                          int request_code) {
    if (context == nullptr || permissions.empty()) {
        LOGW("requestPermissions: invalid args");
        return;
    }

    jclass activity_class = env->FindClass("android/app/Activity");
    if (activity_class == nullptr) {
        LOGE("requestPermissions: Activity class not found");
        return;
    }

    jmethodID request_method = env->GetMethodID(activity_class, "requestPermissions",
                                                "([Ljava/lang/String;I)V");
    if (request_method == nullptr) {
        LOGE("requestPermissions: requestPermissions method not found");
        env->DeleteLocalRef(activity_class);
        return;
    }

    jclass string_class = env->FindClass("java/lang/String");
    jobjectArray perm_array = env->NewObjectArray(static_cast<jsize>(permissions.size()),
                                                  string_class, nullptr);
    if (perm_array == nullptr) {
        LOGE("requestPermissions: NewObjectArray failed");
        env->DeleteLocalRef(activity_class);
        env->DeleteLocalRef(string_class);
        return;
    }

    for (size_t i = 0; i < permissions.size(); ++i) {
        jstring perm_str = env->NewStringUTF(permissions[i].c_str());
        env->SetObjectArrayElement(perm_array, static_cast<jsize>(i), perm_str);
        env->DeleteLocalRef(perm_str);
    }

    env->CallVoidMethod(context, request_method, perm_array,
                        static_cast<jint>(request_code));

    env->DeleteLocalRef(perm_array);
    env->DeleteLocalRef(string_class);
    env->DeleteLocalRef(activity_class);

    LOGI("requestPermissions: requested %zu permission(s), code=%d",
         permissions.size(), request_code);
}

} // namespace onyxbridge
