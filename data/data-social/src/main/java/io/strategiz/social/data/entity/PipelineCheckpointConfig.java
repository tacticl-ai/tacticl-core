package io.strategiz.social.data.entity;

import java.util.HashMap;
import java.util.Map;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/** Configures which pipeline stages require user approval checkpoints. */
@IgnoreExtraProperties
public class PipelineCheckpointConfig {

	private Map<String, CheckpointRule> roleCheckpoints = new HashMap<>();

	private boolean approveBeforeDeploy = true;

	private boolean approveRequirements = true;

	private boolean approveArchitecture = true;

	private boolean approveOnSecurityFindings = true;

	private int securityFindingSeverityThreshold = 2;

	private boolean autoApproveAll = false;

	public PipelineCheckpointConfig() {
	}

	public Map<String, CheckpointRule> getRoleCheckpoints() {
		return roleCheckpoints;
	}

	public void setRoleCheckpoints(Map<String, CheckpointRule> roleCheckpoints) {
		this.roleCheckpoints = roleCheckpoints;
	}

	public boolean isApproveBeforeDeploy() {
		return approveBeforeDeploy;
	}

	public void setApproveBeforeDeploy(boolean approveBeforeDeploy) {
		this.approveBeforeDeploy = approveBeforeDeploy;
	}

	public boolean isApproveRequirements() {
		return approveRequirements;
	}

	public void setApproveRequirements(boolean approveRequirements) {
		this.approveRequirements = approveRequirements;
	}

	public boolean isApproveArchitecture() {
		return approveArchitecture;
	}

	public void setApproveArchitecture(boolean approveArchitecture) {
		this.approveArchitecture = approveArchitecture;
	}

	public boolean isApproveOnSecurityFindings() {
		return approveOnSecurityFindings;
	}

	public void setApproveOnSecurityFindings(boolean approveOnSecurityFindings) {
		this.approveOnSecurityFindings = approveOnSecurityFindings;
	}

	public int getSecurityFindingSeverityThreshold() {
		return securityFindingSeverityThreshold;
	}

	public void setSecurityFindingSeverityThreshold(int securityFindingSeverityThreshold) {
		this.securityFindingSeverityThreshold = securityFindingSeverityThreshold;
	}

	public boolean isAutoApproveAll() {
		return autoApproveAll;
	}

	public void setAutoApproveAll(boolean autoApproveAll) {
		this.autoApproveAll = autoApproveAll;
	}

	/** Per-role checkpoint rule controlling when approvals are required. */
	@IgnoreExtraProperties
	public static class CheckpointRule {

		private boolean beforeRole;

		private boolean afterRole;

		private boolean onRejection;

		public CheckpointRule() {
		}

		public boolean isBeforeRole() {
			return beforeRole;
		}

		public void setBeforeRole(boolean beforeRole) {
			this.beforeRole = beforeRole;
		}

		public boolean isAfterRole() {
			return afterRole;
		}

		public void setAfterRole(boolean afterRole) {
			this.afterRole = afterRole;
		}

		public boolean isOnRejection() {
			return onRejection;
		}

		public void setOnRejection(boolean onRejection) {
			this.onRejection = onRejection;
		}

	}

}
