package com.railway.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Standard API response wrapper used by ALL services.
 *
 * Success → { "status": "success", "data": { ... } }
 * Error   → { "status": "error",   "reason": "..." }
 *
 * @param <T> type of data payload — can be any object
 *
 * WHY generic <T>?
 *   ApiResponse<User>     → data is a User object
 *   ApiResponse<List<Train>> → data is a list of trains
 *   ApiResponse<Void>     → no data (e.g., logout success)
 *
 * WHY @JsonInclude(NON_NULL)?
 *   On success, "reason" field is null → it won't appear in JSON.
 *   On error, "data" field is null → it won't appear in JSON.
 *   This keeps the response clean — no "reason": null cluttering
 *   success responses, no "data": null cluttering error responses.
 *
 * WHY no @Setter?
 *   Responses are immutable once created. You build one and return it.
 *   No one should modify a response after construction.
 *   This follows the immutability principle — safer in concurrent code.
 *
 * WHY @Builder?
 *   You COULD use the static factory methods below (recommended).
 *   But @Builder gives flexibility for edge cases:
 *     ApiResponse.<User>builder()
 *         .status("success")
 *         .data(user)
 *         .build();
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  private final String status;
  private final T data;
  private final String reason;

  /**
   * Factory method for success with data.
   *
   * Usage: return ResponseEntity.ok(ApiResponse.success(user));
   * Output: { "status": "success", "data": { "id": 1, "email": "..." } }
   */
  public static <T> ApiResponse<T> success(T data) {
    return ApiResponse.<T>builder()
      .status("success")
      .data(data)
      .build();
  }

  /**
   * Factory method for success without data.
   *
   * Usage: return ResponseEntity.ok(ApiResponse.success());
   * Output: { "status": "success" }
   *
   * Use for actions like logout, delete, etc. where there's
   * nothing meaningful to return.
   */
  public static <T> ApiResponse<T> success() {
    return ApiResponse.<T>builder()
      .status("success")
      .build();
  }

  /**
   * Factory method for error.
   *
   * Usage: return ResponseEntity.badRequest().body(ApiResponse.error("Invalid email"));
   * Output: { "status": "error", "reason": "Invalid email" }
   */
  public static <T> ApiResponse<T> error(String reason) {
    return ApiResponse.<T>builder()
      .status("error")
      .reason(reason)
      .build();
  }
}
