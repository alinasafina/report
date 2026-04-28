package ru.paperless.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.paperless.report.entity.ProjectJiraStatus;

import java.util.Collection;
import java.util.List;

public interface ProjectJiraStatusRepository extends JpaRepository<ProjectJiraStatus, Long> {
    Boolean existsByStatusId(Long statusId);

    List<ProjectJiraStatus> findByStatusNameIn(Collection<String> names);
}
