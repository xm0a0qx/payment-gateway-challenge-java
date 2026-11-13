package com.checkout.payment.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.bank.BankClient;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.jayway.jsonpath.JsonPath;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class ProcessPaymentControllerTest {

  private static final int FUTURE_YEAR = YearMonth.now().getYear() + 2;

  @Autowired
  private MockMvc mvc;
  @MockBean
  private BankClient bankClient;

  @Test
  void whenBankAuthorizesThenPaymentIsAuthorizedAndCanBeRetrieved() throws Exception {
    when(bankClient.authorize(any())).thenReturn(true);

    String body = postPayment(validPayment("2222405343248877"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("8877"))
        .andExpect(jsonPath("$.expiryMonth").value(4))
        .andExpect(jsonPath("$.expiryYear").value(FUTURE_YEAR))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(100))
        .andExpect(jsonPath("$.cardNumber").doesNotExist())
        .andExpect(jsonPath("$.cvv").doesNotExist())
        .andReturn().getResponse().getContentAsString();

    String id = JsonPath.read(body, "$.id");
    mvc.perform(get("/payment/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("8877"));
  }

  @Test
  void leadingZerosInCardNumberAndCvvArePreserved() throws Exception {
    when(bankClient.authorize(any())).thenReturn(true);

    postPayment(validPayment("2222405343240123").replace("\"cvv\": \"123\"", "\"cvv\": \"012\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cardNumberLastFour").value("0123"));
  }

  @Test
  void whenBankDeclinesThenPaymentIsDeclined() throws Exception {
    when(bankClient.authorize(any())).thenReturn(false);

    postPayment(validPayment("2222405343248112"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("8112"));
  }

  @Test
  void whenBankIsUnavailableThen502IsReturned() throws Exception {
    when(bankClient.authorize(any())).thenThrow(new BankUnavailableException("down", null));

    postPayment(validPayment("2222405343248870"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void whenSeveralFieldsAreInvalidThenAllErrorsAreReported() throws Exception {
    String body = """
        {"card_number": "1234", "expiry_month": 4, "expiry_year": %d,
         "currency": "JPY", "amount": 100, "cvv": "12"}
        """.formatted(FUTURE_YEAR);

    postPayment(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors.length()").value(3))
        .andExpect(jsonPath("$.errors[0]").value("card_number must be 14-19 digits"))
        .andExpect(jsonPath("$.errors[1]").value("currency must be one of USD, EUR, GBP"))
        .andExpect(jsonPath("$.errors[2]").value("cvv must be 3-4 digits"));
    verifyNoInteractions(bankClient);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', value = {
      "card number too short   | card_number  | \"1234567890123\"        | card_number must be 14-19 digits",
      "card number too long    | card_number  | \"12345678901234567890\" | card_number must be 14-19 digits",
      "card number not numeric | card_number  | \"2222abcd43248877\"     | card_number must be 14-19 digits",
      "card number missing     | card_number  | null                     | card_number is required",
      "month zero              | expiry_month | 0                        | expiry_month must be between 1 and 12",
      "month thirteen          | expiry_month | 13                       | expiry_month must be between 1 and 12",
      "year in the past        | expiry_year  | 2020                     | expiry date must be in the future",
      "unsupported currency    | currency     | \"JPY\"                  | currency must be one of USD, EUR, GBP",
      "currency wrong length   | currency     | \"US\"                   | currency must be one of USD, EUR, GBP",
      "amount zero             | amount       | 0                        | amount must be a positive integer in minor currency units",
      "amount negative         | amount       | -5                       | amount must be a positive integer in minor currency units",
      "cvv too short           | cvv          | \"12\"                   | cvv must be 3-4 digits",
      "cvv too long            | cvv          | \"12345\"                | cvv must be 3-4 digits",
      "cvv not numeric         | cvv          | \"12a\"                  | cvv must be 3-4 digits",
      "cvv missing             | cvv          | null                     | cvv is required",
  })
  void whenPaymentIsInvalidThenItIsRejectedWithoutCallingBank(String caseName, String field,
      String value, String expectedError) throws Exception {
    String body = validPayment("2222405343248877")
        .replaceFirst("\"" + field + "\": [^,}]+", "\"" + field + "\": " + value);

    postPayment(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors[0]").value(expectedError));

    verifyNoInteractions(bankClient);
  }

  @Test
  void whenExpiryIsCurrentMonthThenPaymentIsRejected() throws Exception {
    YearMonth now = YearMonth.now();
    String body = """
        {"card_number": "2222405343248877", "expiry_month": %d, "expiry_year": %d,
         "currency": "GBP", "amount": 100, "cvv": "123"}
        """.formatted(now.getMonthValue(), now.getYear());

    postPayment(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0]").value("expiry date must be in the future"));
    verifyNoInteractions(bankClient);
  }

  @Test
  void whenBodyIsMalformedThenPaymentIsRejected() throws Exception {
    postPayment("{not json")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));
    verifyNoInteractions(bankClient);
  }

  private ResultActions postPayment(String body) throws Exception {
    return mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private static String validPayment(String cardNumber) {
    return """
        {"card_number": "%s", "expiry_month": 4, "expiry_year": %d, "currency": "GBP", "amount": 100, "cvv": "123"}
        """.formatted(cardNumber, FUTURE_YEAR);
  }
}
