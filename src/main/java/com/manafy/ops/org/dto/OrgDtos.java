package com.manafy.ops.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** DTOs for organization administration (Artifact #3 §2). */
public final class OrgDtos {

    private OrgDtos() {}

    public record CreateRegionRequest(@NotBlank String code, @NotBlank String name,
                                      String city, String state, String timezone) {}

    public record UpdateRegionRequest(String name, String city, String state,
                                      String timezone, String status, Long version) {}

    public record RegionResponse(UUID id, String code, String name, String city, String state,
                                 String country, String timezone, String status, long version) {}

    public record CreateAreaRequest(@NotNull UUID regionId, @NotBlank String code, @NotBlank String name,
                                    String city, String state, String postalCodes, String timezone) {}

    public record UpdateAreaRequest(String name, String city, String state, String postalCodes,
                                    String timezone, String status, Long version) {}

    public record AreaResponse(UUID id, UUID regionId, String code, String name, String city, String state,
                               String postalCodes, String timezone, String status, long version) {}

    public record AssignFieldOfficerRequest(@NotNull UUID fieldOfficerId, String designation) {}

    public record AreaFieldOfficerResponse(UUID id, UUID areaId, UUID fieldOfficerId, String designation,
                                           String effectiveFrom, String effectiveTo, boolean current) {}
}
