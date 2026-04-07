# Research Summary: OpenCode Interactive Flow Redesign

**Domain:** Chatbot conversation flow management (state machine, routing, context binding)
**Researched:** 2026-04-07
**Overall confidence:** HIGH

## Executive Summary

The OpenCode interactive flow redesign addresses 6 documented broken points in how a Feishu chatbot manages conversation state, command routing, and context binding for an AI coding assistant. After analyzing the existing codebase (80 domain files, dual state detection systems, fragile text-parsing of session IDs, 4x redundant DB queries per message) and researching current patterns for chatbot state management in Java/Spring, the recommended approach is **evolutionary, not revolutionary**: use patterns already available in Java 17 + Spring Boot 3.2.1 without adding any new dependencies.

The core finding is that Spring Statemachine (the obvious candidate) is overkill for this use case. The state space is 5-6 states with simple transitions. SSM's factory + persister + restore cycle per message adds significant machinery, introduces framework coupling in the domain layer (violating COLA), and solves problems we don't have (hierarchical states, parallel regions, event-driven transitions). Instead, a hand-rolled enum-based state derivation — where state is a pure function of persisted binding data — is simpler, testable, and fits the COLA architecture.

The second critical finding is that the performance problem (redundant DB queries) and the context mismatch bug (chatId→threadId migration) are both solved by the same pattern: resolve a `MessageContext` object once at the top of the processing pipeline and pass it explicitly through all layers. This eliminates the need for each component to independently query the database for binding state.

The third finding is that command routing should use a strategy map pattern rather than the current monolithic switch statement. Each sub-command handler declares its allowed states, eliminating the need for a separate `CommandWhitelist` system. This co-locates the "what" and "when" for each command, making drift between routing and validation impossible.

## Key Findings

**Stack:** No new dependencies needed. All patterns implementable with Java 17 enums + Spring `@Component` auto-discovery + existing SQLite gateways.
**Architecture:** Single `MessageContext` resolved once per message → eliminates 4x DB queries → feeds unified `ConversationState` → drives both routing and validation.
**Critical pitfall:** Spring Statemachine is a tempting but wrong choice — it adds complexity for simple state spaces and violates COLA domain layer purity.

## Implications for Roadmap

Based on research, suggested phase structure:

1. **Unified State Model** — Build `ConversationState` enum + `MessageContext` resolver
   - Addresses: R1 (unified state model), performance bottleneck (redundant queries)
   - Avoids: Dual-state confusion, 4x DB query overhead
   - Rationale: Everything else depends on having a single, correct state representation

2. **Context Binding Migration** — Fix chatId→threadId migration + structured session ID passing
   - Addresses: R2 (context-aware binding), R10 (robust session ID passing)
   - Avoids: Context mismatch bug, fragile text parsing
   - Rationale: Must fix the data flow before redesigning the command flow

3. **Command Router Redesign** — Sub-command handler registry with state guards
   - Addresses: R9 (clean command set), R5 (chatnow executes prompt)
   - Avoids: Monolithic switch statement, separate whitelist drift
   - Rationale: Depends on unified state model from Phase 1

4. **Direct Typing + Reply Suppression** — Plain text as chat in active sessions + null return for async
   - Addresses: R3 (direct typing), R4 (suppress empty replies), R6 (manual control flow)
   - Avoids: Ghost bubbles, status-instead-of-chat bug
   - Rationale: Depends on correct state detection (Phase 1) and context binding (Phase 2)

5. **Card + Command Dual Entry** — Card button actions carry conversation context
   - Addresses: R7 (card + command dual entry), R8 (group→topic model)
   - Avoids: Card actions with null topicId/chatId
   - Rationale: Requires all prior phases to be stable

**Phase ordering rationale:**
- State model is foundational — every other change queries "what state am I in?"
- Context binding fixes must precede command redesign — commands that create sessions need correct migration
- Plain text routing requires stable state detection — routing non-command text depends on knowing we're in TOPIC_ACTIVE_SESSION
- Card integration is last because it's secondary and requires stable state + binding + routing

**Research flags for phases:**
- Phase 1: Standard patterns, unlikely to need further research
- Phase 2: May need investigation into Feishu SDK's `SendResult.getThreadId()` reliability — does it always contain the thread ID when a new topic is created?
- Phase 3: Standard patterns, unlikely to need further research
- Phase 5: Likely needs deeper research into Feishu card callback payload structure — what context data (chatId, topicId) is available in `P2CardActionTrigger`?

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| State machine pattern | HIGH | Strong evidence against SSM; enum approach well-understood |
| Context propagation | HIGH | Explicit parameter passing is industry standard for non-HTTP contexts |
| Command routing | HIGH | Strategy map is natural evolution of existing Spring `@Component` patterns |
| Context binding migration | HIGH | Bug is well-documented; fix approach is mechanical |
| Plain text routing | HIGH | Fix location is clear from codebase analysis |
| Card integration | MEDIUM | Card callback payload structure needs verification against Feishu SDK 2.5.2 |

## Gaps to Address

- Feishu SDK 2.5.2 card action callback: what fields does `P2CardActionTrigger` actually expose? Current `CardCommandAdapter` extracts only `openId` and `messageId`. Need to verify if `chatId` is available.
- SQLite concurrent access: the `InsertOrReplace` approach for bind migration needs testing under concurrent messages to the same context.
- Session cleanup: the `expires_at` column exists but is never populated. Phase-specific research needed on TTL strategy if session count grows.

---

*Summary completed: 2026-04-07*
