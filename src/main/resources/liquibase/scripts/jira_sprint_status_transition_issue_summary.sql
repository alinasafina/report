ALTER TABLE public.jira_sprint_status_transition
    ADD COLUMN IF NOT EXISTS issue_summary TEXT;
/

COMMENT ON COLUMN public.jira_sprint_status_transition.issue_summary
    IS 'Описание задачи из Jira field description';
/
