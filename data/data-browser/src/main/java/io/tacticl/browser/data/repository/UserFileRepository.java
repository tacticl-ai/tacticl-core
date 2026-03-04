package io.tacticl.browser.data.repository;

import com.google.cloud.firestore.Firestore;
import io.cidadel.identity.data.base.audit.FirestoreAuditingHandler;
import io.cidadel.identity.data.base.repository.BaseRepository;
import io.tacticl.browser.data.entity.UserFile;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for user_files Firestore collection. */
@Repository
public class UserFileRepository extends BaseRepository<UserFile> {

	public UserFileRepository(Firestore firestore, FirestoreAuditingHandler auditingHandler) {
		super(firestore, UserFile.class, auditingHandler);
	}

	@Override
	protected String getModuleName() {
		return "data-browser";
	}

	/** Find all files for a user. */
	public List<UserFile> findByUserId(String userId) {
		return findByField("userId", userId);
	}

	/** Find all files for a spark. */
	public List<UserFile> findBySparkId(String sparkId) {
		return findByField("sparkId", sparkId);
	}

}
