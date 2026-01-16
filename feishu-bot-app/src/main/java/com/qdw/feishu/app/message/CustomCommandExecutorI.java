package com.qdw.feishu.app.message;

import com.alibaba.cola.dto.Command;
import com.alibaba.cola.dto.CommandExecutorI;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 命令执行器接口
 * 
 * 用于应用层的扩展点定义
 */
@Command
public interface CustomCommandExecutorI<CMD> extends CommandExecutorI<CMD, Object> {
}
