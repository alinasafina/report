ALTER TABLE public.jira_sprint_tempo_planned_status
    ADD COLUMN IF NOT EXISTS issue_summary VARCHAR(1024);
/

COMMENT ON COLUMN public.jira_sprint_tempo_planned_status.issue_summary
    IS 'Название задачи из Tempo planItem.summary';
/
