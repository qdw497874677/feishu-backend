package com.qdw.feishu.domain.message;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Domain routing decision for an incoming message.
 * Contains only generic routing outcome, no execution side effects.
 * Pure data: appId + persistBinding flag.
 */
@Data
@AllArgsConstructor
public class BotRoutingDecision {

    private String appId;
    private boolean persistBinding;

    public boolean shouldPersistBinding() {
        return persistBinding;
    }
}
