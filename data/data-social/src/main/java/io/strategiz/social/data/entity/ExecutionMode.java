package io.strategiz.social.data.entity;

/** How a spark was actually executed (set during routing). */
public enum ExecutionMode {

	DEVICE,          // Executed on user's device
	CLOUD,           // Cloud execution (LLM tools only, no browser)
	CLOUD_BROWSER    // Cloud execution with browser automation

}
