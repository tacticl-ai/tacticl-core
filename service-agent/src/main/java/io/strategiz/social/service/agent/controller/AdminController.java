package io.strategiz.social.service.agent.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.business.agent.service.DataMigrationService;
import io.strategiz.social.business.agent.service.UserDataPurgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only controller for data operations (migration, purge). */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Admin-only data operations")
public class AdminController {

	private static final Logger log = LoggerFactory.getLogger(AdminController.class);

	private final DataMigrationService dataMigrationService;

	private final UserDataPurgeService userDataPurgeService;

	public AdminController(DataMigrationService dataMigrationService, UserDataPurgeService userDataPurgeService) {
		this.dataMigrationService = dataMigrationService;
		this.userDataPurgeService = userDataPurgeService;
	}

	@PostMapping("/migrate")
	@RequireAuth
	@Operation(summary = "Run data migration",
			description = "Migrates all flat collection data to subcollections under tacticl_users/{userId}/. "
					+ "Idempotent — safe to run multiple times. Does NOT delete flat data.")
	public ResponseEntity<Map<String, Object>> runMigration(@AuthUser AuthenticatedUser user) {
		log.info("Data migration triggered by user {}", user.getUserId());

		DataMigrationService.MigrationResult result = dataMigrationService.migrateAllUsers();

		return ResponseEntity.ok(Map.of(
				"status", "complete",
				"totalMigrated", result.getTotalMigrated(),
				"collections", result.getResults().stream().map(r -> Map.of(
						"collection", r.getCollection(),
						"documentsMigrated", r.getDocumentsMigrated()
				)).toList()
		));
	}

	@PostMapping("/migrate/{userId}")
	@RequireAuth
	@Operation(summary = "Run data migration for a single user",
			description = "Migrates a single user's flat collection data to subcollections.")
	public ResponseEntity<Map<String, Object>> runUserMigration(@PathVariable String userId,
			@AuthUser AuthenticatedUser user) {
		log.info("User migration triggered by {} for target user {}", user.getUserId(), userId);

		DataMigrationService.MigrationResult result = dataMigrationService.migrateUser(userId);

		return ResponseEntity.ok(Map.of(
				"status", "complete",
				"userId", userId,
				"totalMigrated", result.getTotalMigrated(),
				"collections", result.getResults().stream().map(r -> Map.of(
						"collection", r.getCollection(),
						"documentsMigrated", r.getDocumentsMigrated()
				)).toList()
		));
	}

	@PostMapping("/purge/{userId}")
	@RequireAuth
	@Operation(summary = "Purge all user data (GDPR)",
			description = "Deletes all data for a user across flat collections and subcollections. Irreversible.")
	public ResponseEntity<Map<String, Object>> purgeUserData(@PathVariable String userId,
			@AuthUser AuthenticatedUser user) {
		log.info("GDPR purge triggered by {} for target user {}", user.getUserId(), userId);

		UserDataPurgeService.PurgeResult result = userDataPurgeService.purgeAllUserData(userId);

		return ResponseEntity.ok(Map.of(
				"status", "complete",
				"userId", result.getUserId(),
				"totalDeleted", result.getTotalDeleted(),
				"collections", result.getResults().stream().map(r -> Map.of(
						"collection", r.getCollection(),
						"documentsDeleted", r.getDocumentsDeleted()
				)).toList()
		));
	}

}
