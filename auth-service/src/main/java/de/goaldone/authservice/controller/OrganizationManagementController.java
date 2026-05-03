package de.goaldone.authservice.controller;

import de.goaldone.authservice.dto.CompanyRequest;
import de.goaldone.authservice.dto.CompanyResponse;
import de.goaldone.authservice.dto.MemberListItemResponse;
import de.goaldone.authservice.service.OrganizationManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizations", description = "Organization management endpoints")
public class OrganizationManagementController {

    private final OrganizationManagementService organizationService;

    @PostMapping
    @Operation(summary = "Create a new organization", description = "Creates a new organization in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Organization created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "409", description = "Organization already exists")
    })
    public ResponseEntity<CompanyResponse> createOrganization(@Valid @RequestBody CompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organizationService.createOrganization(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get organization by ID", description = "Retrieves an organization by its UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Organization found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    public ResponseEntity<CompanyResponse> getOrganizationById(@PathVariable @Parameter(description = "Organization UUID") UUID id) {
        return ResponseEntity.ok(organizationService.getOrganizationById(id));
    }

    @GetMapping("/search")
    @Operation(summary = "Search organization by slug", description = "Retrieves an organization by its slug (unique identifier)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Organization found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    public ResponseEntity<CompanyResponse> getOrganizationBySlug(@RequestParam @Parameter(description = "Organization slug") String slug) {
        return ResponseEntity.ok(organizationService.getOrganizationBySlug(slug));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update organization", description = "Updates an existing organization's information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Organization updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Organization not found"),
            @ApiResponse(responseCode = "409", description = "Slug already exists")
    })
    public ResponseEntity<CompanyResponse> updateOrganization(@PathVariable @Parameter(description = "Organization UUID") UUID id, @Valid @RequestBody CompanyRequest request) {
        return ResponseEntity.ok(organizationService.updateOrganization(id, request));
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "List organization members", description = "Returns all active and pending members")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Members found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    public ResponseEntity<List<MemberListItemResponse>> listMembers(
            @PathVariable @Parameter(description = "Organization UUID") UUID id) {
        return ResponseEntity.ok(organizationService.listMembers(id));
    }
}
