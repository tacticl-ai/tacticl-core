package io.tacticl.data.telegram.entity;

public class NotificationPrefs {

    private boolean checkpoints = true;
    private boolean progress = false;
    private boolean completion = true;
    private boolean failures = true;

    public boolean isCheckpoints() { return checkpoints; }
    public void setCheckpoints(boolean checkpoints) { this.checkpoints = checkpoints; }

    public boolean isProgress() { return progress; }
    public void setProgress(boolean progress) { this.progress = progress; }

    public boolean isCompletion() { return completion; }
    public void setCompletion(boolean completion) { this.completion = completion; }

    public boolean isFailures() { return failures; }
    public void setFailures(boolean failures) { this.failures = failures; }
}
