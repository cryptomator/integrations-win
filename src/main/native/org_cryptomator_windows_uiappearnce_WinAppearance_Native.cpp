//
//  Created by Martin Beyer on 01.11.2020.
//  Copyright © 2020 Cryptomator. All rights reserved.
//

#include "org_cryptomator_windows_uiappearance_WinAppearance_Native.h"
#include <jni.h>
#include <windows.h>

void throwIllegalStateException(JNIEnv *env, const char* message) {
    jclass exClass = env->FindClass("java/lang/IllegalStateException");
    if (exClass != NULL) {
        env->ThrowNew(exClass, message);
    }
}

JNIEXPORT jint JNICALL Java_org_cryptomator_windows_uiappearance_WinAppearance_00024Native_getCurrentTheme (JNIEnv *env, jobject thisObject){
    DWORD data{};
    DWORD dataSize = sizeof(data);
    LSTATUS status = RegGetValueW(HKEY_CURRENT_USER, L"(Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize)", L"AppsUseLightTheme", RRF_RT_DWORD, NULL, &data, &dataSize);
    if(status != ERROR_SUCCESS){
        char msg[50];
        sprintf_s(msg, 50, "Failed to read registry value (status %d)", (int) status);
        throwIllegalStateException(env, msg);
    }
    return data ? 1 : 0;
}

JNIEXPORT void JNICALL Java_org_cryptomator_windows_uiappearance_WinAppearance_00024Native_waitForNextThemeChange(JNIEnv *env, jobject thisObj){
    HKEY key;
    LSTATUS status = RegOpenKeyExW(HKEY_CURRENT_USER, L"(Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize)", 0, KEY_READ, &key);
    if (status != ERROR_SUCCESS) {
        char msg[50];
        sprintf_s(msg, 50,"Failed to open registry key (status %d)", (int) status);
        throwIllegalStateException(env, msg);
    }
    status = RegNotifyChangeKeyValue(key, TRUE, REG_NOTIFY_CHANGE_LAST_SET, NULL, FALSE);
    if (status != ERROR_SUCCESS) {
        char msg[50];
        sprintf_s(msg, 50, "Failed to observe registry key (status %d)", (int) status);
        throwIllegalStateException(env, msg);
    }
    RegCloseKey(key);
}