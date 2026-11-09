package com.naranjip.application;

import java.io.IOException;
import java.io.InputStream;

/** Object-key based contract: replace LocalAssetStorage with S3 without changing the web/UI API. */
public interface AssetStorage {
    InputStream open(String key) throws IOException;
}
