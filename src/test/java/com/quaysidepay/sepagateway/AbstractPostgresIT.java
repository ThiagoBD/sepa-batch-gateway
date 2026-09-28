package com.quaysidepay.sepagateway;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(PostgresTestcontainersConfig.class)
public class AbstractPostgresIT {

    @Autowired
    protected WebTestClient webTestClient;


    protected WebTestClient.ResponseSpec getRequest(String path) {
        return webTestClient.get()
                .uri(path)
                .exchange();
    }

    protected WebTestClient.ResponseSpec postRequest(String path, Object body) {
        return webTestClient.post()
                .uri(path)
                .bodyValue(body)
                .exchange();
    }
}
