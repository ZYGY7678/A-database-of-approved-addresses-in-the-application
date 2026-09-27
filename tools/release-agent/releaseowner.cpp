#include <jni.h>
#include <android/log.h>

#define LOG_TAG "ApprovedBrowserReleaseAgent"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static bool clearDeviceOwner(JavaVM* vm) {
    JNIEnv* env = nullptr;
    if (vm->AttachCurrentThread(&env, nullptr) != JNI_OK || !env) {
        LOGE("AttachCurrentThread failed");
        return false;
    }

    jclass activityThread = env->FindClass("android/app/ActivityThread");
    if (!activityThread) {
        LOGE("ActivityThread class not found");
        env->ExceptionClear();
        vm->DetachCurrentThread();
        return false;
    }

    jmethodID currentApplication =
        env->GetStaticMethodID(activityThread, "currentApplication",
                               "()Landroid/app/Application;");
    jobject app = currentApplication
        ? env->CallStaticObjectMethod(activityThread, currentApplication)
        : nullptr;

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    if (!app) {
        LOGE("currentApplication returned null");
        vm->DetachCurrentThread();
        return false;
    }

    jclass contextClass = env->FindClass("android/content/Context");
    jmethodID getSystemService =
        contextClass
            ? env->GetMethodID(contextClass, "getSystemService",
                               "(Ljava/lang/String;)Ljava/lang/Object;")
            : nullptr;

    jstring serviceName = env->NewStringUTF("device_policy");
    jobject dpm = getSystemService
        ? env->CallObjectMethod(app, getSystemService, serviceName)
        : nullptr;

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    jmethodID getPackageName =
        contextClass
            ? env->GetMethodID(contextClass, "getPackageName",
                               "()Ljava/lang/String;")
            : nullptr;
    jstring packageName = getPackageName
        ? (jstring)env->CallObjectMethod(app, getPackageName)
        : nullptr;

    if (!dpm || !packageName) {
        LOGE("Could not obtain DevicePolicyManager or package name");
        vm->DetachCurrentThread();
        return false;
    }

    jclass dpmClass = env->GetObjectClass(dpm);
    jmethodID isOwner =
        env->GetMethodID(dpmClass, "isDeviceOwnerApp", "(Ljava/lang/String;)Z");
    jmethodID clearOwner =
        env->GetMethodID(dpmClass, "clearDeviceOwnerApp", "(Ljava/lang/String;)V");

    if (!isOwner || !clearOwner) {
        LOGE("Required DevicePolicyManager methods not found");
        vm->DetachCurrentThread();
        return false;
    }

    jboolean owner = env->CallBooleanMethod(dpm, isOwner, packageName);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        owner = JNI_FALSE;
    }

    if (!owner) {
        LOGI("App is not currently the Device Owner");
        vm->DetachCurrentThread();
        return true;
    }

    LOGI("App is Device Owner; calling clearDeviceOwnerApp()");
    env->CallVoidMethod(dpm, clearOwner, packageName);

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        LOGE("clearDeviceOwnerApp threw an exception");
        vm->DetachCurrentThread();
        return false;
    }

    jboolean stillOwner = env->CallBooleanMethod(dpm, isOwner, packageName);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        stillOwner = JNI_TRUE;
    }

    LOGI("Release result: %s", stillOwner ? "still owner" : "released");
    vm->DetachCurrentThread();
    return stillOwner == JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Agent_OnAttach(JavaVM* vm, char* options, void* reserved) {
    LOGI("Approved Browser release agent attached");
    return clearDeviceOwner(vm) ? JNI_OK : JNI_ERR;
}

extern "C" JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM* vm, char* options, void* reserved) {
    LOGI("Approved Browser release agent loaded");
    return clearDeviceOwner(vm) ? JNI_OK : JNI_ERR;
}
