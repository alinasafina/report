CREATE TABLE public.project_jira_sprint (
sprint_id      BIGINT PRIMARY KEY,
sprint_name    TEXT,
sprint_state   TEXT,
start_date     DATE,
end_date       DATE,
complete_date  DATE,
board_id       BIGINT,
board_name     TEXT,
project_key    TEXT
);