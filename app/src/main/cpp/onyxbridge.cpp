/**
 * OnyxBridge - Android Native Bridge Library
 * Main JNI entry points.
 *
 * Wires Java OnyxBridge class to native permission + toast helpers.
 */

#include "onyxbridge.h"
#include "permission_manager.h"
#include "toast_helper.h"

#include <android/log.h>
#include <jni.h>
#include <cstring>

#define LOG_TAG "OnyxBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* ------------------------------------------------------------------ */
/* Globals                                                            */
/* ------------------------------------------------------------------ */

static JavaVM *g_vm = nullptr;
static jobject g_context_ref = nullptr;   /* Global ref to Application/Activity context */
static jobject g_self_ref    = nullptr;    /* Global ref to the OnyxBridge Java instance */

/* ------------------------------------------------------------------ */
/* JNI Lifecycle                                                      */
/* ------------------------------------------------------------------ */

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad: GetEnv failed");
        return JNI_ERR;
    }
    g_vm = vm;
    LOGI("OnyxBridge native library loaded (v%s)", ONYXBRIDGE_VERSION);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    LOGI("OnyxBridge native library unloaded");
    g_vm = nullptr;
}

/* ------------------------------------------------------------------ */
/* Native methods                                                     */
/* ------------------------------------------------------------------ */

JNIEXPORT void JNICALL
Java_com_onyx_bridge_OnyxBridge_nativeInit(JNIEnv *env, jobject thiz, jobject context) {
    if (g_context_ref != nullptr) {
        env->DeleteGlobalRef(g_context_ref);
    }
    if (g_self_ref != nullptr) {
        env->DeleteGlobalRef(g_self_ref);
    }
    g_context_ref = env->NewGlobalRef(context);
    g_self_ref    = env->NewGlobalRef(thiz);
    LOGI("OnyxBridge initialized with context=%p self=%p", g_context_ref, g_self_ref);
}

JNIEXPORT void JNICALL
Java_com_onyx_bridge_OnyxBridge_nativeCleanup(JNIEnv *env, jobject thiz) {
    if (g_context_ref) {
        env->DeleteGlobalRef(g_context_ref);
        g_context_ref = nullptr;
    }
    if (g_self_ref) {
        env->DeleteGlobalRef(g_self_ref);
        g_self_ref = nullptr;
    }
    LOGI("OnyxBridge cleaned up");
}

JNIEXPORT void JNICALL
Java_com_onyx_bridge_OnyxBridge_requestSmsPermissions(JNIEnv *env, jobject thiz,
                                                       jint request_code) {
    if (g_context_ref == nullptr) {
        LOGE("requestSmsPermissions: context not initialized — call nativeInit() first");
        onyxbridge::ToastHelper::showToast(env, g_context_ref,
                                           "OnyxBridge not initialized", JNI_TRUE);
        return;
    }
    onyxbridge::PermissionManager::requestSmsPermissions(env, g_self_ref, g_context_ref,
                                                          request_code);
}

JNIEXPORT void JNICALL
Java_com_onyx_bridge_OnyxBridge_showPermissionToastNative(JNIEnv *env, jobject thiz,
                                                              jstring message, jboolean is_long) {
    if (message == nullptr) {
        LOGW("showPermissionToast: message is null");
        return;
    }
    const char *msg = env->GetStringUTFChars(message, nullptr);
    if (msg == nullptr) {
        LOGE("showPermissionToast: GetStringUTFChars failed");
        return;
    }
    onyxbridge::ToastHelper::showToast(env, g_context_ref, msg, is_long);
    env->ReleaseStringUTFChars(message, msg);
}

JNIEXPORT jboolean JNICALL
Java_com_onyx_bridge_OnyxBridge_checkPermissionNative(JNIEnv *env, jobject thiz,
                                                            jstring permission) {
    if (permission == nullptr || g_context_ref == nullptr) {
        return JNI_FALSE;
    }
    const char *perm = env->GetStringUTFChars(permission, nullptr);
    if (perm == nullptr) return JNI_FALSE;
    bool granted = onyxbridge::PermissionManager::checkPermission(env, g_context_ref, perm);
    env->ReleaseStringUTFChars(permission, perm);
    return granted ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jintArray JNICALL
Java_com_onyx_bridge_OnyxBridge_checkSmsPermissionsNative(JNIEnv *env, jobject thiz) {
    jintArray result = env->NewIntArray(3);
    if (result == nullptr) {
        LOGE("checkSmsPermissions: NewIntArray failed");
        return nullptr;
    }
    jint vals[3] = {
        onyxbridge::PermissionManager::checkPermission(env, g_context_ref, "android.permission.SEND_SMS")   ? 1 : 0,
        onyxbridge::PermissionManager::checkPermission(env, g_context_ref, "android.permission.RECEIVE_SMS") ? 1 : 0,
        onyxbridge::PermissionManager::checkPermission(env, g_context_ref, "android.permission.READ_SMS")    ? 1 : 0,
    };
    env->SetIntArrayRegion(result, 0, 3, vals);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_onyx_bridge_OnyxBridge_nativeVersion(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(ONYXBRIDGE_VERSION);
}
