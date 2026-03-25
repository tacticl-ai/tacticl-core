package io.strategiz.social.data.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/** User-level configuration embedded in the TacticlUser document. */
@IgnoreExtraProperties
public class UserConfig {

	private int maxConcurrentSparks = 3;

	private BigDecimal spendingLimit = BigDecimal.ZERO;

	private List<String> domainAllowlist = new ArrayList<>();

	private List<String> domainBlocklist = new ArrayList<>();

	private Map<String, Integer> confirmationOverrides = new HashMap<>();

	private ExecutionPreference executionPreference = ExecutionPreference.DEVICE_FIRST;

	private BrowserSettings browserSettings;

	private int syncTimeoutMs = 3000;

	private boolean forceAsyncAll = false;

	private PipelineCheckpointConfig pipelineCheckpoints;

	private BigDecimal pipelineCostCeiling = new BigDecimal("50");

	private double costWarningThreshold = 0.8;

	public UserConfig() {
	}

	public static UserConfig defaults() {
		return new UserConfig();
	}

	public int getMaxConcurrentSparks() {
		return maxConcurrentSparks;
	}

	public void setMaxConcurrentSparks(int maxConcurrentSparks) {
		this.maxConcurrentSparks = maxConcurrentSparks;
	}

	public BigDecimal getSpendingLimit() {
		return spendingLimit;
	}

	public void setSpendingLimit(BigDecimal spendingLimit) {
		this.spendingLimit = spendingLimit;
	}

	public List<String> getDomainAllowlist() {
		return domainAllowlist;
	}

	public void setDomainAllowlist(List<String> domainAllowlist) {
		this.domainAllowlist = domainAllowlist;
	}

	public List<String> getDomainBlocklist() {
		return domainBlocklist;
	}

	public void setDomainBlocklist(List<String> domainBlocklist) {
		this.domainBlocklist = domainBlocklist;
	}

	public Map<String, Integer> getConfirmationOverrides() {
		return confirmationOverrides;
	}

	public void setConfirmationOverrides(Map<String, Integer> confirmationOverrides) {
		this.confirmationOverrides = confirmationOverrides;
	}

	public ExecutionPreference getExecutionPreference() {
		return executionPreference;
	}

	public void setExecutionPreference(ExecutionPreference executionPreference) {
		this.executionPreference = executionPreference;
	}

	public BrowserSettings getBrowserSettings() {
		return browserSettings;
	}

	public void setBrowserSettings(BrowserSettings browserSettings) {
		this.browserSettings = browserSettings;
	}

	public int getSyncTimeoutMs() {
		return syncTimeoutMs;
	}

	public void setSyncTimeoutMs(int syncTimeoutMs) {
		this.syncTimeoutMs = syncTimeoutMs;
	}

	public boolean isForceAsyncAll() {
		return forceAsyncAll;
	}

	public void setForceAsyncAll(boolean forceAsyncAll) {
		this.forceAsyncAll = forceAsyncAll;
	}

	public PipelineCheckpointConfig getPipelineCheckpoints() {
		return pipelineCheckpoints;
	}

	public void setPipelineCheckpoints(PipelineCheckpointConfig pipelineCheckpoints) {
		this.pipelineCheckpoints = pipelineCheckpoints;
	}

	public BigDecimal getPipelineCostCeiling() {
		return pipelineCostCeiling;
	}

	public void setPipelineCostCeiling(BigDecimal pipelineCostCeiling) {
		this.pipelineCostCeiling = pipelineCostCeiling;
	}

	public double getCostWarningThreshold() {
		return costWarningThreshold;
	}

	public void setCostWarningThreshold(double costWarningThreshold) {
		this.costWarningThreshold = costWarningThreshold;
	}

}
