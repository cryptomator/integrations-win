#include <jni.h>
#include "org_cryptomator_windows_keychain_WinHello_Native.h"
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Security.Credentials.h>
#include <winrt/Windows.Security.Cryptography.h>
#include <winrt/Windows.Security.Cryptography.Core.h>
#include <winrt/Windows.Storage.Streams.h>
#include <windows.h>
#include <thread>
#include <chrono>
#include <string>
#include <vector>
#include <stdexcept>
#include <iostream>
#include <atomic>

using namespace winrt;
using namespace Windows::Security::Credentials;
using namespace Windows::Security::Cryptography;
using namespace Windows::Security::Cryptography::Core;
using namespace Windows::Storage::Streams;

const std::wstring s_winHelloKeyName{L"cryptomator_winhello"};
static std::atomic<int> g_promptFocusCount{0};
static std::once_flag runtimeInitFlag;
static IBuffer info = CryptographicBuffer::ConvertStringToBinary(L"EncryptionKey", BinaryStringEncoding::Utf8);

// Helper methods for convertion
std::vector<uint8_t> jbyteArrayToVector(JNIEnv* env, jbyteArray array) {
  if (array == nullptr) {
    return std::vector<uint8_t>();
  }
  jsize length = env->GetArrayLength(array);
  std::vector<uint8_t> result(length);
  env->GetByteArrayRegion(array, 0, length, reinterpret_cast<jbyte*>(result.data()));
  return result;
}

jbyteArray vectorToJbyteArray(JNIEnv* env, const std::vector<uint8_t>& vec) {
  jbyteArray array = env->NewByteArray(vec.size());
  env->SetByteArrayRegion(array, 0, vec.size(), reinterpret_cast<const jbyte*>(vec.data()));
  return array;
}

IBuffer vectorToIBuffer(const std::vector<uint8_t>& vec) {
  DataWriter writer;
  writer.WriteBytes(array_view<const uint8_t>(vec));
  return writer.DetachBuffer();
}

std::vector<uint8_t> iBufferToVector(const IBuffer& buffer) {
  auto reader = DataReader::FromBuffer(buffer);
  if (!buffer || reader.UnconsumedBufferLength() == 0) {
    return {};
  }
  std::vector<uint8_t> result(reader.UnconsumedBufferLength());
  reader.ReadBytes(array_view<uint8_t>(result));
  return result;
}

// Bring Windows Hello pop-up to the front
void queueSecurityPromptFocus(int delay = 500) {
    std::thread([delay]() {
        while (g_promptFocusCount.load() <= 3) {
            std::this_thread::sleep_for(std::chrono::milliseconds(delay));

            auto hWnd = ::FindWindowA("Credential Dialog Xaml Host", nullptr);

            if (hWnd) {
                ::SetForegroundWindow(hWnd);
                g_promptFocusCount.store(0); // Reset the counter
                break;
            } else if (g_promptFocusCount.fetch_add(1) + 1 > 3) {
                g_promptFocusCount.store(0);
                break;
            }
        }
    }).detach();
}

void InitializeWindowsRuntime() {
    std::call_once(runtimeInitFlag, []() {
        winrt::init_apartment();
    });
}

IBuffer DeriveKeyUsingHKDF(const IBuffer& inputData, const IBuffer& salt, uint32_t keySizeInBytes, const IBuffer& info) {
  auto macProvider = MacAlgorithmProvider::OpenAlgorithm(L"HMAC_SHA256");

  // HKDF-extract
  auto hmacKey = macProvider.CreateKey(salt);
  auto pseudorandomKey = CryptographicEngine::Sign(hmacKey, inputData);

  // HKDF-expand
  hmacKey = macProvider.CreateKey(pseudorandomKey); // Re-create HMAC with pseudorandomKey as key
  auto keyMaterial = CryptographicEngine::Sign(hmacKey, info);

  // Truncate key to the desired size if needed (SHA256 output is 32 bytes by default)
  uint32_t actualKeySize = keyMaterial.Length();
  if (actualKeySize > keySizeInBytes) {
    com_array<uint8_t> keyBytes;
    CryptographicBuffer::CopyToByteArray(keyMaterial, keyBytes);
    keyBytes = com_array<uint8_t>(keyBytes.begin(), keyBytes.begin() + keySizeInBytes);
    keyMaterial = CryptographicBuffer::CreateFromByteArray(keyBytes);
  }

  return keyMaterial;
}

bool deriveEncryptionKey(const std::vector<uint8_t>& challenge, IBuffer& key){
  auto challengeBuffer = CryptographicBuffer::CreateFromByteArray(
        array_view<const uint8_t>(challenge.data(), challenge.size()));

  try {
    // The first time this is used a key-pair will be generated using the common name
    auto result = KeyCredentialManager::RequestCreateAsync(s_winHelloKeyName,
          KeyCredentialCreationOption::FailIfExists).get();

    if (result.Status() == KeyCredentialStatus::CredentialAlreadyExists) {
      result = KeyCredentialManager::OpenAsync(s_winHelloKeyName).get();
    } else if (result.Status() != KeyCredentialStatus::Success) {
      std::cerr << "Failed to retrieve Windows Hello credential." << std::endl;
      return false;
    }

    const auto signature = result.Credential().RequestSignAsync(challengeBuffer).get();
    if (signature.Status() != KeyCredentialStatus::Success) {
      if (signature.Status() != KeyCredentialStatus::UserCanceled) {
        std::cerr << "Failed to sign challenge using Windows Hello." << std::endl;
      }
      return false;
    }

    // Derive the encryption/decryption key using HKDF
    const auto response = signature.Result();
    key = DeriveKeyUsingHKDF(response, challengeBuffer, 32, info); // needs to be 32 bytes for SHA256
    return true;

  } catch (winrt::hresult_error const& ex) {
    std::cerr << winrt::to_string(ex.message()) << std::endl;
    return false;
  }
}

// Encrypts data using Windows Hello KeyCredentialManager API and derived key from signed challenge
jbyteArray JNICALL Java_org_cryptomator_windows_keychain_WinHello_00024Native_setEncryptionKey
(JNIEnv* env, jobject obj, jbyteArray cleartext, jbyteArray challenge) {
  queueSecurityPromptFocus();
  try {
    // Convert Java byte arrays to C++ vectors
    std::vector<uint8_t> cleartextVec = jbyteArrayToVector(env, cleartext);
    std::vector<uint8_t> challengeVec = jbyteArrayToVector(env, challenge);

    InitializeWindowsRuntime();

    // Take the random challenge and sign it by Windows Hello
    // to create the key. The challenge is also used as the IV.
    IBuffer key;
    if (!deriveEncryptionKey(challengeVec, key)) {
      throw std::runtime_error("Failed to generate the encryption key with the Windows Hello credential.");
    }

    auto algorithmName = SymmetricAlgorithmNames::AesGcm();
    auto aesProvider = SymmetricKeyAlgorithmProvider::OpenAlgorithm(algorithmName);
    auto keyMaterial = CryptographicBuffer::CreateFromByteArray(array_view<const uint8_t>(key.data(), key.Length()));
    auto aesKey = aesProvider.CreateSymmetricKey(keyMaterial);
    auto dataBuffer = CryptographicBuffer::CreateFromByteArray(array_view<const uint8_t>(cleartextVec.data(), cleartextVec.size()));
    auto encryptedBuffer = CryptographicEngine::Encrypt(aesKey, dataBuffer, keyMaterial);

    std::fill(cleartextVec.begin(), cleartextVec.end(), 0);
    std::fill(challengeVec.begin(), challengeVec.end(), 0);

    return vectorToJbyteArray(env, iBufferToVector(encryptedBuffer));

  } catch (winrt::hresult_error const& hre) {
    HRESULT hr = hre.code();
    winrt::hstring message = hre.message();
    std::cerr << "Error: " << winrt::to_string(message) << " (HRESULT: 0x" << std::hex << hr << ")" << std::endl;
    auto byteArray = env->NewByteArray(0);
    return byteArray;
  } catch (const std::exception& e) {
    std::cerr << "Warning: " << e.what() << std::endl;
    auto byteArray = env->NewByteArray(0);
    return byteArray;
  } catch (...) {
    std::cerr << "Caught an unknown exception" << std::endl;
    auto byteArray = env->NewByteArray(0);
    return byteArray;
  }
}

// Decrypts data using Windows Hello KeyCredentialManager API and derived key from signed challenge
jbyteArray JNICALL Java_org_cryptomator_windows_keychain_WinHello_00024Native_getEncryptionKey
(JNIEnv* env, jobject obj, jbyteArray ciphertext, jbyteArray challenge) {
  queueSecurityPromptFocus();
  try {
    // Convert Java byte arrays to C++ vectors
    std::vector<uint8_t> ciphertextVec = jbyteArrayToVector(env, ciphertext);
    std::vector<uint8_t> challengeVec = jbyteArrayToVector(env, challenge);

    // Take the random challenge and sign it by Windows Hello
    // to create the key. The challenge is also used as the IV.
    IBuffer key;
    if (!deriveEncryptionKey(challengeVec, key)) {
      throw std::runtime_error("Failed to generate the encryption key with the Windows Hello credential.");
    }

    auto algorithmName = SymmetricAlgorithmNames::AesGcm();
    auto aesProvider = SymmetricKeyAlgorithmProvider::OpenAlgorithm(algorithmName);
    auto keyMaterial = CryptographicBuffer::CreateFromByteArray(array_view<const uint8_t>(key.data(), key.Length()));
    auto aesKey = aesProvider.CreateSymmetricKey(keyMaterial);
    auto dataBuffer = CryptographicBuffer::CreateFromByteArray(array_view<const uint8_t>(ciphertextVec.data(), ciphertextVec.size()));
    auto decryptedBuffer = CryptographicEngine::Decrypt(aesKey, dataBuffer, keyMaterial);

    std::fill(ciphertextVec.begin(), ciphertextVec.end(), 0);
    std::fill(challengeVec.begin(), challengeVec.end(), 0);

    return vectorToJbyteArray(env, iBufferToVector(decryptedBuffer));

  } catch (winrt::hresult_error const& hre) {
    HRESULT hr = hre.code();
    winrt::hstring message = hre.message();
    std::cerr << "Error: " << winrt::to_string(message) << " (HRESULT: 0x" << std::hex << hr << ")" << std::endl;
    auto byteArray = env->NewByteArray(0);
    return byteArray;
  } catch (const std::exception& e) {
    std::cerr << "Warning: " << e.what() << std::endl;
    auto byteArray = env->NewByteArray(0);
    return byteArray;
  } catch (...) {
    std::cerr << "Caught an unknown exception" << std::endl;
    auto byteArray = env->NewByteArray(0);
    return byteArray;
  }
}
