package com.example.demofeignnotretry;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.once;

@SpringBootTest(classes = DemoFeignNotRetryApplicationTests.TestConfiguration.class,
        properties = {
                "spring.cloud.loadbalancer.ribbon.enabled=false",
                "spring.cloud.discovery.client.simple.instances.clientWithoutUrl[0].uri=http://localhost:8115",
                "spring.cloud.loadbalancer.retry.retryable-status-codes=500"
        })
@ExtendWith(MockServerExtension.class)
@MockServerSettings(ports = 8115)
class DemoFeignNotRetryApplicationTests {

    private MockServerClient client;

    @Autowired
    private ClientWithoutUrl clientWithoutUrl;

    @Autowired
    private ClientWithUrl clientWithUrl;

    @BeforeEach
    void setup(ClientAndServer client) {
        this.client = client;
    }

    @Test
    //Works correct, retried
    void correctRetry() {
        client.when(request().withPath("/first").withMethod("GET"))
                .respond(response().withStatusCode(500));

        assertThatThrownBy(() -> clientWithoutUrl.getFirst()).isInstanceOf(FeignException.InternalServerError.class);

        client.verify(request().withPath("/first"), atLeast(2));
    }

    @Test
    void retryDoesNotWork() {
        client.when(request().withPath("/second").withMethod("GET"))
                .respond(response().withStatusCode(500));

        assertThatThrownBy(() -> clientWithUrl.getSecond()).isInstanceOf(FeignException.InternalServerError.class);

        client.verify(request().withPath("/second"), once()); //should be more then one
    }

    @FeignClient(name = "clientWithoutUrl", path = "/first")
    public interface ClientWithoutUrl {

        @GetMapping
        String getFirst();
    }

    @FeignClient(name = "clientWithUrl", url = "http://localhost:8115/second")
    public interface ClientWithUrl {

        @GetMapping
        String getSecond();
    }

    @Configuration
    @EnableAutoConfiguration
    @EnableFeignClients(clients = { ClientWithoutUrl.class, ClientWithUrl.class})
    public static class TestConfiguration {

    }
}
