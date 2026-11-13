package com.checkout.payment.gateway.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.checkout.payment.gateway.configuration.ApplicationConfiguration;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(BankClient.class)
@Import(ApplicationConfiguration.class)
@TestPropertySource(properties = "bank.url=http://bank")
class BankClientTest {

  private static final PostPaymentRequest PAYMENT = new PostPaymentRequest();

  static {
    PAYMENT.setCardNumber("2222405343248877");
    PAYMENT.setExpiryMonth(4);
    PAYMENT.setExpiryYear(2030);
    PAYMENT.setCurrency("GBP");
    PAYMENT.setAmount(100);
    PAYMENT.setCvv("123");
  }

  @Autowired
  private BankClient bankClient;
  @Autowired
  private MockRestServiceServer server;

  @Test
  void sendsPaymentInBankFormatAndReturnsAuthorization() {
    server.expect(requestTo("http://bank/payments"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""
            {"card_number": "2222405343248877", "expiry_date": "04/2030",
             "currency": "GBP", "amount": 100, "cvv": "123"}
            """, true))
        .andRespond(withSuccess("{\"authorized\": true, \"authorization_code\": \"abc\"}",
            MediaType.APPLICATION_JSON));

    assertThat(bankClient.authorize(PAYMENT)).isTrue();
    server.verify();
  }

  @Test
  void returnsFalseWhenBankDeclines() {
    server.expect(requestTo("http://bank/payments"))
        .andRespond(withSuccess("{\"authorized\": false, \"authorization_code\": \"\"}",
            MediaType.APPLICATION_JSON));

    assertThat(bankClient.authorize(PAYMENT)).isFalse();
  }

  @Test
  void throwsBankUnavailableWhenBankReturns503() {
    server.expect(requestTo("http://bank/payments")).andRespond(withServiceUnavailable());

    assertThatThrownBy(() -> bankClient.authorize(PAYMENT))
        .isInstanceOf(BankUnavailableException.class);
  }

  @Test
  void throwsBankUnavailableWhenBankTimesOut() {
    server.expect(requestTo("http://bank/payments"))
        .andRespond(withException(new SocketTimeoutException("read timed out")));

    assertThatThrownBy(() -> bankClient.authorize(PAYMENT))
        .isInstanceOf(BankUnavailableException.class);
  }
}
