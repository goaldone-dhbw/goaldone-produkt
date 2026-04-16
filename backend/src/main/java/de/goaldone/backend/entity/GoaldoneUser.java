package de.goaldone.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "goaldoneUsers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoaldoneUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID goaldoneID;

    private List<Account> accounts;


}
