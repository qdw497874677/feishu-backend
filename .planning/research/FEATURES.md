# Feature Landscape

**Domain:** AI coding assistant interactive flow embedded in Feishu/Lark group chat
**Researched:** 2026-04-06
**Overall confidence:** HIGH (grounded in codebase analysis, industry patterns, and known broken points)

## Table Stakes

Features users expect. Missing = product feels incomplete or broken.

### Session Management

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Explicit session binding** — user picks project + session + binds to topic | Users of AI coding tools (ChatGPT, Claude, Cursor) expect to control which conversation context they're in. Implicit/auto-magic binding caused 3 of the 6 current broken points. | Med | Replaces current chatnow auto-magic. The R6 "manual control" requirement. Must work for both new and existing sessions. |
| **Session persistence across messages** — once bound, the topic stays bound until explicitly unbound | Every multi-turn chat product preserves context across messages. Losing binding mid-conversation (the chatId→threadId mismatch bug) is a show-stopper. | Med | Core fix for the context mismatch bug (CONCERNS.md #1). Binding must attach to threadId once known, not just chatId. |
| **Plain text as chat in bound topics** — no `/oc chat` prefix needed | ChatGPT, Claude, Cursor, and every conversational AI lets you just type. Requiring a command prefix for every message breaks conversational flow. | Low | Fix for CONCERNS.md "plain text shows status" bug. When topic is bound+initialized, treat all non-command text as chat prompt. |
| **Session status visibility** — user can see what session/project is bound to current topic | Users need to know "where am I?" at any point. Without status, users send messages into the wrong session or wonder why responses are unexpected. | Low | Already partially exists (`/oc session status`). Needs to be reliable and easily discoverable. |
| **Unbind/reset** — cleanly disconnect a topic from its session | Every binding operation needs an undo. Users must be able to detach and rebind without creating a new topic. | Low | `/oc reset` exists but needs to work reliably with the new unified state model. |
| **Suppress ghost bubbles** — no empty reply messages | Empty/blank messages in a chat thread look like bugs. Every production chat system either sends a real message or sends nothing. | Low | Fix: return `null` not `""` from async task paths. The existing `sendReply()` already handles null correctly. |

### Command Routing

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Single unified state model** — one state detection path, not two | This is an infrastructure-level table stake for developers maintaining the system. Dual state detection (TopicState vs ContextSessionState) causes redundant DB queries and inconsistent behavior. | Med | R1 requirement. Consolidate to ContextSessionState-based detection; TopicState becomes a derived view. |
| **Command whitelist per state** — only show/allow commands valid for current state | Users typing invalid commands and getting cryptic errors is a broken experience. Commands should either work or explain why they don't. | Low | Already exists but needs to align with the new unified state model. The whitelist must include aliases. |
| **Case-insensitive commands** — `/OC`, `/oc`, `/Oc` all work | Already implemented system-wide. Would be a regression if lost. | Low | Existing behavior to preserve. |
| **Command aliases** — `/oc` = `/opencode` = `/code` | Power users expect shortcuts. Already implemented. | Low | Existing behavior to preserve. |

### Error Recovery

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Actionable error messages** — errors tell user what to do, not just what went wrong | Industry standard UX pattern (NN/g, Mind the Product research). "Command not available" should include "Try these instead: ..." | Low | Partially exists. Needs consistency across all error paths. |
| **Graceful degradation for old/unbound topics** — old topics show help instead of crashing | Users will inevitably visit old topics. Silent crash or confusing state is unacceptable. | Low | R7 in PROJECT.md: "old contexts degrade silently to help." |
| **OpenCode server connectivity feedback** — clear message when backend is down | Users need to distinguish "I did something wrong" from "the service is unavailable." | Low | `/oc connect` exists. Needs to be surfaced automatically when server is unreachable, not just on manual check. |

### Conversation UX

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Streaming responses** — real-time token-by-token output via cards | Every modern AI coding assistant streams responses. Users expect to see output as it generates, not wait for completion. | Already built | SSE + CardKit streaming already implemented. Table stake to preserve. |
| **Group chat → topic thread model** — setup in group chat, conversation in topic | Feishu/Lark's native model is group chat + topic threads. Fighting this creates confusion. Selection/setup in main chat, conversation in threads. | Med | R8 requirement. Natural fit for Feishu platform. |
| **Message deduplication** — same event processed only once | WebSocket can deliver duplicates. Without dedup, users see double responses. | Already built | eventId-based deduplication exists and works. |

---

## Differentiators

Features that set the product apart. Not expected by users but create noticeable value when present.

### Guided Flows

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Interactive card buttons for project/session selection** — clickable cards instead of typed commands | Eliminates the need to remember/type session IDs. Discoverability jumps dramatically. Feishu cards support buttons, dropdowns, and structured interactions. | Med-High | R7 "Card + command dual entry." Cards for visual users, commands for power users. Requires fixing the card context bug (CONCERNS.md: card actions lack chatId/topicId). |
| **Step-by-step guided onboarding flow** — first interaction walks user through setup | New users don't know the command set. A card-based wizard (pick project → pick session → confirm bind) reduces time-to-first-conversation from 4+ commands to 2-3 clicks. | Med | Progressive disclosure pattern: show only what's needed at each step. Industry best practice from chatbot UX research. |
| **Contextual next-step suggestions** — after each action, suggest what to do next | After binding, suggest "Now type your question." After error, suggest the fix. Eliminates the "what do I do now?" dead ends. | Low | Already partially present in help text. Needs systematic application to ALL response paths. |

### Session Management (Advanced)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Session list with recent activity** — show last prompt/timestamp per session | Users can identify sessions by context, not by opaque `ses_xxx` IDs. "The one where I was refactoring login" vs "ses_abc123". | Low-Med | Already partially exists in `/oc sessions <project>`. Enhance with last prompt snippet and relative timestamps. |
| **Quick-resume last session** — single command to rebind the most recent session | For the common case: "I want to continue where I left off." Reduces 3-step flow to 1 command/click. | Low | Can be implemented as `/oc resume` or a card button "Continue last session." |
| **Structured session ID passing** — IDs as method parameters, not parsed from markdown | Eliminates the fragile text-parsing bug (CONCERNS.md: session ID extracted from formatted reply text). Makes the system reliable and testable. | Med | R10 requirement. Not user-visible but prevents silent binding failures that users experience as "it just stopped working." |

### Conversation UX (Advanced)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **chatnow actually works** — creates session AND forwards the prompt | The promised "one-step quick start" actually working. `/oc cn write a sort function` creates session, binds topic, AND sends the prompt. Current version only creates the session. | Med | Fix for CONCERNS.md chatnow bug. High perceived value: the "just works" experience. |
| **Request-scoped context caching** — resolve binding once per message, thread through pipeline | Eliminates 3-4 redundant SQLite queries per message. User-invisible but affects response latency. | Med | Performance improvement from CONCERNS.md. Not a feature per se, but affects perceived speed. |
| **Topic state indicator in responses** — subtle badge/label showing current state | Users always know if they're in an unbound topic, bound topic, or main chat. Reduces "why isn't it responding to my text?" confusion. | Low | Could be a header line in bot responses: "📎 Connected to: feishu-backend (ses_abc123)" |

---

## Anti-Features

Features to explicitly NOT build. These seem helpful but create complexity, confusion, or maintenance burden.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| **Auto-detect project from message content** | Unreliable heuristics. User mentions "feishu" — is that the project name or a topic? Creates false confidence and silent misrouting. PROJECT.md explicitly marks this out of scope. | User explicitly selects project from a list. Clear, predictable, zero ambiguity. |
| **Cross-project switching within one topic** | Mixing two project contexts in one thread creates confusing conversation history. "Was that response about project A or B?" Mental model breaks down. | One topic = one project. Want to work on a different project? Open a new topic. Simple, clean. |
| **Auto-migrate old topic bindings** | Migration code is complex, has edge cases, and old bindings may reference deleted sessions. Maintenance burden far exceeds value. | Old topics silently degrade to help text. Users create new topics for new work anyway. |
| **Generic session framework** | Only OpenCode needs session awareness. Building a framework for one consumer is over-engineering. YAGNI. | Keep session management inside OpenCode domain. If another app needs sessions someday, extract then. |
| **Webhook communication mode** | Project iron rule: WebSocket long-connection only. Webhook requires public IP, domain, SSL — completely different deployment model. | Never. WebSocket only. |
| **User-level authentication** | Feishu platform identity (openId) is sufficient. Adding auth adds login flow, token management, permissions — all unnecessary complexity. | Use Feishu openId as user identity. The platform already handles auth. |
| **Automatic session creation on plain text** | If a user types plain text in an unbound topic, auto-creating a session hides the project selection step. User doesn't know which project context they're in. | Show a clear guide: "This topic isn't connected to a project yet. Use `/oc projects` to get started." |
| **Natural language command parsing** | "Hey, can you connect to the feishu project?" → parsing this is fragile, multilingual, and unpredictable. Commands must be unambiguous. | Structured commands with clear syntax. `/oc bind ses_xxx` is unambiguous. Plain text in bound topics = chat prompt. |
| **Conversation history across session rebinding** | If user unbinds session A and binds session B, showing A's history creates confusion about which session generated which response. | Each session binding is clean. Old messages remain in the topic thread (Feishu preserves them), but bot context resets. |
| **Multi-user session sharing** | Two users in the same topic bound to the same session could create conflicting commands and confusing interleaved responses. | One binding per topic, first-come-first-served. Second user sees status but uses their own topic for interaction. |

---

## Feature Dependencies

```
                    Unified State Model (R1)
                    /                    \
                   v                      v
    Command Whitelist per State    Plain Text as Chat (R3)
              |                           |
              v                           v
    Interactive Card Buttons (R7)   Ghost Bubble Suppression (R4)
              |
              v
    Guided Onboarding Flow
              |
              v
    Contextual Next-Step Suggestions

    Explicit Session Binding (R6)
              |
              +---> Session Persistence (R2: context migration fix)
              |
              +---> chatnow Executes Prompt (R5)
              |
              +---> Structured Session ID Passing (R10)
              |
              v
    Quick-Resume Last Session

    Card Context Fix (chatId/topicId in card events)
              |
              v
    Interactive Card Buttons (R7)
              |
              v
    Card-Based Project/Session Selection
```

### Critical Path

1. **Unified State Model** must come first — everything depends on consistent state detection
2. **Session binding + context migration** comes second — fixes the core broken flow
3. **Plain text as chat + ghost bubble suppression** — enables the natural conversation UX
4. **Card context fix** is prerequisite for interactive card features
5. **Guided flows and polish** layer on top once the foundation is solid

---

## MVP Recommendation

**Prioritize (Phase 1 — "Make it work"):**

1. ✅ Unified state model (R1) — eliminates dual-state confusion, reduces DB queries
2. ✅ Session binding with context migration (R2) — fixes the chatId→threadId mismatch
3. ✅ Plain text as chat in bound topics (R3) — core conversational UX
4. ✅ Ghost bubble suppression (R4) — `null` not `""`, trivial fix
5. ✅ chatnow executes prompt (R5) — fixes the documented-but-broken one-step flow
6. ✅ Structured session ID passing (R10) — eliminates fragile text parsing

**Prioritize (Phase 2 — "Make it smooth"):**

7. ✅ Clean command set redesign (R9) — commands aligned with the new manual-control flow
8. ✅ Actionable error messages — every error path includes next steps
9. ✅ Contextual next-step suggestions — systematic "what to do now" in all responses
10. ✅ Group chat → topic thread model (R8) — setup in main chat, conversation in threads

**Defer (Phase 3 — "Make it delightful"):**

11. Interactive card buttons for selection (R7) — requires card context fix first
12. Step-by-step guided onboarding wizard
13. Quick-resume last session
14. Session list with activity snippets
15. Topic state indicator in responses
16. Request-scoped context caching (performance)

**Rationale for ordering:**
- Phase 1 fixes all 6 documented broken points. This is the minimum for a working product.
- Phase 2 polishes the command-based experience and makes it self-guiding.
- Phase 3 adds visual/interactive enhancements that require the card event pipeline fix — a separate infrastructure concern that shouldn't block the core flow.

**Defer indefinitely:**
- All anti-features listed above. The manual-control principle means NO auto-magic.

---

## Sources

- **Codebase analysis:** `.planning/codebase/ARCHITECTURE.md`, `.planning/codebase/CONCERNS.md` — primary source for broken points and technical constraints (HIGH confidence)
- **Project requirements:** `.planning/PROJECT.md` — R1 through R10 requirements, out-of-scope decisions (HIGH confidence)
- **Industry UX patterns:** Mind the Product "Nine UX best practices for AI chatbots" (2024) — context-aware design, expectation management, error handling, feedback loops (MEDIUM confidence)
- **Chatbot interface design:** Fuselab Creative "Chatbot Interface Design Guide" (2026) — core design patterns, typing indicators, quick reply buttons, fallback mechanisms, progressive disclosure (MEDIUM confidence)
- **AI coding assistant context:** Standard Beagle "The crisis of context" (2026) — context binding challenges, needle-in-haystack problem, context-loop design pattern (MEDIUM confidence)
- **Existing app documentation:** `APP_USAGE_GUIDE.md`, `APP_GUIDE.md` — current command set, documented flows, known UX promises (HIGH confidence)
- **Feishu platform model:** Feishu/Lark group chat + topic thread model is the native interaction paradigm; card interactions via P2CardActionTrigger (HIGH confidence, observed in codebase)
