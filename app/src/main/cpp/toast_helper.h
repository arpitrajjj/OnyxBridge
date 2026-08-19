/**
 * OnyxBridge - ToastHelper header
 * Displays Toast messages from native code via JNI.
 */

#ifndef ONYXBRIDGE_TOAST_HELPER_H
#define ONYXBRIDGE_TOAST_HELPER_H

#include <jni.h>
#include <string>

namespace onyxbridge {

class ToastHelper {
public:
    /**
     * Show a Toast message.
     * @param env      JNI environment
     * @param context  Application/Activity context
     * @param message  Message to display
     * @param is_long  If true, Toast.LENGTH_LONG; otherwise Toast.LENGTH_SHORT
     */
    static void showToast(JNIEnv *env, jobject context, const std::string &message,
                          jboolean is_long);

    /**
     * Show a Toast message with default short duration.
     */
    static void showShort(JNIEnv *env, jobject context, const std::string &message);

    /**
     * Show a Toast message with long duration.
     */
    static void showLong(JNIEnv *env, jobject context, const std::string &message);
};

} // namespace onyxbridge

#endif // ONYXBRIDGE_TOAST_HELPER_H
