-- PostgreSQL Test Database Initialization Script
-- Lightweight alternative for testing without Oracle DB

-- Create Employee table for testing
CREATE TABLE employees (
    employee_id SERIAL PRIMARY KEY,
    employee_name VARCHAR(100) NOT NULL,
    department VARCHAR(50),
    email VARCHAR(100),
    hire_date DATE,
    salary DECIMAL(10,2),
    level VARCHAR(20),
    bonus DECIMAL(10,2),
    status VARCHAR(20) DEFAULT 'Active'
);

-- Insert test data
INSERT INTO employees (employee_name, department, email, hire_date, salary, level, bonus, status) 
VALUES 
    ('田中太郎', '開発部', 'tanaka@example.com', '2020-04-01', 500000, 'Senior', 150000, 'Active'),
    ('佐藤花子', '営業部', 'sato@example.com', '2019-01-15', 450000, 'Mid', 100000, 'Active'),
    ('鈴木次郎', 'マーケティング部', 'suzuki@example.com', '2021-07-01', 420000, 'Junior', 80000, 'Active'),
    ('高橋三郎', 'IT部', 'takahashi@example.com', '2018-03-15', 600000, 'Senior', 200000, 'Active'),
    ('田村四郎', '人事部', 'tamura@example.com', '2022-01-10', 380000, 'Junior', 60000, 'Active');

-- Create CSV export log table for testing
CREATE TABLE csv_export_log (
    log_id SERIAL PRIMARY KEY,
    export_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    employee_count INTEGER,
    file_name VARCHAR(200),
    status VARCHAR(20)
);

-- Create test user with limited permissions
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'csvuser') THEN
        CREATE USER csvuser WITH PASSWORD 'csvpass';
    END IF;
END
$$;

-- Grant permissions
GRANT SELECT, INSERT, UPDATE ON employees TO csvuser;
GRANT SELECT, INSERT ON csv_export_log TO csvuser;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO csvuser;

-- Display test data
SELECT 'Test Employee Count: ' || COUNT(*) as info FROM employees;
SELECT employee_name, department, status FROM employees;