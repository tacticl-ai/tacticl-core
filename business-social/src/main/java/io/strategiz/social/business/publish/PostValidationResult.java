package io.strategiz.social.business.publish;

import java.util.ArrayList;
import java.util.List;

public class PostValidationResult {

	private boolean valid;

	private List<String> errors = new ArrayList<>();

	private List<String> warnings = new ArrayList<>();

	public static PostValidationResult valid() {
		PostValidationResult result = new PostValidationResult();
		result.valid = true;
		return result;
	}

	public static PostValidationResult invalid(List<String> errors) {
		PostValidationResult result = new PostValidationResult();
		result.valid = false;
		result.errors = errors;
		return result;
	}

	public boolean isValid() {
		return valid;
	}

	public List<String> getErrors() {
		return errors;
	}

	public List<String> getWarnings() {
		return warnings;
	}

	public void addWarning(String warning) {
		this.warnings.add(warning);
	}

}
