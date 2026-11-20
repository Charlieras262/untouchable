package io.qzz.toshiro.untouchable.crypto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Returns the verifier for a key given its {@code kid}. The library does not store keys: whoever uses it
 * resolves them (keystore, KMS, HSM, configuration) (SPEC §9.3).
 */
public interface KeyResolver {

    /** Verifier for that kid, or null if the key is unknown. */
    SealVerifier resolve(String keyId);

    static KeyResolver of(String keyId, SealVerifier verifier) {
        return of(Collections.singletonMap(keyId, verifier));
    }

    static KeyResolver of(Map<String, SealVerifier> verifiers) {
        final Map<String, SealVerifier> copy = new LinkedHashMap<String, SealVerifier>(verifiers);
        return new KeyResolver() {
            @Override
            public SealVerifier resolve(String keyId) {
                return copy.get(keyId);
            }
        };
    }
}
