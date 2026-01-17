package com.qdw.feishu.domain.gateway;

import java.util.Map;

/**
 * 飞书 Webhook 验证器接口
 *
 * 负责验证飞书平台推送的事件请求是否合法
 */
public interface WebhookValidator {

    /**
     * 验证 Webhook 请求的签名
     *
     * @param headers HTTP 请求头（包含签名、时间戳、nonce等）
     * @param body 请求体内容
     * @return 验证结果对象
     */
    WebhookValidationResult validate(Map<String, String> headers, String body);

    /**
     * Webhook 验证结果
     */
    class WebhookValidationResult {
        private final boolean valid;
        private final String error;

        public WebhookValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }

        public static WebhookValidationResult success() {
            return new WebhookValidationResult(true, null);
        }

        public static WebhookValidationResult failure(String error) {
            return new WebhookValidationResult(false, error);
        }

        public boolean isValid() {
            return valid;
        }

        public String getError() {
            return error;
        }
    }
}
