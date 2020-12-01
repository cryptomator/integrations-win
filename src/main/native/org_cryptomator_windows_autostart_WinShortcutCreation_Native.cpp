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
#include "org_cryptomator_windows_autostart_WinShortcutCreation_Native.h"

LPCWSTR get_nullterminate_and_release(JNIEnv * env,  jstring name);
HRESULT CreateLink(LPCWSTR lpszPathObj, LPCWSTR lpszPathLink, LPCWSTR lpszDesc);

JNIEXPORT jint JNICALL Java_org_cryptomator_windows_autostart_WinShortcutCreation_00024Native_createShortcut
  (JNIEnv * env, jobject thisObj, jstring target, jstring storage_path, jstring description) {

    //make the utf16 char arrays null terminated
    LPCWSTR link_target = get_nullterminate_and_release(env,target);
    LPCWSTR link_location = get_nullterminate_and_release(env,storage_path);
    LPCWSTR link_description = get_nullterminate_and_release(env,description);

    //initialize
    int hResult = CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);
    if( hResult != S_OK && hResult != S_FALSE && hResult != RPC_E_CHANGED_MODE) {
        //error of the type E_INVALIDARG, E_OUTOFMEMORY or E_UNEXPECTED
        return hResult;
    }
    // compute
    int result = CreateLink(link_target, link_location, link_description);
    // uninitialize
    CoUninitialize();

    //clean up
    delete [] link_target;
    delete [] link_location;
    delete [] link_description;

    return result;
}

LPCWSTR get_nullterminate_and_release(JNIEnv * env,  jstring name){
    const jchar* _array = env->GetStringChars(name, JNI_FALSE);
    const jsize _array_length = env->GetStringLength(name);

    int length;
    if ( _array[_array_length - 1] != '\0') {
        length = _array_length + 1;
    } else {
        length = _array_length;
    }

    WCHAR * interim =  new WCHAR[length];
    wcscpy(interim, (wchar_t *) _array);

    if ( _array[_array_length - 1] != '\0') {
        interim[length-1] = '\0';
    }

    env->ReleaseStringChars(name, _array);

    return interim;
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
// S_OK (0x000000000) - If anything works fine
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