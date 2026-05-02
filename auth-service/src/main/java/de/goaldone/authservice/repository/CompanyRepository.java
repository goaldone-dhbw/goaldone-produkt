package de.goaldone.authservice.repository;

import de.goaldone.authservice.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {
    
    Optional<Company> findBySlug(String slug);
}
