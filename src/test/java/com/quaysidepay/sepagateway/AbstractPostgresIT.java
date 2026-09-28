package com.quaysidepay.sepagateway;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgresTestcontainersConfig.class)
public class AbstractPostgresIT {

    @Autowired
    protected RestTestClient restTestClient;


    protected RestTestClient.ResponseSpec getRequest(String path) {
        return restTestClient.get()
                .uri(path)
                .exchange();
    }

    protected RestTestClient.ResponseSpec postRequest(String path, Object body) {
        return restTestClient.post()
                .uri(path)
                .body(body)
                .exchange();
    }
}
