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
    } else if (!CryptProtectMemory(data.data(), static_cast<DWORD>(data.size()), CRYPTPROTECTMEMORY_SAME_PROCESS)) {
        //clear the original data to prevent memory leaks
        SecureZeroMemory(data.data(), data.size());
        throw std::runtime_error("Failed to protect data. Error code: " + std::to_string(GetLastError()) );
    }
}

void UnprotectMemory(std::vector<uint8_t>& data) {
    if (data.empty()) {
        throw std::invalid_argument("Data to unprotect must not be empty");
    } else if (data.size() % CRYPTPROTECTMEMORY_BLOCK_SIZE != 0) {
        throw std::invalid_argument("Data to unprotect must have a size being a multiple of CRYPTPROTECTMEMORY_BLOCK_SIZE (16 bytes).");
    } else if (!CryptUnprotectMemory(data.data(), static_cast<DWORD>(data.size()), CRYPTPROTECTMEMORY_SAME_PROCESS)) {
        throw std::runtime_error("Failed to unprotect data. Error code: " + std::to_string(GetLastError()) );
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


IBuffer DeriveKeyUsingHKDF(const IBuffer& inputData, const IBuffer& salt) {
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

    auto keySizeInBytes = 32; //HKDF requires the keysize to be smaller than 255*macLength, but this is trivially fulfilled here
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
    SecureZeroMemory(previousBlock.data(), previousBlock.size());
    SecureZeroMemory(result.data(), result.size());
    return buffer;
}


// Sign the fixed challenge with the user's Windows Hello credentials
IBuffer getSignature(const std::wstring& keyId) {
    auto result = KeyCredentialManager::RequestCreateAsync(keyId, KeyCredentialCreationOption::FailIfExists).get();

    if (result.Status() == KeyCredentialStatus::CredentialAlreadyExists) {
        result = KeyCredentialManager::OpenAsync(keyId).get();
    } else if (result.Status() != KeyCredentialStatus::Success) {
        throw std::runtime_error("Failed to retrieve WindowsHello credential. Error code: " + std::to_string(GetLastError()) );
    }

    auto challengeBuffer = CryptographicBuffer::ConvertStringToBinary(HELLO_CHALLENGE, BinaryStringEncoding::Utf16LE);
    const auto signature = result.Credential().RequestSignAsync(challengeBuffer).get();

    if (signature.Status() != KeyCredentialStatus::Success) {
        if (signature.Status() != KeyCredentialStatus::UserCanceled) {
            throw std::runtime_error("Failed to sign challenge with WindowsHello. Error code: " + std::to_string(GetLastError()) );
        } else {
            throw std::logic_error("User canceled operation");
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
            SecureZeroMemory(signatureData.data(), signatureData.size());
            foundInCache = true;
        }
    }
    if (!foundInCache) {
        signature = getSignature(keyId);
        auto copyToProtect = iBufferToVector(signature);
        // cache
        try {
            ProtectMemory(copyToProtect);
            std::lock_guard<std::mutex> lock(cacheMutex);
            keyCache[keyId] = copyToProtect;
            SecureZeroMemory(copyToProtect.data(), copyToProtect.size());
        } catch(const std::exception& e) {
            SecureZeroMemory(copyToProtect.data(), copyToProtect.size());
            throw e;
        }
    }

    // Derive the encryption/decryption key using HKDF
    return DeriveKeyUsingHKDF(signature, salt);
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
    std::vector<uint8_t> cleartextVec = jbyteArrayToVector(env, cleartext);
    try {
        // Convert Java byte arrays to C++ vectors
        auto saltBuffer =  CryptographicBuffer::CreateFromByteArray(jbyteArrayToVector(env, salt));

        winrt::init_apartment(winrt::apartment_type::single_threaded);

        // Create key material using Windows Hello
        auto toReleaseKeyId = (LPCWSTR)env->GetByteArrayElements(keyId, NULL);
        const std::wstring keyIdentifier(toReleaseKeyId);
        env->ReleaseByteArrayElements(keyId, (jbyte*) toReleaseKeyId, JNI_ABORT);
        IBuffer keyMaterial = getOrCreateKey(keyIdentifier, saltBuffer);

        //encrypt
        auto ivBuffer = CryptographicBuffer::GenerateRandom(16); // 128-bit IV for AES-CBC
        auto aesCbcPkcs7Algorithm = SymmetricKeyAlgorithmProvider::OpenAlgorithm(SymmetricAlgorithmNames::AesCbcPkcs7());
        auto aesKey = aesCbcPkcs7Algorithm.CreateSymmetricKey(keyMaterial);
        auto dataBuffer = CryptographicBuffer::CreateFromByteArray(cleartextVec);
        auto encryptedBuffer = CryptographicEngine::Encrypt(aesKey, dataBuffer, ivBuffer);

        // Compute HMAC over (IV || ciphertext)
        auto macProvider = MacAlgorithmProvider::OpenAlgorithm(MacAlgorithmNames::HmacSha256());
        auto hmacKey = macProvider.CreateKey(keyMaterial);

        // Concatenate IV and ciphertext into a single buffer
        auto iv = iBufferToVector(ivBuffer);
        auto encrypted = iBufferToVector(encryptedBuffer);
        std::vector<uint8_t> dataToAuthenticate(iv.size() + encrypted.size());
        std::copy(iv.begin(), iv.end(), dataToAuthenticate.begin());
        std::copy(encrypted.begin(), encrypted.end(), dataToAuthenticate.begin() + iv.size());

        // Create HMAC for IV+Ciphertext
        auto dataToAuthenticateBuffer = CryptographicBuffer::CreateFromByteArray(dataToAuthenticate);
        auto hmacBuffer = CryptographicEngine::Sign(hmacKey, dataToAuthenticateBuffer);

        // Combine IV, ciphertext, and HMAC into the final buffer
        auto hmac = iBufferToVector(hmacBuffer);
        std::vector<uint8_t> output(dataToAuthenticate.size() + hmac.size());
        std::copy(dataToAuthenticate.begin(), dataToAuthenticate.end(), output.begin());
        std::copy(hmac.begin(), hmac.end(), output.begin() + dataToAuthenticate.size());

        SecureZeroMemory(cleartextVec.data(), cleartextVec.size());
        return vectorToJbyteArray(env, output);
    }
    catch (winrt::hresult_error const& hre) {
        SecureZeroMemory(cleartextVec.data(), cleartextVec.size());
        HRESULT hr = hre.code();
        winrt::hstring message = hre.message();
        std::cerr << "Error: " << winrt::to_string(message) << " (HRESULT: 0x" << std::hex << hr << ")" << std::endl;
        return NULL;
    }
    catch(const std::logic_error& le) { //tad' hacky: Logic error must only be thrown,if the user cancels the operation.
        SecureZeroMemory(cleartextVec.data(), cleartextVec.size());
        return NULL; //user cancelled
    }
    catch (const std::exception& e) {
        SecureZeroMemory(cleartextVec.data(), cleartextVec.size());
        std::cerr << "Warning: " << e.what() << std::endl;
        return NULL;
    }
    catch (...) {
        SecureZeroMemory(cleartextVec.data(), cleartextVec.size());
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
        IBuffer keyMaterial = getOrCreateKey(keyIdentifier, saltBuffer);

        // Split the input data
        std::vector<uint8_t> iv(ciphertextVec.begin(), ciphertextVec.begin() + ivSize);
        std::vector<uint8_t> encrypted(ciphertextVec.begin() + ivSize, ciphertextVec.end() - hmacSize);
        std::vector<uint8_t> hmac(ciphertextVec.end() - hmacSize, ciphertextVec.end());

        // Recreate the data to authenticate (IV || ciphertext)
        std::vector<uint8_t> dataToAuthenticate(ciphertextVec.begin(), ciphertextVec.end() - hmacSize);

        // Compute HMAC to verify integrity
        auto macProvider = MacAlgorithmProvider::OpenAlgorithm(MacAlgorithmNames::HmacSha256());
        auto hmacKey = macProvider.CreateKey(keyMaterial);
        auto dataToAuthenticateBuffer = CryptographicBuffer::CreateFromByteArray(dataToAuthenticate);
        auto computedHmacBuffer = CryptographicEngine::Sign(hmacKey, dataToAuthenticateBuffer);
        std::vector<uint8_t> computedHmac = iBufferToVector(computedHmacBuffer);

        // Compare HMACs
        bool verify_success = computedHmac == hmac;
        SecureZeroMemory(computedHmac.data(), computedHmac.size());
        if (!verify_success) {
            throw std::runtime_error("HMAC verification failed.");
        }

        // Decrypt ciphertext
        auto aesCbcPkcs7Algorithm = SymmetricKeyAlgorithmProvider::OpenAlgorithm(SymmetricAlgorithmNames::AesCbcPkcs7());
        auto aesKey = aesCbcPkcs7Algorithm.CreateSymmetricKey(keyMaterial);
        auto ivBuffer = CryptographicBuffer::CreateFromByteArray(array_view<const uint8_t>(iv.data(), iv.size()));
        auto encryptedBuffer = CryptographicBuffer::CreateFromByteArray(
            array_view<const uint8_t>(encrypted.data(), encrypted.size())
        );
        auto decryptedBuffer = CryptographicEngine::Decrypt(aesKey, encryptedBuffer, ivBuffer);


        jbyteArray decryptedArray = env->NewByteArray(decryptedBuffer.Length());
        if (decryptedArray != nullptr) {
            env->SetByteArrayRegion(decryptedArray, 0, decryptedBuffer.Length(), reinterpret_cast<const jbyte*>(decryptedBuffer.data()));
        }
        return decryptedArray;
    }
    catch (winrt::hresult_error const& hre) {
        HRESULT hr = hre.code();
        winrt::hstring message = hre.message();
        std::cerr << "Error: " << winrt::to_string(message) << " (HRESULT: 0x" << std::hex << hr << ")" << std::endl;
        return NULL;
    }
    catch(const std::logic_error& le) { //tad' hacky: Logic error must only be thrown,if the user cancels the operation.
        return NULL; //user cancelled
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
