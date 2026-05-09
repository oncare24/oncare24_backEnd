package com.oncare.oncare24.security.crypto.ffi;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

public class JnaCryptoFfiClient {
    private static final String GENERATED_KEY_ID = "oncare24-backend-generate-data-key";
    private static final long DATA_KEY_TTL_SECONDS = 86_400L;
    private static final int DATA_KEY_LENGTH_BYTES = 32;
    private static final String ML_KEM_1024 = "ML-KEM-1024";

    private final CryptoFfiNative lib;
    private final ObjectMapper objectMapper;

    public JnaCryptoFfiClient() {
        this(CryptoFfiNative.load(), new ObjectMapper());
    }

    JnaCryptoFfiClient(CryptoFfiNative lib, ObjectMapper objectMapper) {
        this.lib = lib;
        this.objectMapper = objectMapper;
    }

    public byte[] generateDataKey() {
        Pointer handle = createFacade();
        try {
            long createdAt = Instant.now().getEpochSecond();
            long expiresAt = createdAt + DATA_KEY_TTL_SECONDS;
            CryptoFfiNative.BorrowedArg keyId = CryptoFfiNative.BorrowedArg.utf8(GENERATED_KEY_ID);

            byte[] dataKey = callBytes(out -> lib.crypto_ffi_generate_data_key(
                    handle,
                    keyId.asByValue(),
                    createdAt,
                    expiresAt,
                    out
            ));

            if (dataKey.length != DATA_KEY_LENGTH_BYTES) {
                throw new IllegalStateException(
                        "crypto_ffi_generate_data_key returned " + dataKey.length
                                + " bytes, expected " + DATA_KEY_LENGTH_BYTES
                );
            }
            return dataKey;
        } finally {
            check(lib.crypto_ffi_facade_free(handle), "crypto_ffi_facade_free failed");
        }
    }

    public MlKemKeyPair generateMlKemKeypair() {
        Pointer handle = createFacade();
        try {
            byte[] keypairJson = callBytes(out -> lib.crypto_ffi_generate_mlkem_keypair(handle, out));
            MlKemKeyPair keyPair = readMlKemKeyPair(keypairJson);
            if (!ML_KEM_1024.equals(keyPair.algorithm())) {
                throw new IllegalStateException("unexpected ML-KEM algorithm: " + keyPair.algorithm());
            }
            if (keyPair.publicKey() == null || keyPair.publicKey().length == 0) {
                throw new IllegalStateException("crypto_ffi_generate_mlkem_keypair returned an empty public key");
            }
            if (keyPair.privateKey() == null || keyPair.privateKey().length == 0) {
                throw new IllegalStateException("crypto_ffi_generate_mlkem_keypair returned an empty private key");
            }
            return keyPair;
        } finally {
            check(lib.crypto_ffi_facade_free(handle), "crypto_ffi_facade_free failed");
        }
    }

    public byte[] createKeyEnvelope(
            byte[] dataKey,
            String keyId,
            String ownerId,
            int ownerType,
            byte[] publicKey
    ) {
        requireDataKey(dataKey);
        long parsedOwnerId = parseUnsignedId(ownerId, "ownerId");
        Pointer handle = createFacade();
        try {
            long createdAt = Instant.now().getEpochSecond();
            long expiresAt = createdAt + DATA_KEY_TTL_SECONDS;
            CryptoFfiNative.BorrowedArg keyIdArg = CryptoFfiNative.BorrowedArg.utf8(keyId);
            CryptoFfiNative.BorrowedArg publicKeyArg = CryptoFfiNative.BorrowedArg.of(publicKey);

            CryptoFfiNative.FfiCreateKeyEnvelopeRequest.ByReference request =
                    new CryptoFfiNative.FfiCreateKeyEnvelopeRequest.ByReference();
            request.data_key = dataKeyInput(keyIdArg, dataKey, createdAt, expiresAt);
            request.owner_id = parsedOwnerId;
            request.owner_type = ownerType;
            request.public_key = publicKeyArg.asStruct();

            return callBytes(out -> lib.crypto_ffi_create_key_envelope(handle, request, out));
        } finally {
            check(lib.crypto_ffi_facade_free(handle), "crypto_ffi_facade_free failed");
        }
    }

    public byte[] encryptPackage(
            byte[] dataKey,
            String keyId,
            byte[] plaintext,
            long userId,
            byte[] userPublicKey,
            long guardianId,
            byte[] guardianPublicKey
    ) {
        requireDataKey(dataKey);
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId must not be blank");
        }
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        requireNonEmpty(userPublicKey, "userPublicKey");
        requireNonEmpty(guardianPublicKey, "guardianPublicKey");
        Pointer handle = createFacade();
        try {
            long createdAt = Instant.now().getEpochSecond();
            long expiresAt = createdAt + DATA_KEY_TTL_SECONDS;
            CryptoFfiNative.BorrowedArg keyIdArg = CryptoFfiNative.BorrowedArg.utf8(keyId);
            CryptoFfiNative.BorrowedArg plaintextArg = CryptoFfiNative.BorrowedArg.of(plaintext);
            CryptoFfiNative.BorrowedArg userPublicKeyArg = CryptoFfiNative.BorrowedArg.of(userPublicKey);
            CryptoFfiNative.BorrowedArg guardianPublicKeyArg = CryptoFfiNative.BorrowedArg.of(guardianPublicKey);

            CryptoFfiNative.FfiEncryptPackageRequest.ByReference request =
                    new CryptoFfiNative.FfiEncryptPackageRequest.ByReference();
            request.plaintext = plaintextArg.asStruct();
            request.user_id = userId;
            request.user_public_key = userPublicKeyArg.asStruct();
            request.guardian_id = guardianId;
            request.guardian_public_key = guardianPublicKeyArg.asStruct();
            request.data_key = dataKeyInput(keyIdArg, dataKey, createdAt, expiresAt);

            return callBytes(out -> lib.crypto_ffi_encrypt_package(handle, request, out));
        } finally {
            check(lib.crypto_ffi_facade_free(handle), "crypto_ffi_facade_free failed");
        }
    }

    public byte[] decryptPackage(
            byte[] encryptedPackage,
            long callerId,
            int callerType,
            byte[] privateKey
    ) {
        if (encryptedPackage == null || encryptedPackage.length == 0) {
            throw new IllegalArgumentException("encryptedPackage must not be empty");
        }
        if (privateKey == null || privateKey.length == 0) {
            throw new IllegalArgumentException("privateKey must not be empty");
        }
        Pointer handle = createFacade();
        try {
            CryptoFfiNative.BorrowedArg packageArg = CryptoFfiNative.BorrowedArg.of(encryptedPackage);
            CryptoFfiNative.BorrowedArg privateKeyArg = CryptoFfiNative.BorrowedArg.of(privateKey);

            CryptoFfiNative.FfiDecryptPackageRequest.ByReference request =
                    new CryptoFfiNative.FfiDecryptPackageRequest.ByReference();
            request.package_ = packageArg.asStruct();
            request.caller_id = callerId;
            request.caller_type = callerType;
            request.private_key = privateKeyArg.asStruct();

            return callBytes(out -> lib.crypto_ffi_decrypt_package(handle, request, out));
        } finally {
            check(lib.crypto_ffi_facade_free(handle), "crypto_ffi_facade_free failed");
        }
    }

    public byte[] openKeyEnvelope(
            byte[] envelopeJson,
            String callerId,
            int callerType,
            byte[] privateKey
    ) {
        long parsedCallerId = parseUnsignedId(callerId, "callerId");
        Pointer handle = createFacade();
        try {
            CryptoFfiNative.BorrowedArg envelopeArg = CryptoFfiNative.BorrowedArg.of(envelopeJson);
            CryptoFfiNative.BorrowedArg privateKeyArg = CryptoFfiNative.BorrowedArg.of(privateKey);

            CryptoFfiNative.FfiOpenKeyEnvelopeRequest.ByReference request =
                    new CryptoFfiNative.FfiOpenKeyEnvelopeRequest.ByReference();
            request.envelope = envelopeArg.asStruct();
            request.caller_id = parsedCallerId;
            request.caller_type = callerType;
            request.private_key = privateKeyArg.asStruct();

            byte[] dataKey = callBytes(out -> lib.crypto_ffi_open_key_envelope(handle, request, out));
            requireDataKey(dataKey);
            return dataKey;
        } finally {
            check(lib.crypto_ffi_facade_free(handle), "crypto_ffi_facade_free failed");
        }
    }

    private Pointer createFacade() {
        PointerByReference outHandle = new PointerByReference();
        check(lib.crypto_ffi_facade_new_default(outHandle), "crypto_ffi_facade_new_default failed");
        Pointer handle = outHandle.getValue();
        if (handle == null || Pointer.nativeValue(handle) == 0) {
            throw new IllegalStateException("crypto_ffi_facade_new_default returned a null handle");
        }
        return handle;
    }

    private byte[] callBytes(NativeBytesCall call) {
        CryptoFfiNative.FfiByteBuffer.ByReference out = new CryptoFfiNative.FfiByteBuffer.ByReference();
        check(call.invoke(out), "native bytes call failed");
        out.read();
        try {
            return out.toByteArray();
        } finally {
            check(lib.crypto_ffi_byte_buffer_free(out.byValue()), "crypto_ffi_byte_buffer_free failed");
        }
    }

    private MlKemKeyPair readMlKemKeyPair(byte[] keypairJson) {
        try {
            return objectMapper.readValue(keypairJson, MlKemKeyPair.class);
        } catch (IOException error) {
            throw new IllegalStateException("failed to parse ML-KEM keypair JSON", error);
        }
    }

    private static CryptoFfiNative.FfiDataKeyInput dataKeyInput(
            CryptoFfiNative.BorrowedArg keyIdArg,
            byte[] dataKey,
            long createdAtUnixSeconds,
            long expiresAtUnixSeconds
    ) {
        return new CryptoFfiNative.FfiDataKeyInput(
                keyIdArg.asStruct(),
                Arrays.copyOf(dataKey, dataKey.length),
                createdAtUnixSeconds,
                expiresAtUnixSeconds
        );
    }

    private static void requireDataKey(byte[] dataKey) {
        if (dataKey == null || dataKey.length != DATA_KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "data key must be exactly " + DATA_KEY_LENGTH_BYTES
                            + " bytes, got " + (dataKey == null ? "null" : dataKey.length)
            );
        }
    }

    private static void requireNonEmpty(byte[] value, String fieldName) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
    }

    private static long parseUnsignedId(String value, String fieldName) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw new IllegalArgumentException(fieldName + " must not be negative: " + value);
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(fieldName + " must be a numeric unsigned 64-bit id: " + value, error);
        }
    }

    private void check(int code, String operation) {
        if (code == CryptoFfiNative.FFI_ERROR_OK) {
            return;
        }
        throw new CryptoFfiException(operation + ": code=" + code + ", message=" + lastErrorMessage());
    }

    private String lastErrorMessage() {
        long len = lib.crypto_ffi_last_error_message_length().longValue();
        if (len <= 0) {
            return "";
        }
        if (len > Integer.MAX_VALUE - 1L) {
            return "last error message is too large: " + len;
        }

        Memory buffer = new Memory(len + 1L);
        int code = lib.crypto_ffi_last_error_message_copy(buffer, new CryptoFfiNative.SizeT(len + 1L));
        if (code != CryptoFfiNative.FFI_ERROR_OK) {
            return "unable to copy last error message; copy failed with code=" + code;
        }
        return buffer.getString(0, "UTF-8");
    }

    @FunctionalInterface
    private interface NativeBytesCall {
        int invoke(CryptoFfiNative.FfiByteBuffer.ByReference outBuffer);
    }

    public static final class CryptoFfiException extends RuntimeException {
        public CryptoFfiException(String message) {
            super(message);
        }
    }
}
