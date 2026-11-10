package com.termux.app

import com.jarvisai.assistant.service.TermuxService as CoreTermuxService

/**
 * Direct subclass targeting component com.termux.app.TermuxService
 * for native compatibility with standard Termux scripts and wake locks.
 */
class TermuxService : CoreTermuxService()
