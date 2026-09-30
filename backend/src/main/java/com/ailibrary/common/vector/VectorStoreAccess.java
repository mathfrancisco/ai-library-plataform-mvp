package com.ailibrary.common.vector;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Single entry point for vector operations. Every embedding call needs a configured provider,
 * so vector features are skipped entirely while {@code app.ai.enabled=false}.
 */
@Component
public class VectorStoreAccess {
    private final ObjectProvider<VectorStore> stores;
    private final boolean enabled;

    public VectorStoreAccess(ObjectProvider<VectorStore> stores, @Value("${app.ai.enabled:false}") boolean enabled) {
        this.stores = stores;
        this.enabled = enabled;
    }

    public Optional<VectorStore> store() {
        return enabled ? Optional.ofNullable(stores.getIfAvailable()) : Optional.empty();
    }
}
