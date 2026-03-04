package io.strategiz.social.data.entity;

import java.time.Instant;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.identity.data.base.annotation.Collection;
import io.cidadel.identity.data.base.entity.BaseEntity;

/** Represents a user's granted access to a source code repository. */
@IgnoreExtraProperties
@Collection("repo_grants")
public class RepoGrant extends BaseEntity {

	private String id;

	private String userId;

	private RepoProvider provider;

	private String repoFullName;

	private AccessLevel accessLevel;

	private Instant grantedAt;

	private String oauthTokenRef;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public RepoProvider getProvider() {
		return provider;
	}

	public void setProvider(RepoProvider provider) {
		this.provider = provider;
	}

	public String getRepoFullName() {
		return repoFullName;
	}

	public void setRepoFullName(String repoFullName) {
		this.repoFullName = repoFullName;
	}

	public AccessLevel getAccessLevel() {
		return accessLevel;
	}

	public void setAccessLevel(AccessLevel accessLevel) {
		this.accessLevel = accessLevel;
	}

	public Instant getGrantedAt() {
		return grantedAt;
	}

	public void setGrantedAt(Instant grantedAt) {
		this.grantedAt = grantedAt;
	}

	public String getOauthTokenRef() {
		return oauthTokenRef;
	}

	public void setOauthTokenRef(String oauthTokenRef) {
		this.oauthTokenRef = oauthTokenRef;
	}

}
