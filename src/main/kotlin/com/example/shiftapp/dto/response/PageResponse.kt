package com.example.shiftapp.dto.response

import org.springframework.data.domain.Page

/**
 * Stable, framework-agnostic page payload for paginated list endpoints.
 *
 * Why not return Spring's `Page<T>` directly?
 *  - Spring's serialised shape (e.g. `pageable.sort.unsorted`) is internal
 *    and changes between versions, which leaks Jackson/Spring Data details
 *    into the API contract.
 *  - Frontend clients only need the bare essentials to render pagination
 *    controls.
 *
 * Field semantics mirror Spring's `Page` so the mapping is trivial:
 *  - `page`          0-based current page index
 *  - `size`          page size that was used
 *  - `totalElements` total rows across all pages
 *  - `totalPages`    number of pages with the current size
 */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <S, T> from(page: Page<S>, transform: (S) -> T): PageResponse<T> =
            PageResponse(
                content = page.content.map(transform),
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
    }
}
