//
//  Created by Martin Beyer on 01.11.2020.
//  Copyright © 2020 Cryptomator. All rights reserved.
//

#include "org_cryptomator_windows_uiappearance_WinAppearance_Native.h"
#include <jni.h>
#include <windows.h>
#include <winreg.h>
#include "SKYAppearanceObserver.h"

//JNIEnv *cur_env; /* pointer to native method interface */
//jobject listener;

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

class Observer{
public:
    Observer(JNIEnv *cur_env, jobject listener);

    JNIEnv *cur_env;
    jobject listener;
    Observer();
    int calljvm();
    void registerWndProc();

    static LRESULT WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);
    LRESULT realWndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);
};

Observer::Observer(JNIEnv *cur_env, jobject listener){
    this->cur_env = cur_env;
    this->listener = listener;
}

LRESULT CALLBACK Observer::WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    //get the current observer from the Window
    Observer* me = reinterpret_cast<Observer*>(GetWindowLongPtr(hwnd, GWLP_USERDATA));

    //this workaround is needed, as wndclass.lpfnWndProc needs to be a static class, but I need to use variables of an object to call calljvm()
    if (me) {
        return me->realWndProc(hwnd, msg, wParam, lParam);
    }
    return DefWindowProc(hwnd, msg, wParam, lParam);
}

LRESULT CALLBACK Observer::realWndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    WCHAR *plParam = (LPWSTR) lParam;
    switch (msg) {
        case WM_SETTINGCHANGE :
            if (plParam) {
                //Only call the notify methode if the correct setting changed
                if (lstrcmp(reinterpret_cast<LPCSTR>(plParam), TEXT("ImmersiveColorSet")) == 0) {
                    //MessageBox(NULL, TEXT("got correct message"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove
                    this->calljvm();
                }
                break;
            }
        default:
            return DefWindowProc(hwnd, msg, wParam, lParam);
    }
    return EXIT_SUCCESS;
}
int Observer::calljvm() { //notify
    JavaVM *vm = nullptr;
    cur_env->GetJavaVM(&vm);
    (*vm).AttachCurrentThread((void **)&cur_env,NULL);
    jclass listenerClass = cur_env->GetObjectClass(listener);
    jmethodID listenerMethodID = cur_env->GetMethodID(listenerClass, "systemAppearanceChanged", "()V");
    cur_env->CallVoidMethod(listenerClass, listenerMethodID);
    (*vm).DetachCurrentThread();
    return EXIT_SUCCESS;
}


void Observer::registerWndProc() {
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
        MessageBox(NULL, TEXT("Registering class failed!"), TEXT("Error!"), MB_ICONEXCLAMATION | MB_OK);
        return;
    }

    /* Create a window based on our new class */
    HWND hwnd;
    hwnd = CreateWindow(szAppName, "WindowsSettingsThemeListener",
                        WS_OVERLAPPEDWINDOW,
                        CW_USEDEFAULT, CW_USEDEFAULT,
                        CW_USEDEFAULT, CW_USEDEFAULT,
                        NULL, NULL, h2, NULL);
    /* CreateWindow failed? */
    if( !hwnd ) {
        MessageBox(NULL, TEXT("Window creation failed!"), TEXT("Error!"), MB_ICONEXCLAMATION | MB_OK);
        return;
    }
    //To store the current object in the window (and recieve it later in the static WndProc)
    SetWindowLongPtrA(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(this));

    /* Show and update our window */
    ShowWindow(hwnd, 1); /* 0 = SW_HIDE */ //TODO hide me
    UpdateWindow(hwnd);
    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0) ) {
        TranslateMessage(&msg); /* for certain keyboard messages */
        DispatchMessage(&msg); /* send message to WndProc */
    }
}

JNIEXPORT jlong JNICALL Java_org_cryptomator_windows_uiappearance_WinAppearance_00024Native_registerObserverWithListener(JNIEnv *env, jobject thisObj, jobject listenerObj) {
    JavaVM *vm = nullptr;
    if ((*env).GetJavaVM(&vm) != JNI_OK) {
        return 0;
    }
    //listener = (*env).NewGlobalRef(listenerObj);
    //if (listener == NULL) {
    //    return 0;
    //}
    Observer observer(env, listenerObj);
    observer.registerWndProc();
    //Program blocks / waits here. Nothing is returned to java. Java waits.
    jlong observerPtr = reinterpret_cast<jlong>(&observer);
    return observerPtr;
}

JNIEXPORT void JNICALL Java_org_cryptomator_windows_uiappearance_WinAppearance_00024Native_setToLight(JNIEnv *env, jobject thisObj) {
    HKEY  hkResult;

    if(RegOpenKeyExA(HKEY_CURRENT_USER, R"(Software\Microsoft\Windows\CurrentVersion\Themes\Personalize)", 0, KEY_SET_VALUE, &hkResult)){
        printf(R"(RegOpenKeyExA(Software\Microsoft\Windows\CurrentVersion\Themes\Personalize) failed)"); //TODO decide if really helpfull / if it appears in cryptomators log
    }
    DWORD value{1};
    RegSetValueExA(hkResult, "AppsUseLightTheme", 0, REG_DWORD, (PBYTE)&value, sizeof(DWORD));
    //TODO need to close key?
}


JNIEXPORT void JNICALL Java_org_cryptomator_windows_uiappearance_WinAppearance_00024Native_setToDark(JNIEnv *env, jobject thisObj){
    HKEY  hkResult;
    RegOpenKeyExA(HKEY_CURRENT_USER, R"(Software\Microsoft\Windows\CurrentVersion\Themes\Personalize)", 0, KEY_SET_VALUE, &hkResult);
    DWORD value{0};
    RegSetValueExA(hkResult, "AppsUseLightTheme", 0, REG_DWORD, (PBYTE)&value, sizeof(DWORD));
    //TODO need to close key?
}