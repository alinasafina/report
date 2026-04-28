CREATE TABLE IF NOT EXISTS public.jira_sprint_employee_effort (
  id                 BIGSERIAL PRIMARY KEY,
  project_key         TEXT NOT NULL,
  sprint_first_id           BIGINT,
  sprint_first_name         TEXT,
  sprint_last_logged_id           BIGINT,
  sprint_last_logged_name         TEXT,
  issue_key           TEXT NOT NULL,
  assignee            TEXT,
  developer           TEXT,
  employee            TEXT NOT NULL,            -- конкретный сотрудник из employee.full_name
  first_estimate_hours NUMERIC(12,2),           -- первая оценка (часы)
  logged_hours        NUMERIC(12,2) NOT NULL,   -- сколько списал сотрудник
  collected_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  epic_key         TEXT
);