package com.ailibrary.catalog;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/** Timeouts for outbound catalog calls so a slow provider cannot hold request threads. */
@Configuration
public class CatalogHttpConfig {
    @Bean
    RestClientCustomizer catalogTimeouts(
            @Value("${app.catalog.connect-timeout:PT3S}") Duration connect,
            @Value("${app.catalog.read-timeout:PT8S}") Duration read) {
        return builder -> {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(connect)
                    .proxy(ProxySelector.getDefault()) // honours https.proxyHost & co.
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
            factory.setReadTimeout(read);
            builder.requestFactory(factory);
        };
    }
}
