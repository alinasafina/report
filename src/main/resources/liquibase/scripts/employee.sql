CREATE TABLE public.employee (
id             BIGSERIAL PRIMARY KEY,
full_name      VARCHAR(255) NOT NULL,
selectable     BOOLEAN NOT NULL DEFAULT true,
jira_user_key  VARCHAR(255)
);

COMMENT ON TABLE public.employee IS 'Сотрудник';
COMMENT ON COLUMN public.employee.id IS 'Идентификатор сотрудника';
COMMENT ON COLUMN public.employee.full_name IS 'ФИО сотрудника';
COMMENT ON COLUMN public.employee.selectable IS 'Можно ли выбирать в отчетах/фильтрах';
COMMENT ON COLUMN public.employee.jira_user_key IS 'Jira user key (Tempo assigneeKey), используется для Tempo Planning API';