package com.clacjz.medical.rag.model.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页结果")
public class PageResult<T> {

    @Schema(description = "数据列表")
    private List<T> items;

    @Schema(description = "总记录数")
    private long total;

    @Schema(description = "当前页码")
    private int page;

    @Schema(description = "每页大小")
    private int pageSize;

    @Schema(description = "总页数")
    private int totalPages;

    public static <T, R> PageResult<R> from(Page<T> page, Function<T, R> mapper) {
        List<R> items = page.getContent().stream()
                .map(mapper)
                .collect(Collectors.toList());

        return PageResult.<R>builder()
                .items(items)
                .total(page.getTotalElements())
                .page(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .build();
    }

    public static <T> PageResult<T> from(Page<T> page) {
        return PageResult.<T>builder()
                .items(page.getContent())
                .total(page.getTotalElements())
                .page(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .build();
    }

    public static <T, R> PageResult<R> from(IPage<T> page, Function<T, R> mapper) {
        List<R> items = page.getRecords().stream()
                .map(mapper)
                .collect(Collectors.toList());

        return PageResult.<R>builder()
                .items(items)
                .total(page.getTotal())
                .page((int) page.getCurrent())
                .pageSize((int) page.getSize())
                .totalPages((int) page.getPages())
                .build();
    }

    public static <T> PageResult<T> from(IPage<T> page) {
        return PageResult.<T>builder()
                .items(page.getRecords())
                .total(page.getTotal())
                .page((int) page.getCurrent())
                .pageSize((int) page.getSize())
                .totalPages((int) page.getPages())
                .build();
    }
}
