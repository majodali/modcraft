package com.majod.llmbridge;

import java.io.IOException;
import java.util.Map;

@FunctionalInterface
public interface HttpTransport {
	String post(String url, Map<String, String> headers, String body) throws IOException, InterruptedException;
}
