package com.capgemini.test.code.adapter.out.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "emailNotificationClient", url = "${external.service.url}")
public interface EmailClient {
  @PostMapping(value = "/email", consumes = MediaType.APPLICATION_JSON_VALUE)
  void send(@RequestBody EmailNotificationRequest request);
}
