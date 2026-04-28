package ru.paperless.report.client.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TempoPlanSearchRequest {
    /**
     * yyyy-MM-dd
     */
    private String from;

    /**
     * yyyy-MM-dd
     */
    private String to;
}