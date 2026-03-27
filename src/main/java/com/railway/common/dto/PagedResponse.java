package com.railway.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Generic paginated response used by ALL services.
 *
 * WHY THIS EXISTS:
 *   Without a standard paginated response, each service would
 *   invent its own format. Frontend would handle:
 *     admins: { "items": [...], "total": 50 }
 *     trains: { "data": [...], "count": 100, "pages": 10 }
 *     bookings: { "results": [...], "totalRecords": 200 }
 *
 *   With PagedResponse, every list endpoint returns the same shape.
 *   Frontend writes one pagination component that works everywhere.
 *
 * WHAT THE FRONTEND GETS:
 *   {
 *     "content": [...],        ← the actual items
 *     "page": 0,               ← current page (0-indexed)
 *     "size": 10,              ← items per page
 *     "totalElements": 47,     ← total items across all pages
 *     "totalPages": 5,         ← total number of pages
 *     "hasNext": true,         ← is there a next page?
 *     "hasPrevious": false     ← is there a previous page?
 *   }
 *
 * @param <T> the DTO type (AdminResponse, TrainResponse, etc.)
 */
@Getter
@Builder
@AllArgsConstructor
public class PagedResponse<T> {

  private List<T> content;
  private int page;
  private int size;
  private long totalElements;
  private int totalPages;
  private boolean hasNext;
  private boolean hasPrevious;

  /**
   * Converts a Spring Data Page<Entity> to our PagedResponse<DTO>.
   *
   * @param springPage the page returned by the repository
   * @param mapper     function to convert entity → DTO (e.g., adminMapper::toResponse)
   *
   * WHY a mapper function?
   *   The repository returns entities (Admin, User, Train).
   *   The API should return DTOs (AdminResponse, UserResponse).
   *   The mapper converts each entity in the page to a DTO.
   *   Using Function<E, T> keeps this class generic — it doesn't
   *   know about Admin or User, it just maps whatever you give it.
   */
  public static <E, T> PagedResponse<T> of(Page<E> springPage, Function<E, T> mapper) {
    return PagedResponse.<T>builder()
      .content(springPage.getContent().stream().map(mapper).toList())
      .page(springPage.getNumber())
      .size(springPage.getSize())
      .totalElements(springPage.getTotalElements())
      .totalPages(springPage.getTotalPages())
      .hasNext(springPage.hasNext())
      .hasPrevious(springPage.hasPrevious())
      .build();
  }
}
