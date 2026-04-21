// railway-common-lib/src/main/java/com/railway/common/kafka/KafkaTopics.java
package com.railway.common.kafka;


public final class KafkaTopics {

  private KafkaTopics() {}


  public static final class Auth {
    private Auth() {}

    public static final String EMAIL_VERIFICATION_REMINDER = "auth.email.verification.reminder";

    public static final String PASSWORD_CHANGED = "auth.user.password.changed";
  }


  public static final class Booking {
    private Booking() {}

    // Reserved for future use
    public static final String BOOKING_CONFIRMED = "booking.confirmed";
    public static final String BOOKING_CANCELLED = "booking.cancelled";
  }


  public static final class Payment {
    private Payment() {}

    // Reserved for future use
    public static final String PAYMENT_SUCCESS = "payment.success";
    public static final String PAYMENT_FAILED = "payment.failed";
  }
}
