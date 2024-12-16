#include <jni.h>
#include "org_cryptomator_windows_keychain_WindowsHello_Native.h"
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

const std::wstring s_windowsHelloKeyName{L"cryptomator_windowshello"};
static std::atomic<int> g_promptFocusCount{0};
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
  if (array == nullptr) {
    return nullptr;
  }
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

IBuffer DeriveKeyUsingHKDF(const IBuffer& inputData, const IBuffer& salt, uint32_t keySizeInBytes, const IBuffer& info) {
	auto macProvider = MacAlgorithmProvider::OpenAlgorithm(L"HMAC_SHA256"); //MacLength is 32 bytes for SHA256

  // HKDF-extract
  auto extractKey = macProvider.CreateKey(salt);
  auto pseudorandomKey = CryptographicEngine::Sign(extractKey, inputData);
  
  // HKDF-expand
  auto expandKey = macProvider.CreateKey(pseudorandomKey);
  if (expandKey.KeySize() < macProvider.MacLength()) {
	  throw std::runtime_error("Key provided by HMAC_SHA256 implementation is shorter than the HMAC length.");
  }
  int maxKeySize = 255 * macProvider.MacLength();
  if (keySizeInBytes > maxKeySize) {
	  throw std::runtime_error("HKDF requires keySizeInBytes to be at most " +  std::to_string(maxKeySize) + " bytes.");
  }

  int N = std::ceil(keySizeInBytes / macProvider.MacLength());
  std::vector<uint8_t> result;
  std::vector<uint8_t> previousBlock = std::vector<uint8_t>(0);

  for (uint8_t i = 0; i < N; i++) {
      std::vector<uint8_t> input(previousBlock);
      if (info.Length() > 0) {
          input.insert(input.end(), info.data(), info.data() + info.Length());
      }
      input.push_back(i+1);
      
      auto inputBuffer = CryptographicBuffer::CreateFromByteArray(input);
      auto blockBuffer = CryptographicEngine::Sign(expandKey, inputBuffer);

      std::vector<uint8_t> block;
	  block.insert(block.end(), blockBuffer.data(), blockBuffer.data() + blockBuffer.Length());
      previousBlock = block;
      result.insert(result.end(), block.begin(), block.end());
  }

  result.resize(keySizeInBytes);
  return CryptographicBuffer::CreateFromByteArray(result);
}

bool deriveEncryptionKey(const std::vector<uint8_t>& challenge, IBuffer& key){
  auto challengeBuffer = CryptographicBuffer::CreateFromByteArray(
        array_view<const uint8_t>(challenge.data(), challenge.size()));

  try {
    // The first time this is used a key-pair will be generated using the common name
    auto result = KeyCredentialManager::RequestCreateAsync(s_windowsHelloKeyName,
          KeyCredentialCreationOption::FailIfExists).get();

    if (result.Status() == KeyCredentialStatus::CredentialAlreadyExists) {
      result = KeyCredentialManager::OpenAsync(s_windowsHelloKeyName).get();
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

jboolean JNICALL Java_org_cryptomator_windows_keychain_WindowsHello_00024Native_isSupported
  (JNIEnv * env, jobject obj) {
  try {
    winrt::init_apartment(winrt::apartment_type::single_threaded);

    auto keyCredentialAvailable = KeyCredentialManager::IsSupportedAsync().get();
    return keyCredentialAvailable ? JNI_TRUE : JNI_FALSE;

  } catch (winrt::hresult_error const& hre) {
     HRESULT hr = hre.code();
     winrt::hstring message = hre.message();
     std::cerr << "Error: " << winrt::to_string(message) << " (HRESULT: 0x" << std::hex << hr << ")" << std::endl;
     return JNI_FALSE;
   } catch (const std::exception& e) {
     std::cerr << "Warning: " << e.what() << std::endl;
     return JNI_FALSE;
   } catch (...) {
     std::cerr << "Caught an unknown exception" << std::endl;
     return JNI_FALSE;
   }
}

// Encrypts data using Windows Hello KeyCredentialManager API
jbyteArray JNICALL Java_org_cryptomator_windows_keychain_WindowsHello_00024Native_setEncryptionKey
(JNIEnv* env, jobject obj, jbyteArray cleartext, jbyteArray challenge) {
  queueSecurityPromptFocus();
  try {
    // Convert Java byte arrays to C++ vectors
    std::vector<uint8_t> cleartextVec = jbyteArrayToVector(env, cleartext);
    std::vector<uint8_t> challengeVec = jbyteArrayToVector(env, challenge);

    winrt::init_apartment(winrt::apartment_type::single_threaded);

    // Take the random challenge and sign it by Windows Hello
    // to create the key.
    IBuffer key;
    if (!deriveEncryptionKey(challengeVec, key)) {
      throw std::runtime_error("Failed to generate the encryption key with the Windows Hello credential.");
    }

    auto iv = CryptographicBuffer::GenerateRandom(16); // 128-bit IV for AES-CBC
    auto algorithmName = SymmetricAlgorithmNames::AesCbcPkcs7();
    auto aesProvider = SymmetricKeyAlgorithmProvider::OpenAlgorithm(algorithmName);
    auto aesKey = aesProvider.CreateSymmetricKey(key);
    auto dataBuffer = CryptographicBuffer::CreateFromByteArray(array_view<const uint8_t>(cleartextVec.data(), cleartextVec.size()));
    auto encryptedBuffer = CryptographicEngine::Encrypt(aesKey, dataBuffer, iv);

    // Compute HMAC over (IV || ciphertext)
    auto macProvider = MacAlgorithmProvider::OpenAlgorithm(MacAlgorithmNames::HmacSha256());
    auto hmacKey = macProvider.CreateKey(key);

    // Concatenate IV and ciphertext into a single buffer
    std::vector<uint8_t> ivVec = iBufferToVector(iv);
    std::vector<uint8_t> encryptedVec = iBufferToVector(encryptedBuffer);
    std::vector<uint8_t> dataToAuthenticate(ivVec.size() + encryptedVec.size());
    std::copy(ivVec.begin(), ivVec.end(), dataToAuthenticate.begin());
    std::copy(encryptedVec.begin(), encryptedVec.end(), dataToAuthenticate.begin() + ivVec.size());

    // Create a buffer from the concatenated vector
    auto dataToAuthenticateBuffer = CryptographicBuffer::CreateFromByteArray(array_view<const uint8_t>(dataToAuthenticate.data(), dataToAuthenticate.size()));
    auto hmac = CryptographicEngine::Sign(hmacKey, dataToAuthenticateBuffer);

    // Combine IV, ciphertext, and HMAC into the final buffer
    std::vector<uint8_t> hmacVec = iBufferToVector(hmac);
    std::vector<uint8_t> output(dataToAuthenticate.size() + hmacVec.size());
    std::copy(dataToAuthenticate.begin(), dataToAuthenticate.end(), output.begin());
    std::copy(hmacVec.begin(), hmacVec.end(), output.begin() + dataToAuthenticate.size());

    std::fill(cleartextVec.begin(), cleartextVec.end(), 0);
    std::fill(challengeVec.begin(), challengeVec.end(), 0);
    std::fill(ivVec.begin(), ivVec.end(), 0);
    std::fill(encryptedVec.begin(), encryptedVec.end(), 0);
    std::fill(dataToAuthenticate.begin(), dataToAuthenticate.end(), 0);
    std::fill(hmacVec.begin(), hmacVec.end(), 0);

    return vectorToJbyteArray(env, output);

  } catch (winrt::hresult_error const& hre) {
    HRESULT hr = hre.code();
    winrt::hstring message = hre.message();
    std::cerr << "Error: " << winrt::to_string(message) << " (HRESULT: 0x" << std::hex << hr << ")" << std::endl;
    return NULL;
  } catch (const std::exception& e) {
    std::cerr << "Warning: " << e.what() << std::endl;
    return NULL;
  } catch (...) {
    std::cerr << "Caught an unknown exception" << std::endl;
    return NULL;
  }
}

// Decrypts data using Windows Hello KeyCredentialManager API
jbyteArray JNICALL Java_org_cryptomator_windows_keychain_WindowsHello_00024Native_getEncryptionKey
(JNIEnv* env, jobject obj, jbyteArray ciphertext, jbyteArray challenge) {
  queueSecurityPromptFocus();
  try {
    // Convert Java byte arrays to C++ vectors
    std::vector<uint8_t> ciphertextVec = jbyteArrayToVector(env, ciphertext);
    std::vector<uint8_t> challengeVec = jbyteArrayToVector(env, challenge);

    winrt::init_apartment(winrt::apartment_type::single_threaded);

    size_t ivSize = 16; // IV size (128-bit)
    size_t hmacSize = 32; // HMAC size (256-bit)

    // Ensure the input is long enough to contain IV (16 bytes), ciphertext, and HMAC (32 bytes)
    if (ciphertextVec.size() < ivSize + hmacSize) {
      throw std::runtime_error("Invalid ciphertext");
    }

    // Take the random challenge and sign it by Windows Hello
    // to create the key.
    IBuffer key;
    if (!deriveEncryptionKey(challengeVec, key)) {
      throw std::runtime_error("Failed to generate the encryption key with the Windows Hello credential.");
    }

    auto algorithmName = SymmetricAlgorithmNames::AesCbcPkcs7();
    auto aesProvider = SymmetricKeyAlgorithmProvider::OpenAlgorithm(algorithmName);
    auto aesKey = aesProvider.CreateSymmetricKey(key);

    // Split the input data
    std::vector<uint8_t> ivVec(ciphertextVec.begin(), ciphertextVec.begin() + ivSize);
    std::vector<uint8_t> encryptedVec(ciphertextVec.begin() + ivSize, ciphertextVec.end() - hmacSize);
    std::vector<uint8_t> hmacVec(ciphertextVec.end() - hmacSize, ciphertextVec.end());

    // Recreate the data to authenticate (IV || ciphertext)
    std::vector<uint8_t> dataToAuthenticate(ciphertextVec.begin(), ciphertextVec.end() - hmacSize);

    // Compute HMAC to verify integrity
    auto macProvider = MacAlgorithmProvider::OpenAlgorithm(MacAlgorithmNames::HmacSha256());
    auto hmacKey = macProvider.CreateKey(key);
    auto dataToAuthenticateBuffer = CryptographicBuffer::CreateFromByteArray(
        array_view<const uint8_t>(dataToAuthenticate.data(), dataToAuthenticate.size())
    );
    auto computedHmac = CryptographicEngine::Sign(hmacKey, dataToAuthenticateBuffer);
    std::vector<uint8_t> computedHmacVec = iBufferToVector(computedHmac);

    // Compare HMACs
    if (computedHmacVec != hmacVec) {
        throw std::runtime_error("HMAC verification failed.");
    }

    // Decrypt ciphertext
    auto ivBuffer = CryptographicBuffer::CreateFromByteArray(array_view<const uint8_t>(ivVec.data(), ivVec.size()));
    auto encryptedBuffer = CryptographicBuffer::CreateFromByteArray(
        array_view<const uint8_t>(encryptedVec.data(), encryptedVec.size())
    );
    auto decryptedBuffer = CryptographicEngine::Decrypt(aesKey, encryptedBuffer, ivBuffer);

    std::fill(ciphertextVec.begin(), ciphertextVec.end(), 0);
    std::fill(challengeVec.begin(), challengeVec.end(), 0);
    std::fill(ivVec.begin(), ivVec.end(), 0);
    std::fill(encryptedVec.begin(), encryptedVec.end(), 0);
    std::fill(hmacVec.begin(), hmacVec.end(), 0);
    std::fill(dataToAuthenticate.begin(), dataToAuthenticate.end(), 0);
    std::fill(computedHmacVec.begin(), computedHmacVec.end(), 0);

    return vectorToJbyteArray(env, iBufferToVector(decryptedBuffer));

  } catch (winrt::hresult_error const& hre) {
    HRESULT hr = hre.code();
    winrt::hstring message = hre.message();
    std::cerr << "Error: " << winrt::to_string(message) << " (HRESULT: 0x" << std::hex << hr << ")" << std::endl;
    return NULL;
  } catch (const std::exception& e) {
    std::cerr << "Warning: " << e.what() << std::endl;
    return NULL;
  } catch (...) {
    std::cerr << "Caught an unknown exception" << std::endl;
    return NULL;
  }
}
