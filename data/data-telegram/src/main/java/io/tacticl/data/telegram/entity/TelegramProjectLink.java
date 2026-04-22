package io.tacticl.data.telegram.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import io.tacticl.data.pipeline.entity.PdlcRole;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document("telegram_project_links")
public class TelegramProjectLink extends BaseMongoEntity {

    @Indexed(unique = true)
    private long chatId;

    @Indexed
    private String projectId;

    @Indexed
    private String ownerUserId;

    private String groupTitle;
    private ProjectStatus status;
    private Map<PdlcRole, Long> forumTopics;
    private Long pinnedStatusMessageId;
    private Instant initializedAt;
    private Instant statusChangedAt;

    public static TelegramProjectLink create(String projectId, long chatId, String ownerUserId, String groupTitle) {
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
    public Instant getInitializedAt() { return initializedAt; }
    public Instant getStatusChangedAt() { return statusChangedAt; }
}
