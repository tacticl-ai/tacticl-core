package io.strategiz.social.business.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.data.entity.Reminder;
import io.strategiz.social.data.repository.ReminderRepository;
import com.google.cloud.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill to set a reminder for the user. Tier 0: auto-execute. */
@Component
public class SetReminderSkill implements AgentSkill {

	private static final Logger log = LoggerFactory.getLogger(SetReminderSkill.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final ReminderRepository reminderRepository;

	public SetReminderSkill(ReminderRepository reminderRepository) {
		this.reminderRepository = reminderRepository;
	}

	@Override
	public String getName() {
		return "set_reminder";
	}

	@Override
	public String getDescription() {
		return "Set a reminder for the user at a specific date and time";
	}

	@Override
	public ToolDefinition getToolDefinition() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode message = properties.putObject("message");
		message.put("type", "string");
		message.put("description", "The reminder message");

		ObjectNode remindAt = properties.putObject("remind_at");
		remindAt.put("type", "string");
		remindAt.put("description", "When to send the reminder (ISO-8601 datetime, e.g. 2025-01-15T14:30:00Z)");

		schema.putArray("required").add("message").add("remind_at");

		return new ToolDefinition(getName(), getDescription(), schema);
	}

	@Override
	public String execute(JsonNode input, String userId) {
		String message = input.get("message").asText();
		String remindAtStr = input.get("remind_at").asText();

		Instant remindAt;
		try {
			remindAt = Instant.parse(remindAtStr);
		}
		catch (DateTimeParseException e) {
			return "Invalid date/time format: " + remindAtStr + ". Please use ISO-8601 format (e.g. 2025-01-15T14:30:00Z).";
		}

		Reminder reminder = new Reminder();
		reminder.setUserId(userId);
		reminder.setMessage(message);
		reminder.setRemindAt(remindAt);
		reminder.setDelivered(false);
		reminder.setCreatedDate(Timestamp.now());

		try {
			reminderRepository.save(userId, reminder, null);
			log.info("Reminder set for user {} at {}: {}", userId, remindAt, message);
			return "Reminder set for " + remindAt + ": " + message;
		}
		catch (Exception e) {
			log.error("Failed to set reminder for user {}: {}", userId, e.getMessage(), e);
			return "Failed to set reminder: " + e.getMessage();
		}
	}

	@Override
	public int getConfirmationTier() {
		return 0;
	}

}
