/**
 * OnyxBridge - PermissionManager header
 * Handles Android runtime permission checks and requests from native code.
 */

#ifndef ONYXBRIDGE_PERMISSION_MANAGER_H
#define ONYXBRIDGE_PERMISSION_MANAGER_H

#include <jni.h>
#include <string>
#include <vector>

namespace onyxbridge {

#define ONYXBRIDGE_VERSION "1.0.0"

class PermissionManager {
public:
    /* Constants matching the Java OnyxBridge class */
    static constexpr const char *PERMISSION_SEND_SMS   = "android.permission.SEND_SMS";
    static constexpr const char *PERMISSION_RECEIVE_SMS = "android.permission.RECEIVE_SMS";
    static constexpr const char *PERMISSION_READ_SMS    = "android.permission.READ_SMS";

    /**
     * Check whether a single permission is granted.
     * Calls Context.checkSelfPermission(String) under the hood.
     */
    static bool checkPermission(JNIEnv *env, jobject context, const std::string &permission);

    /**
     * Request SEND_SMS, RECEIVE_SMS, READ_SMS at once from the Activity.
     * Calls Activity.requestPermissions(String[], int).
     */
    static void requestSmsPermissions(JNIEnv *env, jobject thiz, jobject context,
                                      int request_code);

    /**
     * Generic helper to request arbitrary permission list from the Activity.
     */
    static void requestPermissions(JNIEnv *env, jobject context,
                                    const std::vector<std::string> &permissions,
                                    int request_code);

private:
    /* Find the activity from a context (Context may itself be an Activity) */
    static jobject findActivity(JNIEnv *env, jobject context);
};

} // namespace onyxbridge

#endif // ONYXBRIDGE_PERMISSION_MANAGER_H
