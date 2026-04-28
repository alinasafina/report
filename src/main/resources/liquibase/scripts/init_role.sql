CREATE ROLE app_liquibase_role;

CREATE USER app_liquibase WITH PASSWORD 'app_liquibase';
GRANT app_liquibase_role TO app_liquibase;
