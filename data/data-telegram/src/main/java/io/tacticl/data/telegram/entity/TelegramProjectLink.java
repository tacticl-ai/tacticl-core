package io.tacticl.data.telegram.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import io.tacticl.data.pipeline.entity.PdlcRole;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

// Partial unique index ensures concurrent /init in the same chat cannot create
// two simultaneously-active links, while still allowing archived rows to coexist
// with a freshly re-initialized link for the same chatId.
@Document("telegram_project_links")
@CompoundIndex(
    name = "chat_active_unique",
    def = "{'chatId': 1, 'isActive': 1}",
    unique = true,
    partialFilter = "{ 'isActive': true }"
)
public class TelegramProjectLink extends BaseMongoEntity {

    private long chatId;

    @Indexed
    private String projectId;

    @Indexed
    private String ownerUserId;

    private String groupTitle;

    @Indexed
    private ProjectStatus status;
    private Map<PdlcRole, Long> forumTopics;
    private Long pinnedStatusMessageId;

    @Version
    private Long version;

    private Instant initializedAt;
    private Instant statusChangedAt;

    public static TelegramProjectLink create(String projectId, long chatId, String ownerUserId, String groupTitle) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(ownerUserId, "ownerUserId");
        Objects.requireNonNull(groupTitle, "groupTitle");
        var link = new TelegramProjectLink();
        link.projectId = projectId;
        link.chatId = chatId;
        link.ownerUserId = ownerUserId;
        link.groupTitle = groupTitle;
        link.status = ProjectStatus.ACTIVE;
        link.initializedAt = Instant.now();
        link.statusChangedAt = link.initializedAt;
        return link;
    }

    public void archive() { setStatus(ProjectStatus.ARCHIVED); }
    public void orphan()  { setStatus(ProjectStatus.ORPHANED); }
    public void reactivate() { setStatus(ProjectStatus.ACTIVE); }

    /**
     * Remap this link to a new chat id (e.g., group → supergroup migration).
     * Forum topics and the pinned status message do not survive the migration,
     * so both are cleared.
     */
    public void migrateTo(long newChatId) {
        this.chatId = newChatId;
        this.forumTopics = null;
        this.pinnedStatusMessageId = null;
    }

    private void setStatus(ProjectStatus s) {
        this.status = s;
        this.statusChangedAt = Instant.now();
    }

    public long getChatId() { return chatId; }
    public void setChatId(long chatId) { this.chatId = chatId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getGroupTitle() { return groupTitle; }
    public void setGroupTitle(String groupTitle) { this.groupTitle = groupTitle; }
    public ProjectStatus getStatus() { return status; }
    public Map<PdlcRole, Long> getForumTopics() { return forumTopics; }
    public void setForumTopics(Map<PdlcRole, Long> forumTopics) { this.forumTopics = forumTopics; }
    public Long getPinnedStatusMessageId() { return pinnedStatusMessageId; }
    public void setPinnedStatusMessageId(Long id) { this.pinnedStatusMessageId = id; }
    public Long getVersion() { return version; }
    public Instant getInitializedAt() { return initializedAt; }
    public Instant getStatusChangedAt() { return statusChangedAt; }
}
