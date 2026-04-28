CREATE TABLE IF NOT EXISTS public.jira_sprint_status_transition (
id               BIGSERIAL PRIMARY KEY,
project_key      TEXT NOT NULL,
sprint_id        BIGINT,
sprint_name      TEXT,
issue_key        TEXT NOT NULL,
final_assignee   TEXT,
developer        TEXT,
from_status_id   BIGINT,
from_status_name TEXT,
to_status_id     BIGINT,
to_status_name   TEXT,
transition_date  TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS ix_jst_project_sprint
    ON public.jira_sprint_status_transition(project_key, sprint_id);

CREATE INDEX IF NOT EXISTS ix_jst_issue
    ON public.jira_sprint_status_transition(issue_key);

CREATE INDEX IF NOT EXISTS ix_jst_date
    ON public.jira_sprint_status_transition(transition_date);