package com.smart.agent.ingestion;

import java.io.InputStream;

public interface DocumentParser {
    boolean supports(DetectedContentType type);

    ParsedDocument parse(InputStream input, ParseLimits limits);
}
