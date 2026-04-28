package ru.paperless.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.paperless.report.entity.ProjectJiraSprint;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectJiraSprintRepository extends JpaRepository<ProjectJiraSprint, Long> {
    Boolean existsBySprintId(Long SprintId);

    List<ProjectJiraSprint> findAllByOrderBySprintIdAsc();

    @Query("select p from ProjectJiraSprint p where p.sprintId in :sprintIds")
    List<ProjectJiraSprint> findBySprintIds(@Param("sprintIds") List<Long> sprintIds);
}