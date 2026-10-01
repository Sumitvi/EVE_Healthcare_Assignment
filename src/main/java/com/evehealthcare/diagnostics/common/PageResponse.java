package com.evehealthcare.diagnostics.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static final int MAX_SIZE = 50;

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }


    public static PageRequest request(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE), sort);
    }


    public static String escapeLike(String input) {
        return input.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
