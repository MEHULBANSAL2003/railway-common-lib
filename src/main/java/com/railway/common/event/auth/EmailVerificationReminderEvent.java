// railway-common-lib/src/main/java/com/railway/common/event/auth/EmailVerificationReminderEvent.java
package com.railway.common.event.auth;

import java.time.Instant;

public record EmailVerificationReminderEvent(
  String userId,
  String email,
  String name,
  Instant triggeredAt
) {}
