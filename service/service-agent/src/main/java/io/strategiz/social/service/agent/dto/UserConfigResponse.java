package io.strategiz.social.service.agent.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Response DTO for user configuration. */
public class UserConfigResponse {

	private int maxConcurrentSparks;

	private BigDecimal spendingLimit;

	private List<String> domainAllowlist;

	private List<String> domainBlocklist;

	private Map<String, Integer> confirmationOverrides;

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

}
