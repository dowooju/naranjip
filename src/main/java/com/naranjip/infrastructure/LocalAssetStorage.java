package com.naranjip.infrastructure;

import com.naranjip.application.AssetStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(name="app.asset-storage", havingValue="local", matchIfMissing=true)
public class LocalAssetStorage implements AssetStorage {
    private final Path root;
    public LocalAssetStorage(@Value("${app.asset-root:./assets}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }
    @Override public InputStream open(String key) throws IOException {
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root) || !file.toRealPath().startsWith(root.toRealPath())) {
            throw new IOException("Invalid asset key");
        }
        return Files.newInputStream(file);
    }
}
