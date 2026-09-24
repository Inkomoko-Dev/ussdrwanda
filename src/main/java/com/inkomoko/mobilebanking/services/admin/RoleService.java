package com.inkomoko.mobilebanking.services.admin;

import com.inkomoko.mobilebanking.enums.EPrivilege;
import com.inkomoko.mobilebanking.models.admin.AdminRole;
import com.inkomoko.mobilebanking.models.api.portal.PortalDtos.PrivilegeView;
import com.inkomoko.mobilebanking.models.api.portal.PortalDtos.RoleRequest;
import com.inkomoko.mobilebanking.models.api.portal.PortalDtos.RoleView;
import com.inkomoko.mobilebanking.repositories.AdminRoleRepository;
import com.inkomoko.mobilebanking.repositories.AdminUserRepository;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.utils.DateUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/** Roles and the privileges assigned to them. */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final AdminRoleRepository roles;
    private final AdminUserRepository users;
    private final AuditService audit;

    public List<PrivilegeView> privileges() {
        return Arrays.stream(EPrivilege.values())
                .map(p -> new PrivilegeView(p, p.getCategory(), p.getDescription())).toList();
    }

    public List<RoleView> list() {
        return roles.findAllByOrderByIdAsc().stream().map(this::view).toList();
    }

    @Transactional
    public RoleView create(RoleRequest r, String by) {
        String name = r.name().trim();
        if (roles.existsByNameIgnoreCase(name)) throw new IllegalStateException("Role already exists: " + name);
        AdminRole role = new AdminRole();
        role.setName(name);
        role.setDescription(r.description());
        role.setPrivileges(r.privileges().isEmpty() ? EnumSet.noneOf(EPrivilege.class) : EnumSet.copyOf(r.privileges()));
        role.setCreatedAt(DateUtility.nowKigali());
        role.setUpdatedAt(DateUtility.nowKigali());
        role = roles.save(role);
        audit.admin(by, "ROLE_CREATED", name, true, String.valueOf(role.getPrivileges()));
        return view(role);
    }

    @Transactional
    public RoleView update(Long id, RoleRequest r, String by) {
        AdminRole role = require(id);
        if (role.isSystemRole()) throw new IllegalStateException("The Super Administrator role cannot be changed");
        String name = r.name().trim();
        if (!role.getName().equalsIgnoreCase(name) && roles.existsByNameIgnoreCase(name)) {
            throw new IllegalStateException("Role already exists: " + name);
        }
        role.setName(name);
        role.setDescription(r.description());
        role.getPrivileges().clear();
        role.getPrivileges().addAll(r.privileges());
        role.setUpdatedAt(DateUtility.nowKigali());
        role = roles.save(role);
        audit.admin(by, "ROLE_UPDATED", name, true, String.valueOf(role.getPrivileges()));
        return view(role);
    }

    @Transactional
    public void delete(Long id, String by) {
        AdminRole role = require(id);
        if (role.isSystemRole()) throw new IllegalStateException("The Super Administrator role cannot be deleted");
        long assigned = users.countByRoleId(id);
        if (assigned > 0) throw new IllegalStateException("Role is assigned to " + assigned + " user(s)");
        roles.delete(role);
        audit.admin(by, "ROLE_DELETED", role.getName(), true, null);
    }

    private AdminRole require(Long id) {
        return roles.findById(id).orElseThrow(() -> new IllegalArgumentException("Role not found: " + id));
    }

    private RoleView view(AdminRole r) {
        return new RoleView(r.getId(), r.getName(), r.getDescription(), r.isSystemRole(), r.effectivePrivileges(),
                users.countByRoleId(r.getId()));
    }
}
