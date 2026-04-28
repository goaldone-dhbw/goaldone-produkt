package de.goaldone.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Configuration class for Spring's RestClient.
 * Sets up the base infrastructure for making HTTP requests to external services.
 */
@Configuration
public class RestClientConfig {

    /**
     * Configures and provides a RestClient.Builder bean.
     * <p>
     * The builder is configured to use an {@link HttpClient} forced to version HTTP/1.1.
     * This ensures broad compatibility, particularly with testing tools like WireMock
     * and certain infrastructure components that might not fully support HTTP/2.
     * </p>
     *
     * @return a configured RestClient.Builder
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        // Force HTTP/1.1 for broad compatibility (especially with WireMock in tests)
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient));
    }
}
