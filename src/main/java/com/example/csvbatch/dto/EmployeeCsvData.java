package com.example.csvbatch.dto;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvDate;
import java.math.BigDecimal;
import java.time.LocalDate;

public class EmployeeCsvData {
    
    @CsvBindByName(column = "employeeId")
    private Long employeeId;
    
    @CsvBindByName(column = "employeeName")
    private String employeeName;
    
    @CsvBindByName(column = "department")
    private String department;
    
    @CsvBindByName(column = "email")
    private String email;
    
    @CsvBindByName(column = "hireDate")
    @CsvDate(value = "yyyy-MM-dd")
    private LocalDate hireDate;
    
    @CsvBindByName(column = "salary")
    private BigDecimal salary;
    
    @CsvBindByName(column = "level")
    private String level;
    
    @CsvBindByName(column = "bonus")
    private BigDecimal bonus;
    
    @CsvBindByName(column = "status")
    private String status;
    
    public static class Builder {
        private Long employeeId;
        private String employeeName;
        private String department;
        private String email;
        private LocalDate hireDate;
        private BigDecimal salary;
        private String level;
        private BigDecimal bonus;
        private String status;
        
        public Builder employeeId(Long employeeId) {
            this.employeeId = employeeId;
            return this;
        }
        
        public Builder employeeName(String employeeName) {
            this.employeeName = employeeName;
            return this;
        }
        
        public Builder department(String department) {
            this.department = department;
            return this;
        }
        
        public Builder email(String email) {
            this.email = email;
            return this;
        }
        
        public Builder hireDate(LocalDate hireDate) {
            this.hireDate = hireDate;
            return this;
        }
        
        public Builder salary(BigDecimal salary) {
            this.salary = salary;
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
        
        public EmployeeCsvData build() {
            EmployeeCsvData data = new EmployeeCsvData();
            data.employeeId = this.employeeId;
            data.employeeName = this.employeeName;
            data.department = this.department;
            data.email = this.email;
            data.hireDate = this.hireDate;
            data.salary = this.salary;
            data.level = this.level;
            data.bonus = this.bonus;
            data.status = this.status;
            return data;
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
    
    public String getEmployeeName() {
        return employeeName;
    }
    
    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }
    
    public String getDepartment() {
        return department;
    }
    
    public void setDepartment(String department) {
        this.department = department;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public LocalDate getHireDate() {
        return hireDate;
    }
    
    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }
    
    public BigDecimal getSalary() {
        return salary;
    }
    
    public void setSalary(BigDecimal salary) {
        this.salary = salary;
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
}