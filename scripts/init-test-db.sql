-- Test Database Initialization Script for Oracle XE
-- Create test user and tables for CSV batch processor testing

-- Create application user
CREATE USER csvuser IDENTIFIED BY csvpass;
GRANT CONNECT, RESOURCE TO csvuser;
GRANT CREATE SESSION TO csvuser;
GRANT CREATE TABLE TO csvuser;
GRANT CREATE VIEW TO csvuser;
GRANT CREATE SEQUENCE TO csvuser;
GRANT UNLIMITED TABLESPACE TO csvuser;

-- Connect as csvuser
CONNECT csvuser/csvpass;

-- Create Employee table for testing
CREATE TABLE employees (
    employee_id NUMBER(10) PRIMARY KEY,
    employee_name VARCHAR2(100) NOT NULL,
    department VARCHAR2(50),
    email VARCHAR2(100),
    hire_date DATE,
    salary NUMBER(10,2),
    level VARCHAR2(20),
    bonus NUMBER(10,2),
    status VARCHAR2(20) DEFAULT 'Active'
);

-- Create sequence for employee_id
CREATE SEQUENCE employee_seq
    START WITH 1000
    INCREMENT BY 1
    NOCACHE;

-- Create trigger for auto-incrementing employee_id
CREATE OR REPLACE TRIGGER employee_id_trigger
    BEFORE INSERT ON employees
    FOR EACH ROW
BEGIN
    IF :NEW.employee_id IS NULL THEN
        :NEW.employee_id := employee_seq.NEXTVAL;
    END IF;
END;
/

-- Insert test data
INSERT INTO employees (employee_name, department, email, hire_date, salary, level, bonus, status) 
VALUES ('田中太郎', '開発部', 'tanaka@example.com', DATE '2020-04-01', 500000, 'Senior', 150000, 'Active');

INSERT INTO employees (employee_name, department, email, hire_date, salary, level, bonus, status) 
VALUES ('佐藤花子', '営業部', 'sato@example.com', DATE '2019-01-15', 450000, 'Mid', 100000, 'Active');

INSERT INTO employees (employee_name, department, email, hire_date, salary, level, bonus, status) 
VALUES ('鈴木次郎', 'マーケティング部', 'suzuki@example.com', DATE '2021-07-01', 420000, 'Junior', 80000, 'Active');

INSERT INTO employees (employee_name, department, email, hire_date, salary, level, bonus, status) 
VALUES ('高橋三郎', 'IT部', 'takahashi@example.com', DATE '2018-03-15', 600000, 'Senior', 200000, 'Active');

INSERT INTO employees (employee_name, department, email, hire_date, salary, level, bonus, status) 
VALUES ('田村四郎', '人事部', 'tamura@example.com', DATE '2022-01-10', 380000, 'Junior', 60000, 'Active');

-- Create additional tables for testing
CREATE TABLE csv_export_log (
    log_id NUMBER(10) PRIMARY KEY,
    export_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    employee_count NUMBER(10),
    file_name VARCHAR2(200),
    status VARCHAR2(20)
);

CREATE SEQUENCE csv_export_log_seq
    START WITH 1
    INCREMENT BY 1
    NOCACHE;

CREATE OR REPLACE TRIGGER csv_export_log_trigger
    BEFORE INSERT ON csv_export_log
    FOR EACH ROW
BEGIN
    IF :NEW.log_id IS NULL THEN
        :NEW.log_id := csv_export_log_seq.NEXTVAL;
    END IF;
END;
/

-- Grant necessary permissions
GRANT ALL PRIVILEGES TO csvuser;

-- Commit changes
COMMIT;

-- Display test data
SELECT COUNT(*) as "Test Employee Count" FROM employees;
SELECT employee_name, department, status FROM employees;

PROMPT Test database initialization completed successfully!