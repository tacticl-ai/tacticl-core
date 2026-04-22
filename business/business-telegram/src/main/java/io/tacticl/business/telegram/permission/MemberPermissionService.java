package io.tacticl.business.telegram.permission;

import io.tacticl.data.telegram.entity.MemberRole;
import io.tacticl.data.telegram.entity.TelegramMemberGrant;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramMemberGrantRepository;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class MemberPermissionService {

    private final TelegramProjectLinkRepository projectRepo;
    private final TelegramMemberGrantRepository grantRepo;

    public MemberPermissionService(TelegramProjectLinkRepository projectRepo,
                                   TelegramMemberGrantRepository grantRepo) {
        this.projectRepo = projectRepo;
        this.grantRepo = grantRepo;
    }

    public Optional<MemberRole> findRole(long chatId, String tacticlUserId) {
        return projectRepo.findByChatIdAndIsActiveTrue(chatId).map(project -> {
            if (project.getOwnerUserId().equals(tacticlUserId)) return MemberRole.OWNER;
            return grantRepo.findByProjectIdAndTacticlUserIdAndIsActiveTrue(project.getProjectId(), tacticlUserId)
                .map(TelegramMemberGrant::getRole)
                .orElse(MemberRole.OBSERVER);
        });
    }

    public PermissionCheck require(long chatId, String tacticlUserId, MemberRole minimum) {
        MemberRole actual = findRole(chatId, tacticlUserId).orElse(MemberRole.OBSERVER);
        return actual.atLeast(minimum)
            ? PermissionCheck.allow(actual)
            : PermissionCheck.deny(actual, minimum, "insufficient role");
    }

    public void grant(long chatId, String tacticlUserId, long telegramUserId, MemberRole role, String grantedBy) {
        TelegramProjectLink project = projectRepo.findByChatIdAndIsActiveTrue(chatId)
            .orElseThrow(() -> new IllegalStateException("No active project for chatId " + chatId));
        if (project.getOwnerUserId().equals(tacticlUserId)) {
            return;  // owner role is inferred from project link; no grant row needed
        }
        grantRepo.findByProjectIdAndTacticlUserIdAndIsActiveTrue(project.getProjectId(), tacticlUserId)
            .ifPresentOrElse(
                g -> { g.updateRole(role, grantedBy); grantRepo.save(g); },
                () -> grantRepo.save(TelegramMemberGrant.create(
                    project.getProjectId(), chatId, tacticlUserId, telegramUserId, role, grantedBy))
            );
    }

    public void revoke(long chatId, String tacticlUserId) {
        projectRepo.findByChatIdAndIsActiveTrue(chatId).ifPresent(project ->
            grantRepo.findByProjectIdAndTacticlUserIdAndIsActiveTrue(project.getProjectId(), tacticlUserId)
                .ifPresent(g -> { g.delete(); grantRepo.save(g); }));
    }

    public List<TelegramMemberGrant> listGrants(String projectId) {
        return grantRepo.findByProjectIdAndIsActiveTrue(projectId);
    }
}
