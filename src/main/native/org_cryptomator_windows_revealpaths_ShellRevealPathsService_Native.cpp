#include <jni.h>
#include <windows.h>
#include <shtypes.h>
#include <shobjidl.h>
#include <shlobj.h>
#include <strsafe.h>
#include "org_cryptomator_windows_revealpaths_ShellRevealPathsService_Native.h"

HRESULT OpenFolderAndSelectItem(LPCWSTR directory, LPCWSTR * childs, unsigned int child_count);

JNIEXPORT jint JNICALL Java_org_cryptomator_windows_revealpaths_ShellRevealPathsService_00024Native_reveal (JNIEnv * env, jobject thisObj, jbyteArray jDirectory, jobjectArray jChilds)
{
    //return code
    HRESULT hres;

    //get string arguments
    LPCWSTR directory = (LPCWSTR) env->GetByteArrayElements(jDirectory, NULL);
    long child_count = env->GetArrayLength(jChilds);
    LPCWSTR childs [child_count];
    int i=0;
    for(i=0;i<child_count;i++) {
        childs[i] = (LPCWSTR) env->GetByteArrayElements((jbyteArray) env->GetObjectArrayElement(jChilds,i), NULL);
    }


    //initialize
    int initResult = CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);
    if( initResult == S_OK || initResult == S_FALSE || initResult == RPC_E_CHANGED_MODE) {
        // compute
        hres = OpenFolderAndSelectItem(directory, childs, child_count);
        // uninitialize
        CoUninitialize();
    } else {
        hres = initResult; //error of the type E_INVALIDARG, E_OUTOFMEMORY or E_UNEXPECTED
    }

    //clean up
    env->ReleaseByteArrayElements(jDirectory, (jbyte *) directory, JNI_ABORT);
    for(i=0;i<child_count;i++) {
        env-> ReleaseByteArrayElements((jbyteArray) env->GetObjectArrayElement(jChilds,i), (jbyte *) childs[i], JNI_ABORT);
    }
    return hres;
}



HRESULT OpenFolderAndSelectItem(LPCWSTR directory, LPCWSTR * childs, unsigned int child_count)
{
    HRESULT hres;

    // Parse the paths into PIDLs
    PIDLIST_ABSOLUTE directory_pidl;
    hres = SHParseDisplayName(directory, NULL, &directory_pidl, 0, NULL);
    if( FAILED(hres) ) {
        return hres;
    }

    PIDLIST_ABSOLUTE child_pidls [child_count];
    unsigned int i=0;
    for(i=0;i<child_count;i++) {
        hres = SHParseDisplayName(childs[i], NULL, &(child_pidls[i]), 0, NULL);
        if( FAILED(hres) ) {
            // Use the task allocator to free already acquired pidls
            unsigned int j=0;
            for(j=0;j<i;j++) {
                CoTaskMemFree(&child_pidls[j]);
            }
            CoTaskMemFree(directory_pidl);
            return hres;
        }
    }
    PCIDLIST_ABSOLUTE_ARRAY child_pidls_const = (PCIDLIST_ABSOLUTE_ARRAY) child_pidls;

    // Open Explorer and select the files
    hres = SHOpenFolderAndSelectItems(directory_pidl, child_count, child_pidls_const, 0);

    // Use the task allocator to free pidls
    i=0;
    for(i=0;i<child_count;i++) {
        CoTaskMemFree(&child_pidls[i]);
    }
    CoTaskMemFree(directory_pidl);
    return hres;
}
