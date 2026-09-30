package com.ailibrary.common.vector;

import java.util.Optional;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single entry point for vector operations. Embeddings are computed locally (ONNX), so vector
 * features stay on without an AI key; {@code app.vector.enabled=false} turns them off.
 */
@Component
public class VectorStoreAccess {
    private final ObjectProvider<VectorStore> stores;
    private final boolean enabled;

    public VectorStoreAccess(ObjectProvider<VectorStore> stores, @Value("${app.vector.enabled:true}") boolean enabled) {
        this.stores = stores;
        this.enabled = enabled;
    }

    public Optional<VectorStore> store() {
        return enabled ? Optional.ofNullable(stores.getIfAvailable()) : Optional.empty();
    }
}
