package io.tacticl.browser.data.repository;

import com.google.cloud.firestore.Firestore;
import io.tacticl.browser.data.entity.UserFile;
import io.strategiz.social.data.repository.FirestoreRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Repository for user_files Firestore collection. */
@Repository
public class UserFileRepository extends FirestoreRepository<UserFile> {

	public UserFileRepository(Firestore firestore) {
		super(firestore, UserFile.class, "user_files");
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
