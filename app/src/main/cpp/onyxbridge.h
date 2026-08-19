/**
 * OnyxBridge - Android Native Bridge Library
 * Main JNI header for the OnyxBridge native library.
 *
 * This library provides:
 *   - SMS permission handling (SEND_SMS, RECEIVE_SMS, READ_SMS)
 *   - Toast display from native code
 *   - Cross-platform native code integration helpers
 *
 * Author: OnyxBridge Contributors
 * License: MIT
 */

#ifndef ONYXBRIDGE_H
#define ONYXBRIDGE_H

#include <jni.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/* JNI lifecycle */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved);
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved);

/* Initializer — must be called once with an Application/Activity context */
JNIEXPORT void JNICALL
Java_com_onyx_bridge_OnyxBridge_nativeInit(JNIEnv *env, jobject thiz, jobject context);

/* Cleanup — release cached global refs */
JNIEXPORT void JNICALL
Java_com_onyx_bridge_OnyxBridge_nativeCleanup(JNIEnv *env, jobject thiz);

/* Request SMS permissions (SEND_SMS, RECEIVE_SMS, READ_SMS) from native code */
JNIEXPORT void JNICALL
Java_com_onyx_bridge_OnyxBridge_requestSmsPermissions(JNIEnv *env, jobject thiz,
                                                       jint request_code);

/* Display a Toast message from native code */
JNIEXPORT void JNICALL
Java_com_onyx_bridge_OnyxBridge_showPermissionToastNative(JNIEnv *env, jobject thiz,
                                                              jstring message, jboolean is_long);

/* Check whether a single permission is granted (returns JNI_TRUE/JNI_FALSE) */
JNIEXPORT jboolean JNICALL
Java_com_onyx_bridge_OnyxBridge_checkPermissionNative(JNIEnv *env, jobject thiz,
                                                            jstring permission);

/* Query SMS permission state — returns int[3] {send, receive, read} */
JNIEXPORT jintArray JNICALL
Java_com_onyx_bridge_OnyxBridge_checkSmsPermissionsNative(JNIEnv *env, jobject thiz);

/* Library version string */
JNIEXPORT jstring JNICALL
Java_com_onyx_bridge_OnyxBridge_nativeVersion(JNIEnv *env, jclass clazz);

#ifdef __cplusplus
}
#endif

#endif /* ONYXBRIDGE_H */
