package io.tacticl.data.pipeline.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Document("agent_knowledge")
public class AgentKnowledge {

    @Id private String id;
    @Indexed private String product;
    private List<String> agentTypes;
    private String title;
    private String body;
    @Indexed private KnowledgeStatus status;
    private String proposedBy;
    private Instant proposedAt;
    private String approvedBy;
    private Instant approvedAt;
    private int hitCount;
    private Instant createdAt;

    protected AgentKnowledge() {}

    public static AgentKnowledge propose(String product, List<String> agentTypes,
                                         String title, String body, String proposedBy) {
        AgentKnowledge k = new AgentKnowledge();
        k.id = UUID.randomUUID().toString();
        k.product = product;
        k.agentTypes = agentTypes;
        k.title = title;
        k.body = body;
        k.status = KnowledgeStatus.PROPOSED;
        k.proposedBy = proposedBy;
        k.proposedAt = Instant.now();
        k.hitCount = 0;
        k.createdAt = k.proposedAt;
        return k;
    }

    public void approve(String approvedBy) {
        this.approvedBy = approvedBy;
        this.approvedAt = Instant.now();
        this.status = KnowledgeStatus.APPROVED;
    }

    public void incrementHitCount() { this.hitCount++; }

    public String getId() { return id; }
    public String getProduct() { return product; }
    public List<String> getAgentTypes() { return agentTypes; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public KnowledgeStatus getStatus() { return status; }
    public String getProposedBy() { return proposedBy; }
    public Instant getProposedAt() { return proposedAt; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public int getHitCount() { return hitCount; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(String id) { this.id = id; }
    public void setProduct(String product) { this.product = product; }
    public void setAgentTypes(List<String> agentTypes) { this.agentTypes = agentTypes; }
    public void setTitle(String title) { this.title = title; }
    public void setBody(String body) { this.body = body; }
    public void setStatus(KnowledgeStatus status) { this.status = status; }
    public void setProposedBy(String proposedBy) { this.proposedBy = proposedBy; }
    public void setProposedAt(Instant proposedAt) { this.proposedAt = proposedAt; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public void setHitCount(int hitCount) { this.hitCount = hitCount; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
