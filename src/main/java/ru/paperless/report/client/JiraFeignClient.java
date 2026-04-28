package ru.paperless.report.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import ru.paperless.report.client.dto.request.JiraSearchRequest;
import ru.paperless.report.client.dto.request.TempoPlanSearchRequest;
import ru.paperless.report.client.dto.response.*;
import ru.paperless.report.config.JiraFeignConfig;
import ru.paperless.report.dto.JiraFieldDto;
import ru.paperless.report.dto.JiraProjectStatusesItem;
import ru.paperless.report.dto.TempoAllocationDto;
import ru.paperless.report.dto.TempoPlanDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@FeignClient(
        name = "jiraClient",
        url = "${jira.base-url}",
        configuration = JiraFeignConfig.class
)
public interface JiraFeignClient {

    @GetMapping("/rest/api/2/myself")
    Map<String, Object> myself();

    @GetMapping("/rest/api/2/project/{projectKey}/statuses")
    List<JiraProjectStatusesItem> projectStatuses(@PathVariable("projectKey") String projectKey);

    @GetMapping("/rest/api/2/project/{projectKey}/statuses")
    String projectStatuses2(@PathVariable("projectKey") String projectKey);

    @GetMapping("/rest/agile/1.0/board")
    JiraBoardsResponse boards(@RequestParam("projectKeyOrId") String projectKeyOrId);

    @GetMapping("/rest/agile/1.0/board/{boardId}/sprint")
    JiraSprintsResponse sprints(
            @PathVariable("boardId") Long boardId,
            @RequestParam("state") String state,
            @RequestParam("startAt") int startAt,
            @RequestParam("maxResults") int maxResults
    );

    @GetMapping("/rest/api/2/field")
    List<JiraFieldDto> getFields();

    @PostMapping("/rest/api/2/search")
    JiraSearchResponse search(@RequestBody JiraSearchRequest request);

    @GetMapping("/rest/api/2/issue/{key}")
    JiraIssueResponse getIssue(
            @PathVariable("key") String key,
            @RequestParam("expand") String expand,
            @RequestParam("fields") String fields
    );

    @GetMapping("/rest/api/2/issue/{key}")
    JiraIssueSprintResponse getIssue(
            @PathVariable("key") String key,
            @RequestParam("fields") String fields
    );

    @GetMapping("/rest/api/2/issue/{key}/worklog")
    JiraWorklogResponse getWorklog(
            @PathVariable("key") String key,
            @RequestParam("startAt") int startAt,
            @RequestParam("maxResults") int maxResults
    );

    @GetMapping("/rest/api/2/user/search")
    List<JiraUserResponse> searchUsers(
            @RequestParam("username") String username
    );

    @PostMapping(value = "/rest/tempo-planning/1/plan/search")
    List<TempoPlanDto> searchPlans(@RequestBody TempoPlanSearchRequest req);

    @GetMapping(value = "/rest/tempo-planning/1/allocation")
    List<TempoAllocationDto> getAllocations(
            @RequestParam("assigneeKeys") List<String> assigneeKeys,
            @RequestParam("assigneeType") String assigneeType, // "user" или "USER" — ниже в сервисе сделаем как в доке
            @RequestParam("startDate") String startDate,       // yyyy-MM-dd
            @RequestParam("endDate") String endDate            // yyyy-MM-dd
    );
}