package io.strategiz.social.data.entity;

import java.util.ArrayList;
import java.util.List;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/** Browser-specific settings embedded in UserConfig. */
@IgnoreExtraProperties
public class BrowserSettings {

	private List<String> domainAllowlist = new ArrayList<>();

	private List<String> domainBlocklist = new ArrayList<>();

	private List<String> autoBlockCategories = new ArrayList<>();

	private boolean allowFileDownloads = true;

	private boolean allowFileUploads = true;

	private long maxFileSize = 52428800; // 50MB

	private int maxSpendPerAction = 0;

	public BrowserSettings() {
	}

	public static BrowserSettings defaults() {
		return new BrowserSettings();
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

	public List<String> getAutoBlockCategories() {
		return autoBlockCategories;
	}

	public void setAutoBlockCategories(List<String> autoBlockCategories) {
		this.autoBlockCategories = autoBlockCategories;
	}

	public boolean isAllowFileDownloads() {
		return allowFileDownloads;
	}

	public void setAllowFileDownloads(boolean allowFileDownloads) {
		this.allowFileDownloads = allowFileDownloads;
	}

	public boolean isAllowFileUploads() {
		return allowFileUploads;
	}

	public void setAllowFileUploads(boolean allowFileUploads) {
		this.allowFileUploads = allowFileUploads;
	}

	public long getMaxFileSize() {
		return maxFileSize;
	}

	public void setMaxFileSize(long maxFileSize) {
		this.maxFileSize = maxFileSize;
	}

	public int getMaxSpendPerAction() {
		return maxSpendPerAction;
	}

	public void setMaxSpendPerAction(int maxSpendPerAction) {
		this.maxSpendPerAction = maxSpendPerAction;
	}

}
