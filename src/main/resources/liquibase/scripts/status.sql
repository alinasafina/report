CREATE TABLE public.project_jira_status (
status_id        BIGINT,
status_name      TEXT,
status_category  TEXT
);

ALTER TABLE public.project_jira_status
    ADD CONSTRAINT project_jira_status_pk PRIMARY KEY (status_id);