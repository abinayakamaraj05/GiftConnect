package com.giftconnect.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * JSON-friendly wrapper for a paged result.
 *
 * Spring's Page serializes a lot of query plumbing (pageable, sort, ...) that
 * the frontend never reads, so API responses expose just these five fields.
 * Also keeps the audit endpoints from depending on Jackson's handling of
 * Spring internals, which changes between versions.
 *
 * @param <T> the mapped element type actually sent to the client
 */
public class PagedResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    /** Maps every entity on the page through {@code mapper} (never serializes the entity). */
    public static <E, T> PagedResponse<T> from(Page<E> source, Function<E, T> mapper) {
        PagedResponse<T> response = new PagedResponse<>();
        response.content = source.getContent().stream().map(mapper).toList();
        response.page = source.getNumber();
        response.size = source.getSize();
        response.totalElements = source.getTotalElements();
        response.totalPages = source.getTotalPages();
        return response;
    }

    public List<T> getContent() { return content; }

    public int getPage() { return page; }

    public int getSize() { return size; }

    public long getTotalElements() { return totalElements; }

    public int getTotalPages() { return totalPages; }
}
