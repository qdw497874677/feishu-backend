package com.qdw.feishu.domain.message;

import com.qdw.feishu.domain.app.FishuAppI;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Domain routing decision for an incoming message.
 * Contains only generic routing outcome, no execution side effects.
 */
@Data
@AllArgsConstructor
public class BotRoutingDecision {

    private String appId;
    private FishuAppI app;
    private boolean persistBinding;

    public boolean shouldPersistBinding() {
        return persistBinding;
    }
}
