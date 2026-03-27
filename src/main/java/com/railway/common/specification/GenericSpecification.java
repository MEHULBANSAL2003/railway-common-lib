package com.railway.common.specification;

import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds dynamic WHERE clauses from filters.
 *
 * Supports three types of filters:
 *   1. Exact match  → WHERE role = 'ADMIN'
 *   2. LIKE search on a single field → WHERE LOWER(firstName) LIKE '%mehul%'
 *   3. LIKE search across multiple fields → WHERE (email LIKE '%x%' OR name LIKE '%x%')
 *   4. Boolean → WHERE enabled = true
 *   5. Active only → WHERE enabled = true (for user-facing endpoints)
 *
 * All methods are null-safe — null values are silently ignored.
 * Pass all request params blindly, only non-null ones become filters.
 *
 * @param <T> the entity type
 */
public class GenericSpecification<T> {

  private final List<FilterCriteria> filters = new ArrayList<>();
  private final List<String> globalSearchFields = new ArrayList<>();
  private String globalSearchTerm;
  private String activeField;
  private boolean activeOnly = false;

  public static <T> GenericSpecification<T> builder() {
    return new GenericSpecification<>();
  }

  /**
   * Exact match filter: WHERE field = value
   * Use for: role, department, status — enum/fixed values
   */
  public GenericSpecification<T> equal(String field, Object value) {
    if (value != null && !value.toString().isBlank()) {
      filters.add(new FilterCriteria(field, FilterType.EQUAL, value.toString()));
    }
    return this;
  }

  /**
   * Boolean filter: WHERE field = true/false
   * Use for: enabled, locked, verified
   */
  public GenericSpecification<T> isTrue(String field, Boolean value) {
    if (value != null) {
      filters.add(new FilterCriteria(field, FilterType.BOOLEAN, value.toString()));
    }
    return this;
  }

  /**
   * LIKE search on a SINGLE field: WHERE LOWER(field) LIKE '%term%'
   * Use for: column-specific filters in data tables
   *
   * Example:
   *   .like("firstName", "mehul")  → WHERE LOWER(first_name) LIKE '%mehul%'
   *   .like("email", "gmail")      → WHERE LOWER(email) LIKE '%gmail%'
   */
  public GenericSpecification<T> like(String field, String value) {
    if (value != null && !value.isBlank()) {
      filters.add(new FilterCriteria(field, FilterType.LIKE, value.trim().toLowerCase()));
    }
    return this;
  }

  /**
   * LIKE search across MULTIPLE fields: WHERE (field1 LIKE '%term%' OR field2 LIKE '%term%')
   * Use for: global search bar that searches across name, email, phone, etc.
   *
   * Example:
   *   .search("mehul", "email", "firstName", "lastName", "phone")
   *   → WHERE (LOWER(email) LIKE '%mehul%' OR LOWER(firstName) LIKE '%mehul%' OR ...)
   */
  public GenericSpecification<T> search(String term, String... fields) {
    if (term != null && !term.isBlank()) {
      this.globalSearchTerm = term.trim().toLowerCase();
      for (String field : fields) {
        this.globalSearchFields.add(field);
      }
    }
    return this;
  }

  /**
   * Active-only filter: WHERE activeField = true
   * Use for: user-facing endpoints (show only active records)
   * Don't call this for admin endpoints (admins see everything)
   */
  public GenericSpecification<T> activeOnly(String field) {
    this.activeField = field;
    this.activeOnly = true;
    return this;
  }

  /**
   * Build the final Specification.
   *
   * All filters are AND'd together.
   * Global search fields are OR'd within themselves, then AND'd with the rest.
   *
   * Example: equal("role","ADMIN") + like("firstName","mehul") + search("ops","department")
   * Builds: WHERE role='ADMIN' AND LOWER(first_name) LIKE '%mehul%'
   *         AND (LOWER(department) LIKE '%ops%')
   */
  public Specification<T> build() {
    return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      for (FilterCriteria criteria : filters) {
        switch (criteria.type) {
          case EQUAL:
            predicates.add(cb.equal(root.get(criteria.field), criteria.value));
            break;
          case BOOLEAN:
            predicates.add(cb.equal(root.get(criteria.field), Boolean.parseBoolean(criteria.value)));
            break;
          case LIKE:
            predicates.add(cb.like(cb.lower(root.get(criteria.field)),  criteria.value + "%"));
            break;
        }
      }

      // Global search — OR across multiple fields
      if (globalSearchTerm != null && !globalSearchFields.isEmpty()) {
        List<Predicate> searchPredicates = new ArrayList<>();
        for (String field : globalSearchFields) {
          searchPredicates.add(cb.like(
            cb.lower(root.get(field)),
            "%" + globalSearchTerm + "%"
          ));
        }
        predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
      }

      // Active-only filter
      if (activeOnly && activeField != null) {
        predicates.add(cb.equal(root.get(activeField), true));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private enum FilterType {
    EQUAL,
    BOOLEAN,
    LIKE
  }

  private record FilterCriteria(String field, FilterType type, String value) {}
}
