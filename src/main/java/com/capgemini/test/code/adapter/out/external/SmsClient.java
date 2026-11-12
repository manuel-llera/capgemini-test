package com.capgemini.test.code.adapter.out.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "smsNotificationClient", url = "${external.service.url}")
public interface SmsClient {
  @PostMapping(value = "/sms", consumes = MediaType.APPLICATION_JSON_VALUE)
  void send(@RequestBody SmsNotificationRequest request);
}
