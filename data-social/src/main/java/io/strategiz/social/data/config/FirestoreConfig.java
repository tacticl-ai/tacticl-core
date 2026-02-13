package io.strategiz.social.data.config;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures Google Cloud Firestore client for the tacticl GCP project. */
@Configuration
public class FirestoreConfig {

	private static final Logger logger = LoggerFactory.getLogger(FirestoreConfig.class);

	@Value("${tacticl.firestore.project-id:tacticl}")
	private String projectId;

	@Bean
	public Firestore firestore() {
		logger.info("Initializing Firestore for project: {}", projectId);
		FirestoreOptions options = FirestoreOptions.newBuilder().setProjectId(projectId).build();
		return options.getService();
	}

}
