package com.qdw.feishu.domain.session;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 类型令牌（解决泛型类型擦除）
 * 
 * 使用方式：
 * <pre>
 * private static final TypeToken<GameData> GAME_DATA_TYPE = new TypeToken<GameData>() {};
 * </pre>
 */
@Slf4j
public abstract class TypeToken<T> {
    
    private final Type type;
    
    protected TypeToken() {
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof ParameterizedType) {
            this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
        } else {
            throw new RuntimeException("TypeToken must be created with type parameter, e.g., new TypeToken<GameData>() {}");
        }
    }
    
    /**
     * 获取泛型类型
     */
    public Type getType() {
        return type;
    }
    
    /**
     * 获取原始类型（Class）
     */
    @SuppressWarnings("unchecked")
    public Class<? super T> getRawType() {
        if (type instanceof Class) {
            return (Class<? super T>) type;
        }
        if (type instanceof ParameterizedType) {
            return (Class<? super T>) ((ParameterizedType) type).getRawType();
        }
        throw new RuntimeException("Cannot determine raw type for: " + type);
    }
    
    @Override
    public String toString() {
        return "TypeToken{" + type + "}";
    }
}
