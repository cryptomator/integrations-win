//
//  Created by Martin Beyer on 01.11.2020.
//  Copyright © 2020 Cryptomator. All rights reserved.
//

#include "org_cryptomator_windows_uiappearance_WinAppearance_Native.h"
#include <jni.h>
#include <windows.h>
#include <winreg.h>
#include "SKYAppearanceObserver.h"

jobject j_listener;
JavaVM *globalVM = nullptr;

//JNIEnv *cur_env; /* pointer to native method interface */
//jobject listener;

class Observer{
public:
    JNIEnv *cur_env;
    jobject listener;
    //JavaVM *jvm;
    HWND observer_hwnd;

    //Observer()
    void initialize(JNIEnv *cur_env, jobject listener);
    int calljvm();
    int registerWndProc();

    static LRESULT WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);
    LRESULT realWndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);
};

void Observer::initialize(JNIEnv *cur_env, jobject listener) {
    this->cur_env = cur_env;
    this->listener = listener;
    //cur_env->GetJavaVM(&jvm);
    //MessageBox(NULL, TEXT("In init attaching"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove
    //(*globalVM).AttachCurrentThread((void **)&cur_env,NULL); //TODO globalVM
    //MessageBox(NULL, TEXT("In init: attached"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove


    //this->vm = pVm;
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
        case WM_SETTINGCHANGE:
            if (plParam) {
                //Only call the notify methode if the correct setting changed
                if (lstrcmp(reinterpret_cast<LPCSTR>(plParam), TEXT("ImmersiveColorSet")) == 0) {
                    MessageBox(NULL, TEXT("theme changed"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove
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
    MessageBox(NULL, TEXT("In callJVM"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove
    //globalVM->GetEnv()
    /*
    //JavaVM *vm = nullptr;
    if (cur_env->GetJavaVM(&vm) != JNI_OK){ //crashes
       MessageBox(NULL, TEXT("JNI Is not OK"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove
       return EXIT_FAILURE;
    } else {   MessageBox(NULL, TEXT("JNI Is OK"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove
    };
     */
    if (!globalVM){
        MessageBox(NULL, TEXT("VM is empty!!"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove
    }
    (*globalVM).AttachCurrentThread((void **)&cur_env,NULL);
    MessageBox(NULL, TEXT("called AttachCurrentThread"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove

    if (listener == NULL) {
        MessageBox(NULL, TEXT("listener == NULL"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove
        return EXIT_FAILURE;
    }
    MessageBox(NULL, TEXT("listener != NULL, FINE"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove

    jclass listenerClass = cur_env->GetObjectClass(listener);
    jmethodID listenerMethodID = cur_env->GetMethodID(listenerClass, "systemAppearanceChanged", "()V");

    if (!listenerMethodID || !listenerClass){
        MessageBox(NULL, TEXT("method and or class null"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove
        return EXIT_FAILURE;
    }
    MessageBox(NULL, TEXT("method and or class found"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove


    if(cur_env->ExceptionCheck()) {
        cur_env->ExceptionDescribe();
        cur_env->ExceptionClear();
    }
    MessageBox(NULL, TEXT("calling..."), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove

    cur_env->CallVoidMethod(listener, listenerMethodID);
    //MessageBox(NULL, TEXT("CallVoidMethod called"), TEXT("OK!"), MB_ICONEXCLAMATION | MB_OK); //TODO remove

    (*globalVM).DetachCurrentThread();
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
        //MessageBox(NULL, TEXT("Registering class failed!"), TEXT("Error!"), MB_ICONEXCLAMATION | MB_OK);
        return EXIT_FAILURE;
    }

    /* Create a window based on our new class */

    this->observer_hwnd = CreateWindow(szAppName, "WindowsSettingsThemeListener",
                                       WS_OVERLAPPEDWINDOW,
                                       CW_USEDEFAULT, CW_USEDEFAULT,
                                       CW_USEDEFAULT, CW_USEDEFAULT,
                                       NULL, NULL, h2, NULL);
    /* CreateWindow failed? */
    if( !this->observer_hwnd ) {
        //MessageBox(NULL, TEXT("Window creation failed!"), TEXT("Error!"), MB_ICONEXCLAMATION | MB_OK);
        return EXIT_FAILURE;
    }
    //To store the current object in the window (and recieve it later in the static WndProc)
    SetWindowLongPtrA(this->observer_hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(this));

    /* Show and update our window */
    ShowWindow(this->observer_hwnd, 1); /* 0 = SW_HIDE */ //TODO hide me
    UpdateWindow(this->observer_hwnd);
    return EXIT_SUCCESS;
}

WINBOOL isObserving;

//Observer observer;


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
    if ((*env).GetJavaVM(&globalVM) != JNI_OK) {
        return;
    }
    globalVM->AttachCurrentThread((void **)&env,NULL);
    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0) && isObserving ) { // TOD/O: add additional "isObserving" flag
        TranslateMessage(&msg); /* for certain keyboard messages */
        DispatchMessage(&msg); /* send message to WndProc */
    }
    globalVM->DetachCurrentThread();
}

JNIEXPORT jint JNICALL Java_org_cryptomator_windows_uiappearance_WinAppearance_00024Native_prepareObserving(JNIEnv *env, jobject thisObj, jobject listenerObj) {
    JavaVM *vm = nullptr;
    if ((*env).GetJavaVM(&vm) != JNI_OK) {
        return EXIT_FAILURE;
    }
    //j_listener = listenerObj;
    Observer observer;
    (*vm).AttachCurrentThread((void **)&env,NULL);
    observer.initialize(env, listenerObj);
    isObserving = TRUE;
    vm->DetachCurrentThread();
    return observer.registerWndProc();
}

JNIEXPORT void JNICALL Java_org_cryptomator_windows_uiappearance_WinAppearance_00024Native_stopObserving(JNIEnv *env, jobject thisObj) {
    // TODO: stop GetMessage-loop
    isObserving = FALSE;
    // TODO: close window (and send a last message if required)
    //CloseWindow(observer.observer_hwnd);
    //DestroyWindow(observer.observer_hwnd);
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