package ru.paperless.report.dto;

import java.util.Set;

public record StatusSets(Set<Long> fromIds, Set<Long> toIds) {
}
