package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.io.Serializable;
import java.time.YearMonth;

public class PostPaymentRequest implements Serializable {

  @JsonProperty("card_number")
  @NotNull(message = "card_number is required")
  @Pattern(regexp = "\\d{14,19}", message = "card_number must be 14-19 digits")
  private String cardNumber;
  @JsonProperty("card_number_last_four")
  @Schema(hidden = true)
  private int cardNumberLastFour;
  @JsonProperty("expiry_month")
  @Min(value = 1, message = "expiry_month must be between 1 and 12")
  @Max(value = 12, message = "expiry_month must be between 1 and 12")
  private int expiryMonth;
  @JsonProperty("expiry_year")
  private int expiryYear;
  @NotNull(message = "currency is required")
  @Pattern(regexp = "USD|EUR|GBP", message = "currency must be one of USD, EUR, GBP")
  private String currency;
  @Positive(message = "amount must be a positive integer in minor currency units")
  private int amount;
  @NotNull(message = "cvv is required")
  @Pattern(regexp = "\\d{3,4}", message = "cvv must be 3-4 digits")
  private String cvv;

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public int getCardNumberLastFour() {
    return cardNumberLastFour;
  }

  public void setCardNumberLastFour(int cardNumberLastFour) {
    this.cardNumberLastFour = cardNumberLastFour;
  }

  public int getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(int expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public int getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(int expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public int getAmount() {
    return amount;
  }

  public void setAmount(int amount) {
    this.amount = amount;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
  }

  @JsonProperty("expiry_date")
  @Schema(hidden = true)
  public String getExpiryDate() {
    return String.format("%d/%d", expiryMonth, expiryYear);
  }

  @JsonIgnore
  @AssertTrue(message = "expiry date must be in the future")
  public boolean isExpiryDateInFuture() {
    if (expiryMonth < 1 || expiryMonth > 12) {
      return true; // reported by the expiry_month constraints
    }
    return YearMonth.of(expiryYear, expiryMonth).isAfter(YearMonth.now());
  }

  @Override
  public String toString() {
    return "PostPaymentRequest{" +
        "cardNumberLastFour=" + cardNumberLastFour +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        '}';
  }
}
