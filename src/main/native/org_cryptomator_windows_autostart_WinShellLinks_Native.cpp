#ifndef UNICODE
    #define UNICODE
#endif

#include <jni.h>
#include <windows.h>
#include <winnls.h>
#include <shobjidl.h>
#include <objbase.h>
#include <objidl.h>
#include <shlguid.h>
#include <wchar.h>
#include "org_cryptomator_windows_autostart_WinShellLinks_Native.h"

HRESULT CreateLink(LPCWSTR lpszPathObj, LPCWSTR lpszPathLink, LPCWSTR lpszDesc);

JNIEXPORT jint JNICALL Java_org_cryptomator_windows_autostart_WinShellLinks_00024Native_createShortcut
  (JNIEnv * env, jobject thisObj, jbyteArray target, jbyteArray storage_path, jbyteArray description) {

    //make the utf16 char arrays null terminated
    LPCWSTR link_target = (LPCWSTR) env->GetByteArrayElements(target, JNI_FALSE);
    LPCWSTR link_location = (LPCWSTR) env->GetByteArrayElements(storage_path, JNI_FALSE);
    LPCWSTR link_description = (LPCWSTR) env->GetByteArrayElements(description, JNI_FALSE);

    //initialize
    int initResult = CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);
    if( initResult != S_OK && initResult != S_FALSE && initResult != RPC_E_CHANGED_MODE) {
        //error of the type E_INVALIDARG, E_OUTOFMEMORY or E_UNEXPECTED
        return initResult;
    }
    // compute
    int executeResult = CreateLink(link_target, link_location, link_description);
    // uninitialize
    CoUninitialize();

    //clean up
    env->ReleaseByteArrayElements(target, (jbyte *) link_target, JNI_ABORT);
    env->ReleaseByteArrayElements(storage_path, (jbyte *) link_location, JNI_ABORT);
    env->ReleaseByteArrayElements(description, (jbyte *) link_description, JNI_ABORT);

    return executeResult;
}

// CreateLink - Uses the Shell's IShellLink and IPersistFile interfaces
//              to create and store a shortcut to the specified object.
//
// Returns the result of calling the member functions of the interfaces.
//
// Parameters:
// lpszPathObj  - Address of a buffer that contains the path of the object,
//                including the file name.
// lpszPathLink - Address of a buffer that contains the path where the
//                Shell link is to be stored, including the file name.
// lpszDesc     - Address of a buffer that contains a description of the
//                Shell link, stored in the Comment field of the link
//                properties.
//
// Returns:
// S_OK (0x000000000) - If everything works fine
// ErrorCode          - else
//
HRESULT CreateLink(LPCWSTR lpszPathObj, LPCWSTR lpszPathLink, LPCWSTR lpszDesc) {
    HRESULT hres;
    IShellLinkW* psl;

    // Get a pointer to the IShellLink interface. It is assumed that CoInitialize
    // has already been called.
    hres = CoCreateInstance(CLSID_ShellLink, NULL, CLSCTX_INPROC_SERVER, IID_IShellLinkW, (LPVOID*)&psl);
    if (SUCCEEDED(hres))
    {
        IPersistFile* ppf;

        // Set the path to the shortcut target and add the description.
        psl->SetPath(lpszPathObj);
        psl->SetDescription(lpszDesc);

        // Query IShellLink for the IPersistFile interface, used for saving the
        // shortcut in persistent storage.
        hres = psl->QueryInterface(IID_IPersistFile, (LPVOID*)&ppf);

        if (SUCCEEDED(hres))
        {
            // Add code here to check return value from MultiByteWideChar
            // for success.

            // Save the link by calling IPersistFile::Save.
            hres = ppf->Save(lpszPathLink, TRUE);
            ppf->Release();
        }
        psl->Release();
    }
return hres;
}
