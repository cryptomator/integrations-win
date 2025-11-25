#include <iostream>
#include <windows.h>
#include <string_view>
#include <winrt/Windows.UI.Notifications.h>
#include <winrt/Windows.Data.Xml.Dom.h>
#include "org_cryptomator_windows_notify_WinToasts_Native.h"

using namespace winrt;
using namespace Windows::Data::Xml::Dom;
using namespace Windows::UI::Notifications;

JNIEXPORT jint JNICALL Java_org_cryptomator_windows_notify_WinToasts_00024Native_sendToastNotification
        (JNIEnv* env, jobject obj, jbyteArray jAumid, jbyteArray jToastXml) {
    LPCWSTR aumid = (LPCWSTR)env->GetByteArrayElements(jAumid, NULL);
    LPCWSTR toastXmlString = (LPCWSTR)env->GetByteArrayElements(jToastXml, NULL);
    HRESULT result = 0;
    winrt::init_apartment();
        try {
            std::wstring wAUMID{ aumid };
            std::wstring wToastXmlString{ toastXmlString };
            XmlDocument toastXml;
            toastXml.LoadXml(wToastXmlString);
            ToastNotification toast{ toastXml };
            toast.ExpiresOnReboot(true);
            ToastNotifier notifier{ ToastNotificationManager::CreateToastNotifier(wAUMID) };
            notifier.Show(toast);
        }
        catch (winrt::hresult_error const& e) {
            std::wcout << std::wstring_view( e.message()) << std::endl;
            result = e.code();
        }
        env->ReleaseByteArrayElements(jAumid, (jbyte*) aumid, JNI_ABORT);
        env->ReleaseByteArrayElements(jToastXml, (jbyte*) toastXmlString, JNI_ABORT);
        return result;
}