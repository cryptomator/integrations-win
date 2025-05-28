#include <jni.h>
#include "org_cryptomator_windows_keychain_WindowsHello_Native.h"
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Security.Credentials.h>
#include <winrt/Windows.Security.Cryptography.h>
#include <winrt/Windows.Security.Cryptography.Core.h>
#include <winrt/Windows.Storage.Streams.h>
#include <windows.h>
#include <wincrypt.h>
#include <unordered_map>
#include <mutex>
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

static std::atomic<int> g_promptFocusCount{ 0 };
static std::mutex cacheMutex;
static std::unordered_map<std::wstring, std::vector<uint8_t>> keyCache;
static constexpr auto HKDF_INFO = L"org.cryptomator.windows.keychain.windowsHello";
static constexpr auto HELLO_CHALLENGE = L"CryptobotAndRalph3000";

// Helper methods to handle KeyCredential
void ProtectMemory(std::vector<uint8_t>& data) {
    if (data.empty()) {
        throw std::invalid_argument("Data to protect must not be empty");
    } else if (data.size() % CRYPTPROTECTMEMORY_BLOCK_SIZE != 0) {
        throw std::invalid_argument("Data to protect must have a size being a multiple of CRYPTPROTECTMEMORY_BLOCK_SIZE (16 bytes).");
    } else if (CryptProtectMemory(data.data(), static_cast<DWORD>(data.size()), CRYPTPROTECTMEMORY_SAME_PROCESS)) {
        throw std::runtime_error("Failed to protect data: " ); //TODO: error code!
    }
}

void UnprotectMemory(std::vector<uint8_t>& data) {
    if (data.empty()) {
        throw std::invalid_argument("Data to unprotect must not be empty");
    } else if (data.size() % CRYPTPROTECTMEMORY_BLOCK_SIZE != 0) {
        throw std::invalid_argument("Data to unprotect must have a size being a multiple of CRYPTPROTECTMEMORY_BLOCK_SIZE (16 bytes).");
    } else if (!CryptUnprotectMemory(data.data(), static_cast<DWORD>(data.size()), CRYPTPROTECTMEMORY_SAME_PROCESS)) {
        throw std::runtime_error("Failed to unprotect data: " ); //TODO: error code!
    }
}

// Helper methods for conversion
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
            }
            else if (g_promptFocusCount.fetch_add(1) + 1 > 3) {
                g_promptFocusCount.store(0);
                break;
            }
        }
        }).detach();
}


IBuffer DeriveKeyUsingHKDF(const IBuffer& inputData, const IBuffer& salt, uint32_t keySizeInBytes) {
    auto info = CryptographicBuffer::ConvertStringToBinary(HKDF_INFO, BinaryStringEncoding::Utf8);
    auto macProvider = MacAlgorithmProvider::OpenAlgorithm(L"HMAC_SHA256"); //MacLength is 32 bytes for SHA256

    // HKDF-extract
    auto extractKey = macProvider.CreateKey(salt);
    auto pseudorandomKey = CryptographicEngine::Sign(extractKey, inputData);

    // HKDF-expand
    auto expandKey = macProvider.CreateKey(pseudorandomKey);
    if (expandKey.KeySize() < macProvider.MacLength()) {
        throw std::runtime_error("Key provided by HMAC_SHA256 implementation is shorter than the HMAC length.");
    }
    auto maxKeySize = 255 * macProvider.MacLength();
    if (keySizeInBytes > maxKeySize) {
        throw std::runtime_error("HKDF requires keySizeInBytes to be at most " + std::to_string(maxKeySize) + " bytes.");
    }

    int N = std::ceil(keySizeInBytes / macProvider.MacLength());
    std::vector<uint8_t> result;
    std::vector<uint8_t> previousBlock = std::vector<uint8_t>(0);

    for (uint8_t i = 0; i < N; i++) {
        auto input = previousBlock;
        if (info.Length() > 0) {
            input.insert(input.end(), info.data(), info.data() + info.Length());
        }
        input.push_back(i + 1);

        auto t_i = CryptographicEngine::Sign(expandKey, CryptographicBuffer::CreateFromByteArray(input));

        previousBlock.clear();
        previousBlock.insert(previousBlock.end(), t_i.data(), t_i.data() + t_i.Length());
        result.insert(result.end(), previousBlock.begin(), previousBlock.end());
    }

    result.resize(keySizeInBytes);
    auto buffer = CryptographicBuffer::CreateFromByteArray(result);
    std::fill(previousBlock.begin(), previousBlock.end(), 0);
    std::fill(result.begin(), result.end(), 0);
    return buffer;
}


// Sign the fixed challenge with the user's Windows Hello credentials
IBuffer getSignature(const std::wstring& keyId) {
    auto result = KeyCredentialManager::RequestCreateAsync(keyId, KeyCredentialCreationOption::FailIfExists).get();

    if (result.Status() == KeyCredentialStatus::CredentialAlreadyExists) {
        result = KeyCredentialManager::OpenAsync(keyId).get();
    } else if (result.Status() != KeyCredentialStatus::Success) {
        throw std::runtime_error("Failed to retrieve Windows Hello credential."); //TODO: error code
    }

    auto challengeBuffer = CryptographicBuffer::ConvertStringToBinary(HELLO_CHALLENGE, BinaryStringEncoding::Utf16LE);
    const auto signature = result.Credential().RequestSignAsync(challengeBuffer).get();

    if (signature.Status() != KeyCredentialStatus::Success) {
        if (signature.Status() != KeyCredentialStatus::UserCanceled) {
            throw std::runtime_error("Failed to sign challenge using Windows Hello. Reason:"); //TODO: reason
        } else {
            throw std::runtime_error("User canceled operation"); //TODO: can we catch/prevent this?
        }
    }

    return signature.Result();
}


IBuffer getOrCreateKey(const std::wstring& keyId, IBuffer salt) {
    IBuffer signature;
    bool foundInCache = false;
    {
        // Lock for thread safety
        std::lock_guard<std::mutex> lock(cacheMutex);
        auto it = keyCache.find(keyId);
        if (it != keyCache.end()) {
            auto signatureData = it->second;
            UnprotectMemory(signatureData);
            signature = CryptographicBuffer::CreateFromByteArray(signatureData);
            std::fill(signatureData.begin(), signatureData.end(), 0);
            foundInCache = true;
        }
    }
    if (!foundInCache) {
        signature = getSignature(keyId);
        auto protectedCopy = iBufferToVector(signature);
        // cache
        try {
            ProtectMemory(protectedCopy);
            std::lock_guard<std::mutex> lock(cacheMutex);
            keyCache[keyId] = protectedCopy;
            std::fill(protectedCopy.begin(), protectedCopy.end(), 0);
        } catch(...) {
            std::fill(protectedCopy.begin(), protectedCopy.end(), 0);
        }
    }

    // Derive the encryption/decryption key using HKDF
    return DeriveKeyUsingHKDF(signature, salt, 32); // needs to be 32 bytes for SHA256
}


jboolean JNICALL Java_org_cryptomator_windows_keychain_WindowsHello_00024Native_isSupported
(JNIEnv* env, jobject obj) {
    try {
        winrt::init_apartment(winrt::apartment_type::single_threaded);

        auto keyCredentialAvailable = KeyCredentialManager::IsSupportedAsync().get();
        return keyCredentialAvailable ? JNI_TRUE : JNI_FALSE;

    }
    catch (winrt::hresult_error const& hre) {
        HRESULT hr = hre.code();
        winrt::hstring message = hre.message();
        std::cerr << "Error: " << winrt::to_string(message) << " (HRESULT: 0x" << std::hex << hr << ")" << std::endl;
        return JNI_FALSE;
    }
    catch (const std::exception& e) {
        std::cerr << "Warning: " << e.what() << std::endl;
        return JNI_FALSE;
    }
    catch (...) {
        std::cerr << "Caught an unknown exception" << std::endl;
        return JNI_FALSE;
    }
}


// Encrypts data using Windows Hello KeyCredentialManager API
jbyteArray JNICALL Java_org_cryptomator_windows_keychain_WindowsHello_00024Native_encrypt
(JNIEnv* env, jobject obj, jbyteArray keyId, jbyteArray cleartext, jbyteArray salt) {
    queueSecurityPromptFocus();
    try {
        // Convert Java byte arrays to C++ vectors
        std::vector<uint8_t> cleartextVec = jbyteArrayToVector(env, cleartext);
        auto saltBuffer =  CryptographicBuffer::CreateFromByteArray(jbyteArrayToVector(env, salt));

        winrt::init_apartment(winrt::apartment_type::single_threaded);

        // Use Windows Hello to create the key material
        auto toReleaseKeyId = (LPCWSTR)env->GetByteArrayElements(keyId, NULL);
        const std::wstring keyIdentifier(toReleaseKeyId);
        env->ReleaseByteArrayElements(keyId, (jbyte*) toReleaseKeyId, JNI_ABORT);
        IBuffer keyMaterial = getOrCreateKey(keyIdentifier, saltBuffer);

        //encrypt
        auto iv = CryptographicBuffer::GenerateRandom(16); // 128-bit IV for AES-CBC
        auto aesCbcPkcs7Algorithm = SymmetricKeyAlgorithmProvider::OpenAlgorithm(SymmetricAlgorithmNames::AesCbcPkcs7());
        auto aesKey = aesCbcPkcs7Algorithm.CreateSymmetricKey(keyMaterial);
        auto dataBuffer = CryptographicBuffer::CreateFromByteArray(array_view<const uint8_t>(cleartextVec.data(), cleartextVec.size()));
        auto encryptedBuffer = CryptographicEngine::Encrypt(aesKey, dataBuffer, iv);

        // Compute HMAC over (IV || ciphertext)
        auto macProvider = MacAlgorithmProvider::OpenAlgorithm(MacAlgorithmNames::HmacSha256());
        auto hmacKey = macProvider.CreateKey(keyMaterial);

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
        std::fill(ivVec.begin(), ivVec.end(), 0);
        std::fill(encryptedVec.begin(), encryptedVec.end(), 0);
        std::fill(dataToAuthenticate.begin(), dataToAuthenticate.end(), 0);
        std::fill(hmacVec.begin(), hmacVec.end(), 0);

        return vectorToJbyteArray(env, output);

    }
    catch (winrt::hresult_error const& hre) {
        HRESULT hr = hre.code();
        winrt::hstring message = hre.message();
        std::cerr << "Error: " << winrt::to_string(message) << " (HRESULT: 0x" << std::hex << hr << ")" << std::endl;
        return NULL;
    }
    catch (const std::exception& e) {
        std::cerr << "Warning: " << e.what() << std::endl;
        return NULL;
    }
    catch (...) {
        std::cerr << "Caught an unknown exception" << std::endl;
        return NULL;
    }
}

// Decrypts data using Windows Hello KeyCredentialManager API
jbyteArray JNICALL Java_org_cryptomator_windows_keychain_WindowsHello_00024Native_decrypt
(JNIEnv* env, jobject obj, jbyteArray keyId, jbyteArray ciphertext, jbyteArray salt) {
    queueSecurityPromptFocus();
    try {
        // Convert Java byte arrays to C++ vectors
        std::vector<uint8_t> ciphertextVec = jbyteArrayToVector(env, ciphertext);
        auto saltBuffer =  CryptographicBuffer::CreateFromByteArray(jbyteArrayToVector(env, salt));

        winrt::init_apartment(winrt::apartment_type::single_threaded);

        size_t ivSize = 16; // IV size (128-bit)
        size_t hmacSize = 32; // HMAC size (256-bit)

        // Ensure the input is long enough to contain IV (16 bytes), ciphertext, and HMAC (32 bytes)
        if (ciphertextVec.size() < ivSize + hmacSize) {
            throw std::invalid_argument("Ciphertext must be at least 48 bytes long");
        }

        // Create the keyMaterial with Windows Hello
        auto toReleaseKeyId = (LPCWSTR)env->GetByteArrayElements(keyId, NULL);
        const std::wstring keyIdentifier(toReleaseKeyId);
        env->ReleaseByteArrayElements(keyId, (jbyte*)toReleaseKeyId, JNI_ABORT);
        IBuffer keyMaterial = getOrCreateKey(keyIdentifier, saltBuffer);

        // Split the input data
        std::vector<uint8_t> ivVec(ciphertextVec.begin(), ciphertextVec.begin() + ivSize);
        std::vector<uint8_t> encryptedVec(ciphertextVec.begin() + ivSize, ciphertextVec.end() - hmacSize);
        std::vector<uint8_t> hmacVec(ciphertextVec.end() - hmacSize, ciphertextVec.end());

        // Recreate the data to authenticate (IV || ciphertext)
        std::vector<uint8_t> dataToAuthenticate(ciphertextVec.begin(), ciphertextVec.end() - hmacSize);

        // Compute HMAC to verify integrity
        auto macProvider = MacAlgorithmProvider::OpenAlgorithm(MacAlgorithmNames::HmacSha256());
        auto hmacKey = macProvider.CreateKey(keyMaterial);
        auto dataToAuthenticateBuffer = CryptographicBuffer::CreateFromByteArray(dataToAuthenticate);
        auto computedHmac = CryptographicEngine::Sign(hmacKey, dataToAuthenticateBuffer);
        std::vector<uint8_t> computedHmacVec = iBufferToVector(computedHmac);

        // Compare HMACs
        if (computedHmacVec != hmacVec) {
            throw std::runtime_error("HMAC verification failed.");
        }

        // Decrypt ciphertext
        auto aesCbcPkcs7Algorithm = SymmetricKeyAlgorithmProvider::OpenAlgorithm(SymmetricAlgorithmNames::AesCbcPkcs7());
        auto aesKey = aesCbcPkcs7Algorithm.CreateSymmetricKey(keyMaterial);
        auto ivBuffer = CryptographicBuffer::CreateFromByteArray(array_view<const uint8_t>(ivVec.data(), ivVec.size()));
        auto encryptedBuffer = CryptographicBuffer::CreateFromByteArray(
            array_view<const uint8_t>(encryptedVec.data(), encryptedVec.size())
        );
        auto decryptedBuffer = CryptographicEngine::Decrypt(aesKey, encryptedBuffer, ivBuffer);

        std::fill(ciphertextVec.begin(), ciphertextVec.end(), 0);
        std::fill(ivVec.begin(), ivVec.end(), 0);
        std::fill(encryptedVec.begin(), encryptedVec.end(), 0);
        std::fill(hmacVec.begin(), hmacVec.end(), 0);
        std::fill(dataToAuthenticate.begin(), dataToAuthenticate.end(), 0);
        std::fill(computedHmacVec.begin(), computedHmacVec.end(), 0);


        jbyteArray decryptedArray = env->NewByteArray(decryptedBuffer.Length());
        if (decryptedArray == nullptr) {
            return nullptr;
        }
        env->SetByteArrayRegion(decryptedArray, 0, decryptedBuffer.Length(), reinterpret_cast<const jbyte*>(decryptedBuffer.data()));
        return decryptedArray;

    }
    catch (winrt::hresult_error const& hre) {
        HRESULT hr = hre.code();
        winrt::hstring message = hre.message();
        std::cerr << "Error: " << winrt::to_string(message) << " (HRESULT: 0x" << std::hex << hr << ")" << std::endl;
        return NULL;
    }
    catch (const std::exception& e) {
        std::cerr << "Warning: " << e.what() << std::endl;
        return NULL;
    }
    catch (...) {
        std::cerr << "Caught an unknown exception" << std::endl;
        return NULL;
    }
}
