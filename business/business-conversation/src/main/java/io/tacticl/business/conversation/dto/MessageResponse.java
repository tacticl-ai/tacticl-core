package io.tacticl.business.conversation.dto;

public class MessageResponse {

    private String content;
    private String sessionStatus;
    private String sparkId;
    private String pipelineRunId;

    public MessageResponse(String content, String sessionStatus, String sparkId, String pipelineRunId) {
        this.content = content;
        this.sessionStatus = sessionStatus;
        this.sparkId = sparkId;
        this.pipelineRunId = pipelineRunId;
    }

    public String getContent() { return content; }
    public String getSessionStatus() { return sessionStatus; }
    public String getSparkId() { return sparkId; }
    public String getPipelineRunId() { return pipelineRunId; }
}
