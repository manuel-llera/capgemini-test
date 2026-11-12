package com.capgemini.test.code.adapter.out.external;

import org.springframework.stereotype.Component;

import com.capgemini.test.code.application.port.DniValidator;
import com.capgemini.test.code.clients.CheckDniRequest;
import com.capgemini.test.code.clients.DniClient;
import com.capgemini.test.code.domain.exception.ExternalServiceException;
import com.capgemini.test.code.domain.exception.InvalidDniException;

import feign.FeignException;

@Component
public class DniValidatorAdapter implements DniValidator {
  private final DniClient dniClient;

  public DniValidatorAdapter(DniClient dniClient) {
    this.dniClient = dniClient;
  }

  @Override
  public void validate(String dni) {
    try {
      dniClient.check(new CheckDniRequest(dni));
    } catch (FeignException.Conflict exception) {
      throw new InvalidDniException();
    } catch (FeignException exception) {
      throw new ExternalServiceException("DNI validation", exception);
    }
  }
}
