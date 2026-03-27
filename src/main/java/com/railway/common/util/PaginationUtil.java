package com.railway.common.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PaginationUtil {

  private PaginationUtil() {}

  private static final int DEFAULT_PAGE_SIZE = 10;
  private static final int MAX_PAGE_SIZE = 100;
  private static final String DEFAULT_SORT_FIELD = "createdAt";
  private static final Sort.Direction DEFAULT_SORT_DIR = Sort.Direction.DESC;

  /**
   * Builds a validated Pageable from request parameters.
   *
   * @param page              page number (0-indexed), null defaults to 0
   * @param size              items per page, null defaults to 10, max 100
   * @param sortBy            field to sort by (e.g., "email", "createdAt")
   * @param sortDir           direction — "asc" or "desc", defaults to "desc"
   * @param allowedSortFields fields the client is allowed to sort by
   */
  public static Pageable buildPageable(
    Integer page,
    Integer size,
    String sortBy,
    String sortDir,
    String[] allowedSortFields) {

    int validPage = (page != null && page >= 0) ? page : 0;

    int validSize = DEFAULT_PAGE_SIZE;
    if (size != null) {
      validSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    // Validate sortBy — must be in allowed list
    String validSortField = DEFAULT_SORT_FIELD;
    if (sortBy != null && !sortBy.isBlank() && isAllowedSortField(sortBy, allowedSortFields)) {
      validSortField = sortBy.trim();
    }

    // Validate sortDir — only "asc" or "desc"
    Sort.Direction validDirection = DEFAULT_SORT_DIR;
    if (sortDir != null && "asc".equalsIgnoreCase(sortDir.trim())) {
      validDirection = Sort.Direction.ASC;
    }

    Sort sort = Sort.by(validDirection, validSortField);

    return PageRequest.of(validPage, validSize, sort);
  }

  private static boolean isAllowedSortField(String field, String[] allowedFields) {
    for (String allowed : allowedFields) {
      if (allowed.equals(field)) {
        return true;
      }
    }
    return false;
  }
}
