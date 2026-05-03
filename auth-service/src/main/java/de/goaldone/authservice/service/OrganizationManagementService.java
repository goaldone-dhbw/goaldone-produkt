package de.goaldone.authservice.service;

import de.goaldone.authservice.domain.Company;
import de.goaldone.authservice.domain.Membership;
import de.goaldone.authservice.dto.CompanyRequest;
import de.goaldone.authservice.dto.CompanyResponse;
import de.goaldone.authservice.dto.MemberListItemResponse;
import de.goaldone.authservice.repository.CompanyRepository;
import de.goaldone.authservice.repository.InvitationRepository;
import de.goaldone.authservice.repository.MembershipRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationManagementService {

    private final CompanyRepository companyRepository;
    private final MembershipRepository membershipRepository;
    private final InvitationRepository invitationRepository;

    @Transactional
    public CompanyResponse createOrganization(CompanyRequest request) {
        if (companyRepository.findBySlug(request.getSlug()).isPresent()) {
            throw new IllegalArgumentException("Organization with slug '" + request.getSlug() + "' already exists");
        }

        Company company = Company.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .build();

        Company savedCompany = companyRepository.save(company);
        return mapToResponse(savedCompany);
    }

    @Transactional(readOnly = true)
    public CompanyResponse getOrganizationById(UUID id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found with ID: " + id));
        return mapToResponse(company);
    }

    @Transactional(readOnly = true)
    public CompanyResponse getOrganizationBySlug(String slug) {
        Company company = companyRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found with slug: " + slug));
        return mapToResponse(company);
    }

    @Transactional
    public CompanyResponse updateOrganization(UUID id, CompanyRequest request) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found with ID: " + id));

        // Check if slug is being changed and if new slug is already taken
        if (!company.getSlug().equals(request.getSlug()) && 
                companyRepository.findBySlug(request.getSlug()).isPresent()) {
            throw new IllegalArgumentException("Organization with slug '" + request.getSlug() + "' already exists");
        }

        company.setName(request.getName());
        company.setSlug(request.getSlug());

        Company updatedCompany = companyRepository.save(company);
        return mapToResponse(updatedCompany);
    }

    private CompanyResponse mapToResponse(Company company) {
        return CompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .slug(company.getSlug())
                .createdAt(company.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<MemberListItemResponse> listMembers(UUID orgId) {
        List<MemberListItemResponse> members = new ArrayList<>();

        // Active members from memberships table
        List<Membership> memberships = membershipRepository.findByCompanyId(orgId);
        for (Membership m : memberships) {
            String email = m.getUser().getEmails().stream()
                    .filter(e -> e.isPrimary())
                    .map(e -> e.getEmail())
                    .findFirst()
                    .orElse(null);
            members.add(MemberListItemResponse.builder()
                    .userId(m.getUser().getId())
                    .email(email)
                    .role(m.getRole().name())
                    .status("ACTIVE")
                    .build());
        }

        // Pending invited members from invitations table (not yet accepted or declined)
        invitationRepository.findPendingByCompanyId(orgId).forEach(invitation ->
            members.add(MemberListItemResponse.builder()
                    .email(invitation.getEmail())
                    .role(invitation.getRole() != null ? invitation.getRole().name() : null)
                    .status("INVITED")
                    .build())
        );

        return members;
    }
}
