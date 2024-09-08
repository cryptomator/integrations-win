#include <jni.h>
#include <windows.h>
#include <wincrypt.h>
#include "org_cryptomator_windows_keychain_WinHello_Native.h"

JNIEXPORT jbyteArray JNICALL Java_org_cryptomator_windows_keychain_WinHello_00024Native_setEncryptionKey(JNIEnv *env, jobject thisObj, jbyteArray cleartext, jbyteArray salt) {
  DATA_BLOB cleartextBlob;
  cleartextBlob.pbData = (BYTE *) env->GetByteArrayElements(cleartext, JNI_FALSE);
  cleartextBlob.cbData = (DWORD) env->GetArrayLength(cleartext);
  DATA_BLOB saltBlob;
  saltBlob.pbData = (BYTE *) env->GetByteArrayElements(salt, JNI_FALSE);
  saltBlob.cbData = (DWORD) env->GetArrayLength(salt);

  DATA_BLOB ciphertextBlob;
  BOOL success = CryptProtectData(&cleartextBlob, NULL, &saltBlob, NULL, NULL, 0, &ciphertextBlob);

  env->ReleaseByteArrayElements(cleartext, (jbyte*) cleartextBlob.pbData, JNI_ABORT);
  env->ReleaseByteArrayElements(salt, (jbyte*) saltBlob.pbData, JNI_ABORT);

  if (success) {
    jbyteArray ciphertext = env->NewByteArray(ciphertextBlob.cbData);
    env->SetByteArrayRegion(ciphertext, 0, ciphertextBlob.cbData, (jbyte *) ciphertextBlob.pbData);
    return ciphertext;
  } else {
    return NULL;
  }
}

JNIEXPORT jbyteArray JNICALL Java_org_cryptomator_windows_keychain_WinHello_00024Native_getEncryptionKey(JNIEnv *env, jobject thisObj, jbyteArray ciphertext, jbyteArray salt) {
  DATA_BLOB ciphertextBlob;
  ciphertextBlob.pbData = (BYTE *) env->GetByteArrayElements(ciphertext, JNI_FALSE);
  ciphertextBlob.cbData = (DWORD) env->GetArrayLength(ciphertext);
  DATA_BLOB saltBlob;
  saltBlob.pbData = (BYTE *) env->GetByteArrayElements(salt, JNI_FALSE);
  saltBlob.cbData = (DWORD) env->GetArrayLength(salt);

  DATA_BLOB cleartextBlob;
  BOOL success = CryptUnprotectData(&ciphertextBlob, NULL, &saltBlob, NULL, NULL, 0, &cleartextBlob);

  env->ReleaseByteArrayElements(ciphertext, (jbyte*) ciphertextBlob.pbData, JNI_ABORT);
  env->ReleaseByteArrayElements(salt, (jbyte*) saltBlob.pbData, JNI_ABORT);

  if (success) {
    jbyteArray cleartext = env->NewByteArray(cleartextBlob.cbData);
    env->SetByteArrayRegion(cleartext, 0, cleartextBlob.cbData, (jbyte *) cleartextBlob.pbData);
    return cleartext;
  } else {
    return NULL;
  }
}