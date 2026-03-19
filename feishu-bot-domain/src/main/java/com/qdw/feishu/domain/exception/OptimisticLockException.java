package com.qdw.feishu.domain.exception;

/**
 * 乐观锁异常
 * 
 * 当会话更新时版本号不匹配抛出
 */
public class OptimisticLockException extends RuntimeException {
    
    private final long expectedVersion;
    private final long actualVersion;
    
    public OptimisticLockException(String message, long expectedVersion, long actualVersion) {
        super(message);
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }
    
    public OptimisticLockException(long expectedVersion, long actualVersion) {
        this(String.format("Version conflict: expected=%d, actual=%d", expectedVersion, actualVersion),
             expectedVersion, actualVersion);
    }
    
    public long getExpectedVersion() {
        return expectedVersion;
    }
    
    public long getActualVersion() {
        return actualVersion;
    }
}
