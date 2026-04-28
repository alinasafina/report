package ru.paperless.report.client.dto.request;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
public class SprintIdsRequest {
    private String sprintIds; // "101 102 103"
}