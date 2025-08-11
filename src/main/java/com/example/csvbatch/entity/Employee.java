package com.example.csvbatch.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "employees")
public class Employee {
    
    @Id
    @Column(name = "employee_id")
    private Long employeeId;
    
    @NotNull
    @Size(min = 1, max = 100)
    @Column(name = "employee_name", nullable = false, length = 100)
    private String employeeName;
    
    @Size(max = 50)
    @Column(name = "department", length = 50)
    private String department;
    
    @Email
    @Size(max = 100)
    @Column(name = "email", length = 100)
    private String email;
    
    @Column(name = "hire_date")
    private LocalDate hireDate;
    
    @Column(name = "salary", precision = 10, scale = 2)
    private BigDecimal salary;
    
    public Employee() {
    }
    
    public Employee(Long employeeId, String employeeName, String department, 
                   String email, LocalDate hireDate, BigDecimal salary) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.department = department;
        this.email = email;
        this.hireDate = hireDate;
        this.salary = salary;
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
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Employee employee = (Employee) o;
        return Objects.equals(employeeId, employee.employeeId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(employeeId);
    }
    
    @Override
    public String toString() {
        return "Employee{" +
                "employeeId=" + employeeId +
                ", employeeName='" + employeeName + '\'' +
                ", department='" + department + '\'' +
                ", email='" + email + '\'' +
                ", hireDate=" + hireDate +
                ", salary=" + salary +
                '}';
    }
}