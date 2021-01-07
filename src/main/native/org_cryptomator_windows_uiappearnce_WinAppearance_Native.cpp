//
//  Created by Martin Beyer on 01.11.2020.
//  Copyright © 2020 Cryptomator. All rights reserved.
//

#include "org_cryptomator_windows_uiappearance_WinAppearance_Native.h"
#include <jni.h>
#include <windows.h>
#include <winreg.h>
#include <iostream>

using namespace std;

class Observer {
private:
    JavaVM *vm;
    jobject listener;
public:
    HWND window;

    Observer(JavaVM *vm, jobject listener): vm(vm), listener(listener) {};
    int calljvm();
    int registerWndProc();
    jobject getListener() { return listener; };

    static LRESULT WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);
};

static Observer *observer;

LRESULT CALLBACK Observer::WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    WCHAR *plParam = (LPWSTR) lParam;
    switch (msg) {
        case WM_SETTINGCHANGE:
            if (plParam) {
                //Only call the notify methode if the correct setting changed
                if (lstrcmp(reinterpret_cast<LPCSTR>(plParam), TEXT("ImmersiveColorSet")) == 0 && observer != NULL) {
                    observer->calljvm();
                }
                break;
            }
        default:
            return DefWindowProc(hwnd, msg, wParam, lParam);
    }
    return EXIT_SUCCESS;
}

int Observer::calljvm() { //notify
    JNIEnv *env;
    if (vm->GetEnv((void **)&env, JNI_VERSION_10) != JNI_OK) {
        return EXIT_FAILURE;
    }

/*
    if (getEnvStat == JNI_EDETACHED) {
        MessageBox(NULL, TEXT("GetEnv: not attached, sollte nich sein."), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove
        if (jvm->AttachCurrentThread((void **) &env, NULL) != 0) {
            MessageBox(NULL, TEXT("Failed to attach"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove
        }
    } else if (getEnvStat == JNI_OK) {
        //
    } else if (getEnvStat == JNI_EVERSION) {
        MessageBox(NULL, TEXT("Failed to attach"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove
    }
    MessageBox(NULL, TEXT("Thread Attached"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove
*/

    //globalVM->GetEnv()
    /*
    //JavaVM *vm = nullptr;
    if (cur_env->GetJavaVM(&vm) != JNI_OK){ //crashes
       MessageBox(NULL, TEXT("JNI Is not OK"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove
       return EXIT_FAILURE;
    } else {   MessageBox(NULL, TEXT("JNI Is OK"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove
    };
     */
    //(*globalVM).AttachCurrentThread((void **)&cur_env,NULL);
    //MessageBox(NULL, TEXT("called AttachCurrentThread"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove

    jclass listenerClass = env->GetObjectClass(listener);
    if (listenerClass == NULL) {
        MessageBox(NULL, TEXT("listenerClass == NULL"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove
        return EXIT_FAILURE;
    }

    jmethodID listenerMethodID = env->GetMethodID(listenerClass, "systemAppearanceChanged", "()V");
    if (listenerMethodID == NULL) {
        MessageBox(NULL, TEXT("listenerMethodID == NULL"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove
        return EXIT_FAILURE;
    }

    if(env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    //MessageBox(NULL, TEXT("calling..."), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove

    env->CallVoidMethod(listener, listenerMethodID);
    //MessageBox(NULL, TEXT("CallVoidMethod called"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove

    //(*globalVM).DetachCurrentThread();
    //MessageBox(NULL, TEXT("DetachCurrentThread called"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove

    return EXIT_SUCCESS;
}

int Observer::registerWndProc() {
    HINSTANCE h2 = GetModuleHandle(NULL);
    static char szAppName[] = "WindowsSettingsThemeListener"; //TODO get a proper name
    WNDCLASSEX wndclass;
    wndclass.cbSize = sizeof(wndclass);
    wndclass.style = CS_HREDRAW | CS_VREDRAW;
    wndclass.lpfnWndProc = WndProc;
    wndclass.cbClsExtra = 0;
    wndclass.cbWndExtra = 0;
    wndclass.hInstance = h2;
    wndclass.hIcon = LoadIcon(NULL, IDI_APPLICATION);
    wndclass.hIconSm = LoadIcon(NULL, IDI_APPLICATION);
    wndclass.hCursor = LoadCursor(NULL, IDC_ARROW);
    wndclass.hbrBackground = reinterpret_cast<HBRUSH>(COLOR_MENU);
    wndclass.lpszClassName = szAppName;
    wndclass.lpszMenuName = NULL;

    /* Register a new window class with Windows */
    if (!RegisterClassEx(&wndclass)) {
        return EXIT_FAILURE;
    }

    /* Create a window based on our new class */

    this->window = CreateWindow(szAppName, "WindowsSettingsThemeListener",
                                       WS_OVERLAPPEDWINDOW,
                                       CW_USEDEFAULT, CW_USEDEFAULT,
                                       CW_USEDEFAULT, CW_USEDEFAULT,
                                       NULL, NULL, h2, NULL);
    /* CreateWindow failed? */
    if( !this->window ) {
        return EXIT_FAILURE;
    }

    /* Show and update our window */
    ShowWindow(this->window, SW_HIDE);
    UpdateWindow(this->window);
    return EXIT_SUCCESS;
}


JNIEXPORT jint JNICALL Java_org_cryptomator_windows_uiappearance_WinAppearance_00024Native_getCurrentTheme (JNIEnv *env, jobject thisObject){
    DWORD data{};
    DWORD dataSize = sizeof(data);
    RegGetValueA(HKEY_CURRENT_USER, R"(Software\Microsoft\Windows\CurrentVersion\Themes\Personalize)", "AppsUseLightTheme", RRF_RT_DWORD, NULL, &data, &dataSize);

    if (data){
        return 1;
    }
    else{
        return 0;
    }
}

JNIEXPORT void JNICALL Java_org_cryptomator_windows_uiappearance_WinAppearance_00024Native_observe(JNIEnv *env, jobject thisObj){
    //JavaVM *vm = nullptr;
    //if ((*env).GetJavaVM(&globalVM) != JNI_OK) {
    //    return;
    //}
    //globalVM->AttachCurrentThread((void **)&env,NULL);
    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0) && observer != NULL) {
        TranslateMessage(&msg); /* for certain keyboard messages */
        DispatchMessage(&msg); /* send message to WndProc */
    }
    //globalVM->DetachCurrentThread();
}

JNIEXPORT jint JNICALL Java_org_cryptomator_windows_uiappearance_WinAppearance_00024Native_prepareObserving(JNIEnv *env, jobject thisObj, jobject listenerObj) {
    JavaVM *vm = nullptr;
    if (env->GetJavaVM(&vm) != JNI_OK) {
        return EXIT_FAILURE;
    }

    jobject listener = env->NewGlobalRef(listenerObj);
    if (listener == NULL) {
        return EXIT_FAILURE;
    }

    observer = new Observer(vm, listener);
    return observer->registerWndProc();
}

JNIEXPORT void JNICALL Java_org_cryptomator_windows_uiappearance_WinAppearance_00024Native_stopObserving(JNIEnv *env, jobject thisObj) {
    env->DeleteGlobalRef(observer->getListener());
    delete observer;
    observer = NULL;
    // TODO: close window (and send a last message if required)
    //CloseWindow(observer.window);
    //DestroyWindow(observer.window);
    // store hwnd as fieldW:: done
    // TODO: cleanup window
}

JNIEXPORT void JNICALL Java_org_cryptomator_windows_uiappearance_WinAppearance_00024Native_setToLight(JNIEnv *env, jobject thisObj) {
    HKEY  hkResult;

    if(RegOpenKeyExA(HKEY_CURRENT_USER, R"(Software\Microsoft\Windows\CurrentVersion\Themes\Personalize)", 0, KEY_SET_VALUE, &hkResult)){
        printf(R"(RegOpenKeyExA(Software\Microsoft\Windows\CurrentVersion\Themes\Personalize) failed)"); //TODO decide if really helpfull / if it appears in cryptomators log
    }
    DWORD value{1};
    RegSetValueExA(hkResult, "AppsUseLightTheme", 0, REG_DWORD, (PBYTE)&value, sizeof(DWORD));
    RegCloseKey(hkResult);
}


JNIEXPORT void JNICALL Java_org_cryptomator_windows_uiappearance_WinAppearance_00024Native_setToDark(JNIEnv *env, jobject thisObj){
    HKEY  hkResult;
    RegOpenKeyExA(HKEY_CURRENT_USER, R"(Software\Microsoft\Windows\CurrentVersion\Themes\Personalize)", 0, KEY_SET_VALUE, &hkResult);
    DWORD value{0};
    RegSetValueExA(hkResult, "AppsUseLightTheme", 0, REG_DWORD, (PBYTE)&value, sizeof(DWORD));
    RegCloseKey(hkResult);
}