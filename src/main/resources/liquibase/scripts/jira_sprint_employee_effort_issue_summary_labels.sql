ALTER TABLE public.jira_sprint_employee_effort
    ADD COLUMN IF NOT EXISTS issue_summary TEXT;
/

ALTER TABLE public.jira_sprint_employee_effort
    ADD COLUMN IF NOT EXISTS labels TEXT;
/

COMMENT ON COLUMN public.jira_sprint_employee_effort.issue_summary
    IS 'Название задачи из Jira field summary';
/

COMMENT ON COLUMN public.jira_sprint_employee_effort.labels
    IS 'Список labels из Jira через разделитель ;';
/
