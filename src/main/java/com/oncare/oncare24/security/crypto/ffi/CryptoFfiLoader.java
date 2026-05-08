package com.oncare.oncare24.security.crypto.ffi;

import java.nio.file.Files;
import java.nio.file.Path;

public final class CryptoFfiLoader {
    private static final String ENV_LIBRARY_PATH = "CRYPTO_FFI_LIBRARY";
    private static final Path DEFAULT_LIBRARY_PATH = Path.of("native", "crypto_ffi.dll");

    private CryptoFfiLoader() {
    }

    public static Path resolveLibraryPath() {
        String configuredPath = System.getenv(ENV_LIBRARY_PATH);
        Path libraryPath = configuredPath != null && !configuredPath.isBlank()
                ? Path.of(configuredPath)
                : DEFAULT_LIBRARY_PATH;

        Path absolutePath = libraryPath.toAbsolutePath().normalize();
        if (!Files.exists(absolutePath)) {
            throw new IllegalStateException(
                    "crypto_ffi.dll not found. Set " + ENV_LIBRARY_PATH
                            + " or place the DLL at " + DEFAULT_LIBRARY_PATH
                            + ". Resolved path: " + absolutePath
            );
        }
        if (!Files.isRegularFile(absolutePath)) {
            throw new IllegalStateException("crypto_ffi.dll path is not a file: " + absolutePath);
        }

        return absolutePath;
    }
}
