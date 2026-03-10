package io.strategiz.social.data.entity;

/** Execution engine for device spark processing. Desktop devices default to CLAUDE_CODE. */
public enum ExecutionEngine {

	/** Claude Code Agent SDK — full agentic execution with file/bash/web/MCP/subagents. */
	CLAUDE_CODE,

	/** Existing command-based daemon protocol (TERMINAL_CMD, OPEN_URL, etc.). */
	LEGACY,

	/** Choose engine per-spark based on type and complexity. */
	AUTO

}
