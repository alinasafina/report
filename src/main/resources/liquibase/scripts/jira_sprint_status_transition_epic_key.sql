ALTER TABLE public.jira_sprint_status_transition
    ADD COLUMN IF NOT EXISTS epic_key TEXT;
/

COMMENT ON COLUMN public.jira_sprint_status_transition.epic_key
    IS 'Ключ эпика Jira из поля Epic Link';
/
