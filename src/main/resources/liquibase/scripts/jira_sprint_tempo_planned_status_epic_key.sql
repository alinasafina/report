ALTER TABLE public.jira_sprint_tempo_planned_status
    ADD COLUMN IF NOT EXISTS epic_key TEXT;
/

COMMENT ON COLUMN public.jira_sprint_tempo_planned_status.epic_key
    IS 'Ключ эпика Jira из поля Epic Link';
/
