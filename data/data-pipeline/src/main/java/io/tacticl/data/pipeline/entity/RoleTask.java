package io.tacticl.data.pipeline.entity;

/**
 * One Planner-enumerated task for a downstream role (Slice 3 task-plan passthrough).
 *
 * <p>The PLANNER persona writes {@code results/tasks.json} keyed by downstream role/step type;
 * the arbiter forwards it as {@code resultJson.tasks} on the planner's {@code agent_completed}
 * callback. tacticl-core stores each role's ordered task titles here as a write-through
 * projection — {@code status} defaults to {@code "pending"} (the UI derives display state from
 * the ROLE's overall status until live per-task updates exist).
 *
 * <p>Mutable POJO (no-arg + setters) so Spring Data Mongo can hydrate it from the embedded
 * sub-document; pure data carrier with no behaviour.
 */
public class RoleTask {

    private String title;
    private String status;

    protected RoleTask() {}

    public static RoleTask pending(String title) {
        RoleTask t = new RoleTask();
        t.title = title;
        t.status = "pending";
        return t;
    }

    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public void setTitle(String title) { this.title = title; }
    public void setStatus(String status) { this.status = status; }
}
