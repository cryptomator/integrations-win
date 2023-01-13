#ifndef UNICODE
    #define UNICODE
#endif

#include <jni.h>
#include <windows.h>
#include <winnls.h>
#include <shlobj.h>
#include <strsafe.h>
#include <shobjidl.h>
#include <objbase.h>
#include <objidl.h>
#include <shlguid.h>
#include "org_cryptomator_windows_autostart_WinShellLinks_Native.h"

HRESULT CreateLink(LPCWSTR lpszPathObj, LPCWSTR lpszPathLink, LPCWSTR lpszDesc);
//GUID of the Startup Folder: {B97D20BB-F46A-4C97-BA10-5E3608430854}
const GUID StartupFolderGUID = {
    0xB97D20BBu,0xF46Au,0x4C97u, 0xBAu, 0x10u, 0x5Eu,0x36u, 0x08u, 0x43u, 0x08u, 0x54u
};


JNIEXPORT jstring JNICALL Java_org_cryptomator_windows_autostart_WinShellLinks_00024Native_createAndGetStartupFolderPath
  (JNIEnv * env, jobject thisObj) {

    HRESULT hres;
    PWSTR startupfolder_path;
    hres = SHGetKnownFolderPath(StartupFolderGUID, KF_FLAG_CREATE | KF_FLAG_INIT, NULL, &startupfolder_path); //returns C:\Home, not C:\Home\ (NO trailing slash)
    if(FAILED(hres)) {
        return NULL;
    }

    size_t length_startupfolder_path;
    hres = StringCbLengthW(startupfolder_path, STRSAFE_MAX_CCH * sizeof(TCHAR), &length_startupfolder_path);
    if(FAILED(hres)) {
        CoTaskMemFree(startupfolder_path);
        return NULL;
    }
    jstring s = env->NewString( (jchar *) startupfolder_path, length_startupfolder_path/sizeof(WCHAR) );
    CoTaskMemFree(startupfolder_path);
    return s;
};


JNIEXPORT jint JNICALL Java_org_cryptomator_windows_autostart_WinShellLinks_00024Native_createShortcut
  (JNIEnv * env, jobject thisObj, jbyteArray target, jbyteArray storage_path, jbyteArray description) {
    // result to be returned
    HRESULT hres;

    //get the arguments from environment (byte arrays with utf-16LE encodings)
    LPCWSTR link_target = (LPCWSTR) env->GetByteArrayElements(target, NULL);
    LPCWSTR link_location = (LPCWSTR) env->GetByteArrayElements(storage_path, NULL);
    LPCWSTR link_description = (LPCWSTR) env->GetByteArrayElements(description, NULL);

    //initialize
    int initResult = CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);
    if( initResult == S_OK || initResult == S_FALSE || initResult == RPC_E_CHANGED_MODE) {
        // compute
        hres = CreateLink(link_target, link_location, link_description);
        // uninitialize
        CoUninitialize();
    } else {
        hres = initResult; //error of the type E_INVALIDARG, E_OUTOFMEMORY or E_UNEXPECTED
    }

    //clean up
    env->ReleaseByteArrayElements(target, (jbyte *) link_target, JNI_ABORT);
    env->ReleaseByteArrayElements(storage_path, (jbyte *) link_location, JNI_ABORT);
    env->ReleaseByteArrayElements(description, (jbyte *) link_description, JNI_ABORT);

    return hres;
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
// HRESULT ErrorCode  - else
//
HRESULT CreateLink(LPCWSTR lpszPathObj, LPCWSTR lpszPathLink, LPCWSTR lpszDesc) {
    HRESULT hres;
    IShellLink* psl;

    // Get a pointer to the IShellLink interface. It is assumed that CoInitialize
    // has already been called.
    hres = CoCreateInstance(CLSID_ShellLink, NULL, CLSCTX_INPROC_SERVER, IID_IShellLink, (LPVOID*)&psl);
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
            // Save the link by calling IPersistFile::Save.
            hres = ppf->Save(lpszPathLink, TRUE);
            ppf->Release();
        }
        psl->Release();
    }
    return hres;
}