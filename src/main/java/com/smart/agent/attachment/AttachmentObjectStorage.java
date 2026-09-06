package com.smart.agent.attachment;

import java.io.InputStream;

public interface AttachmentObjectStorage {
    InputStream open(String objectKey);

    void delete(String objectKey);
}
