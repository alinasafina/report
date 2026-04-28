CREATE TABLE public.jira_sprint_tempo_planned_status (
id BIGSERIAL PRIMARY KEY,
project_key VARCHAR(64) NOT NULL,
sprint_id   BIGINT,
sprint_name VARCHAR(255),
issue_key VARCHAR(64) NOT NULL,
employee VARCHAR(255) NOT NULL,
assignee_key VARCHAR(255) NOT NULL,
planned_seconds BIGINT NOT NULL,
status_at_sprint_start VARCHAR(255),
status_at_sprint_end   VARCHAR(255)
);

COMMENT ON TABLE public.jira_sprint_tempo_planned_status
    IS 'Запланированные в Tempo задачи со статусами на начало и конец спринта';

COMMENT ON COLUMN public.jira_sprint_tempo_planned_status.id
    IS 'Идентификатор записи';

COMMENT ON COLUMN public.jira_sprint_tempo_planned_status.project_key
    IS 'Ключ проекта Jira';

COMMENT ON COLUMN public.jira_sprint_tempo_planned_status.sprint_id
    IS 'Идентификатор спринта Jira';

COMMENT ON COLUMN public.jira_sprint_tempo_planned_status.sprint_name
    IS 'Название спринта';

COMMENT ON COLUMN public.jira_sprint_tempo_planned_status.issue_key
    IS 'Ключ задачи Jira';

COMMENT ON COLUMN public.jira_sprint_tempo_planned_status.employee
    IS 'ФИО сотрудника (display name)';

COMMENT ON COLUMN public.jira_sprint_tempo_planned_status.assignee_key
    IS 'Jira user key (Tempo assigneeKey)';

COMMENT ON COLUMN public.jira_sprint_tempo_planned_status.planned_seconds
    IS 'Запланированное время в Tempo, секунды';

COMMENT ON COLUMN public.jira_sprint_tempo_planned_status.status_at_sprint_start
    IS 'Статус задачи на начало спринта';

COMMENT ON COLUMN public.jira_sprint_tempo_planned_status.status_at_sprint_end
    IS 'Статус задачи на конец спринта';