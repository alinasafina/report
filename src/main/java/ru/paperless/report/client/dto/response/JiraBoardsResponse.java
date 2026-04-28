package ru.paperless.report.client.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class JiraBoardsResponse {
    private List<Board> values;

    @Data
    public static class Board {
        private Long id;
        private String name;
    }
}