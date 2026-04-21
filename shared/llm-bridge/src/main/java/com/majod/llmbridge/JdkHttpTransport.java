package com.majod.llmbridge;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class JdkHttpTransport implements HttpTransport {
	private final HttpClient client;
	private final Duration requestTimeout;

	public JdkHttpTransport() {
		this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
				Duration.ofSeconds(60));
	}

	public JdkHttpTransport(HttpClient client, Duration requestTimeout) {
		this.client = client;
		this.requestTimeout = requestTimeout;
	}

	@Override
	public String post(String url, Map<String, String> headers, String body) throws IOException, InterruptedException {
		HttpRequest.Builder req = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(requestTimeout)
				.POST(HttpRequest.BodyPublishers.ofString(body));
		headers.forEach(req::header);
		HttpResponse<String> resp = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() / 100 != 2) {
			throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
		}
		return resp.body();
	}
}
