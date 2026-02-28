package io.strategiz.social.service.agent.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Request DTO for updating user configuration. All fields are optional (partial update). */
public class UpdateConfigRequest {

	private Integer maxConcurrentSparks;

	private BigDecimal spendingLimit;

	private List<String> domainAllowlist;

	private List<String> domainBlocklist;

	private Map<String, Integer> confirmationOverrides;

	public Integer getMaxConcurrentSparks() {
		return maxConcurrentSparks;
	}

	public void setMaxConcurrentSparks(Integer maxConcurrentSparks) {
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

	/** Convert non-null fields to a Map for UserConfigService.updateConfig(). */
	public Map<String, Object> toUpdateMap() {
		Map<String, Object> updates = new java.util.HashMap<>();
		if (maxConcurrentSparks != null) {
			updates.put("maxConcurrentSparks", maxConcurrentSparks);
		}
		if (spendingLimit != null) {
			updates.put("spendingLimit", spendingLimit);
		}
		if (domainAllowlist != null) {
			updates.put("domainAllowlist", domainAllowlist);
		}
		if (domainBlocklist != null) {
			updates.put("domainBlocklist", domainBlocklist);
		}
		if (confirmationOverrides != null) {
			updates.put("confirmationOverrides", confirmationOverrides);
		}
		return updates;
	}

}
