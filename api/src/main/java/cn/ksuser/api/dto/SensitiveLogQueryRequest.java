package cn.ksuser.api.dto;

import java.time.LocalDate;

public class SensitiveLogQueryRequest {

    private Integer page = 1; // 页数，默认第1页

    private Integer pageSize = 20; // 每页数量，默认20条

    private LocalDate startDate; // 开始日期（可选）

    private LocalDate endDate; // 结束日期（可选）

    private String operationType; // 操作类型（可选）

    private String result; // 结果：SUCCESS/FAILURE（可选）

    // Getters and Setters
    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
