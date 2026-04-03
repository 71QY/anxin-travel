package com.anxin.travel.common.result;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    private List<T> list;
    private long total;
    private long page;
    private long size;
}
