package com.inkomoko.mobilebanking.models.admin;

import com.inkomoko.mobilebanking.enums.EPrivilege;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "admin_role")
@Getter
@Setter
@NoArgsConstructor
public class AdminRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String name;

    private String description;

    /** System role (Super Administrator): always has every privilege, cannot be edited or deleted. */
    private boolean systemRole;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "admin_role_privilege", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "privilege")
    @Enumerated(EnumType.STRING)
    private Set<EPrivilege> privileges = new HashSet<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Effective privileges (system roles get all of them, including ones added later). */
    public Set<EPrivilege> effectivePrivileges() {
        return systemRole ? EnumSet.allOf(EPrivilege.class)
                : (privileges.isEmpty() ? EnumSet.noneOf(EPrivilege.class) : EnumSet.copyOf(privileges));
    }
}
