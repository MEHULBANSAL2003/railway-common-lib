package com.railway.common.event.auth;

import java.time.Instant;

public record EmailEvent(
  String userId,
  String email,
  String fullName,
  String correlationId,
  Instant triggeredAt
){}
