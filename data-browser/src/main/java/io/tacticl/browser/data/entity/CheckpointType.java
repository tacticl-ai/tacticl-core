package io.tacticl.browser.data.entity;

/** Types of checkpoints that pause browser execution for user review. */
public enum CheckpointType {

	ACTION_CONFIRMATION, LOGIN_REQUIRED, CAPTCHA_DETECTED, PURCHASE_CONFIRMATION, DOWNLOAD_APPROVAL,
	BROWSER_ERROR, AMBIGUOUS_STATE

}
