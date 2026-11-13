package com.checkout.payment.gateway.bank;

import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class BankClient {

  private final RestTemplate restTemplate;
  private final String paymentsUrl;

  public BankClient(RestTemplate restTemplate, @Value("${bank.url}") String bankUrl) {
    this.restTemplate = restTemplate;
    this.paymentsUrl = bankUrl + "/payments";
  }

  public boolean authorize(PostPaymentRequest payment) {
    BankPaymentResponse response;
    try {
      response = restTemplate.postForObject(paymentsUrl, BankPaymentRequest.from(payment),
          BankPaymentResponse.class);
    } catch (RestClientException e) {
      throw new BankUnavailableException("Acquiring bank call failed", e);
    }
    if (response == null) {
      throw new BankUnavailableException("Acquiring bank returned an empty response", null);
    }
    return response.authorized();
  }

  @JsonNaming(SnakeCaseStrategy.class)
  record BankPaymentRequest(String cardNumber,
                            String expiryDate,
                            String currency,
                            int amount,
                            String cvv) {

    static BankPaymentRequest from(PostPaymentRequest postPaymentRequest) {
      return new BankPaymentRequest(postPaymentRequest.getCardNumber(),
          String.format("%02d/%d",
              postPaymentRequest.getExpiryMonth(),
              postPaymentRequest.getExpiryYear()),
          postPaymentRequest.getCurrency(),
          postPaymentRequest.getAmount(),
          postPaymentRequest.getCvv());
    }
  }

  @JsonNaming(SnakeCaseStrategy.class)
  record BankPaymentResponse(boolean authorized, String authorizationCode) {

  }
}
