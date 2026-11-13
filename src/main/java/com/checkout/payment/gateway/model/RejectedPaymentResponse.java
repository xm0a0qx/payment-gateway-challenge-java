package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.List;

public record RejectedPaymentResponse(PaymentStatus status, List<String> errors) {

  public RejectedPaymentResponse(List<String> errors) {
    this(PaymentStatus.REJECTED, errors);
  }
}
