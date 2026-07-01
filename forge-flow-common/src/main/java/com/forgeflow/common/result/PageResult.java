package com.forgeflow.common.result;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.Data;

@Data
public class PageResult<T> implements Serializable {

    private List<T> items = Collections.emptyList();
    private Long total = 0L;
}
