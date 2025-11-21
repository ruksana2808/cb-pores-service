package com.igot.cb;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;



class CbPoresApplicationTest {

    @Test
    void restTemplateBean_ShouldNotBeNull() {
        CbPoresApplication app = new CbPoresApplication();
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(CbPoresApplication.class.getClassLoader());
            RestTemplate restTemplate = app.restTemplate();

            assertNotNull(restTemplate, "RestTemplate should not be null");
            ClientHttpRequestFactory factory = restTemplate.getRequestFactory();
            assertNotNull(factory, "ClientHttpRequestFactory should not be null");
            assertTrue(factory instanceof HttpComponentsClientHttpRequestFactory,
                    "Request factory should be an instance of HttpComponentsClientHttpRequestFactory");
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

}
