//
//  Created by Martin Beyer on 01.11.2020.
//  Copyright © 2020 Cryptomator. All rights reserved.
//

#include "org_cryptomator_windows_uiappearance_WinAppearance_Native.h"
#include <jni.h>
#include <windows.h>

// System.out.println(...)
void log(JNIEnv *env, const char *str) {
    jclass syscls = env->FindClass("java/lang/System");
    jfieldID fid = env->GetStaticFieldID(syscls, "out", "Ljava/io/PrintStream;");
    jobject out = env->GetStaticObjectField(syscls, fid);
    jclass pscls = env->FindClass("java/io/PrintStream");
    jmethodID println = env->GetMethodID(pscls, "println", "(Ljava/lang/String;)V");
    jstring jStr = env->NewStringUTF(str);
    env->CallVoidMethod(out, println, jStr);
}

void throwIllegalStateException(JNIEnv *env, const char* message) {
    jclass exClass = env->FindClass("java/lang/IllegalStateException");
    if (exClass != NULL) {
        env->ThrowNew(exClass, message);
    }
}

JNIEXPORT jint JNICALL Java_org_cryptomator_windows_uiappearance_WinAppearance_00024Native_getCurrentTheme (JNIEnv *env, jobject thisObject){
    DWORD data{};
    DWORD dataSize = sizeof(data);
    LSTATUS status = RegGetValue(HKEY_CURRENT_USER, R"(Software\Microsoft\Windows\CurrentVersion\Themes\Personalize)", "AppsUseLightTheme", RRF_RT_DWORD, NULL, &data, &dataSize);
    if(status != ERROR_SUCCESS){
        char msg[50];
        sprintf(msg, "Failed to read registry value (status %d)", (int) status);
        throwIllegalStateException(env, msg);
    }
    return data ? 1 : 0;
}

JNIEXPORT void JNICALL Java_org_cryptomator_windows_uiappearance_WinAppearance_00024Native_waitForNextThemeChange(JNIEnv *env, jobject thisObj){
    HKEY key;
    LSTATUS status = RegOpenKeyEx(HKEY_CURRENT_USER, R"(Software\Microsoft\Windows\CurrentVersion\Themes\Personalize)", 0, KEY_READ, &key);
    if (status != ERROR_SUCCESS) {
        char msg[50];
        sprintf(msg, "Failed to open registry key (status %d)", (int) status);
        throwIllegalStateException(env, msg);
    }
    status = RegNotifyChangeKeyValue(key, TRUE, REG_NOTIFY_CHANGE_LAST_SET, NULL, FALSE);
    if (status != ERROR_SUCCESS) {
        char msg[50];
        sprintf(msg, "Failed to observe registry key (status %d)", (int) status);
        throwIllegalStateException(env, msg);
    }
    RegCloseKey(key);
}

JNIEXPORT void JNICALL Java_org_cryptomator_windows_uiappearance_WinAppearance_00024Native_setToLight(JNIEnv *env, jobject thisObj) {
    HKEY key;
    LSTATUS status = RegOpenKeyEx(HKEY_CURRENT_USER, R"(Software\Microsoft\Windows\CurrentVersion\Themes\Personalize)", 0, KEY_SET_VALUE, &key);
    if (status != ERROR_SUCCESS) {
        char msg[50];
        sprintf(msg, "Failed to open registry key (status %d)", (int) status);
        throwIllegalStateException(env, msg);
    }
    DWORD value{1};
    status = RegSetValueEx(key, "AppsUseLightTheme", 0, REG_DWORD, (PBYTE)&value, sizeof(DWORD));
    if (status != ERROR_SUCCESS) {
        char msg[50];
        sprintf(msg, "Failed to write registry key (status %d)", (int) status);
        throwIllegalStateException(env, msg);
    }
    RegCloseKey(key);
}


JNIEXPORT void JNICALL Java_org_cryptomator_windows_uiappearance_WinAppearance_00024Native_setToDark(JNIEnv *env, jobject thisObj){
    HKEY key;
    LSTATUS status = RegOpenKeyEx(HKEY_CURRENT_USER, R"(Software\Microsoft\Windows\CurrentVersion\Themes\Personalize)", 0, KEY_SET_VALUE, &key);
    if (status != ERROR_SUCCESS) {
        char msg[50];
        sprintf(msg, "Failed to open registry key (status %d)", (int) status);
        throwIllegalStateException(env, msg);
    }
    DWORD value{0};
    status = RegSetValueEx(key, "AppsUseLightTheme", 0, REG_DWORD, (PBYTE)&value, sizeof(DWORD));
    if (status != ERROR_SUCCESS) {
        char msg[50];
        sprintf(msg, "Failed to write registry key (status %d)", (int) status);
        throwIllegalStateException(env, msg);
    }
    RegCloseKey(key);
}