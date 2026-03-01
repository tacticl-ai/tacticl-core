package io.tacticl.browser.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tacticl.browser")
public class BrowserProperties {

	private boolean enabled = false;

	private int maxConcurrentContexts = 3;

	private int ephemeralTimeoutSeconds = 60;

	private int persistentIdleTimeoutSeconds = 300;

	private int pageLoadTimeoutSeconds = 30;

	private int maxPagesPerContext = 5;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getMaxConcurrentContexts() {
		return maxConcurrentContexts;
	}

	public void setMaxConcurrentContexts(int maxConcurrentContexts) {
		this.maxConcurrentContexts = maxConcurrentContexts;
	}

	public int getEphemeralTimeoutSeconds() {
		return ephemeralTimeoutSeconds;
	}

	public void setEphemeralTimeoutSeconds(int ephemeralTimeoutSeconds) {
		this.ephemeralTimeoutSeconds = ephemeralTimeoutSeconds;
	}

	public int getPersistentIdleTimeoutSeconds() {
		return persistentIdleTimeoutSeconds;
	}

	public void setPersistentIdleTimeoutSeconds(int persistentIdleTimeoutSeconds) {
		this.persistentIdleTimeoutSeconds = persistentIdleTimeoutSeconds;
	}

	public int getPageLoadTimeoutSeconds() {
		return pageLoadTimeoutSeconds;
	}

	public void setPageLoadTimeoutSeconds(int pageLoadTimeoutSeconds) {
		this.pageLoadTimeoutSeconds = pageLoadTimeoutSeconds;
	}

	public int getMaxPagesPerContext() {
		return maxPagesPerContext;
	}

	public void setMaxPagesPerContext(int maxPagesPerContext) {
		this.maxPagesPerContext = maxPagesPerContext;
	}

}
