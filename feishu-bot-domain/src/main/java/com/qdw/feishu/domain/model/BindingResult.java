package com.qdw.feishu.domain.model;

import lombok.Value;

/**
 * Result of a bind operation on ImContextBindingGateway.
 * 
 * Indicates what happened during the bind operation:
 * - created: a new binding was created
 * - updated: an existing binding was updated to point to a different session
 * - noChange: binding already existed and matched the target session
 */
@Value
public class BindingResult {
    
    /** A new binding was created */
    boolean created;
    
    /** An existing binding was updated to a different session */
    boolean updated;
    
    /** Binding already existed and matched the target (no-op) */
    boolean noChange;
    
    /** The resulting binding (may be new or existing) */
    ImContextBinding binding;
    
    /**
     * Create a result indicating a new binding was created.
     * 
     * @param binding the newly created binding
     * @return BindingResult with created=true
     */
    public static BindingResult created(ImContextBinding binding) {
        return new BindingResult(true, false, false, binding);
    }
    
    /**
     * Create a result indicating an existing binding was updated.
     * 
     * @param binding the updated binding
     * @return BindingResult with updated=true
     */
    public static BindingResult updated(ImContextBinding binding) {
        return new BindingResult(false, true, false, binding);
    }
    
    /**
     * Create a result indicating no change was needed.
     * 
     * @param binding the existing binding that already matched
     * @return BindingResult with noChange=true
     */
    public static BindingResult noChange(ImContextBinding binding) {
        return new BindingResult(false, false, true, binding);
    }
    
    /**
     * Check if any change was made.
     * 
     * @return true if created or updated
     */
    public boolean hasChange() {
        return created || updated;
    }
}
