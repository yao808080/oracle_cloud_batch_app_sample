package com.example.csvbatch.dto;

import java.math.BigDecimal;

public class EmployeeDetails {
    
    private Long employeeId;
    private String level;
    private BigDecimal bonus;
    private String status;
    
    public EmployeeDetails() {
    }
    
    public EmployeeDetails(Long employeeId, String level, BigDecimal bonus, String status) {
        this.employeeId = employeeId;
        this.level = level;
        this.bonus = bonus;
        this.status = status;
    }
    
    public static class Builder {
        private Long employeeId;
        private String level;
        private BigDecimal bonus;
        private String status;
        
        public Builder employeeId(Long employeeId) {
            this.employeeId = employeeId;
            return this;
        }
        
        public Builder level(String level) {
            this.level = level;
            return this;
        }
        
        public Builder bonus(BigDecimal bonus) {
            this.bonus = bonus;
            return this;
        }
        
        public Builder status(String status) {
            this.status = status;
            return this;
        }
        
        public EmployeeDetails build() {
            return new EmployeeDetails(employeeId, level, bonus, status);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public Long getEmployeeId() {
        return employeeId;
    }
    
    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }
    
    public String getLevel() {
        return level;
    }
    
    public void setLevel(String level) {
        this.level = level;
    }
    
    public BigDecimal getBonus() {
        return bonus;
    }
    
    public void setBonus(BigDecimal bonus) {
        this.bonus = bonus;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    @Override
    public String toString() {
        return "EmployeeDetails{" +
                "employeeId=" + employeeId +
                ", level='" + level + '\'' +
                ", bonus=" + bonus +
                ", status='" + status + '\'' +
                '}';
    }
}