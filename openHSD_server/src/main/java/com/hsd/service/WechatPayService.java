package com.hsd.service;

import com.alibaba.fastjson2.JSONObject;
import com.hsd.service.dto.PaymentResult;
import com.hsd.service.enums.PayType;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

public interface WechatPayService extends PaymentService {

    @Override
    default String getChannel() {
        return "wechat";
    }

    @Deprecated
    String createNativeOrder(String orderNo, int amountCents, String description) throws Exception;

    @Deprecated
    boolean verifyNotify(String timestamp, String nonce, String body, String signature);

    @Deprecated
    JSONObject parseNotifyBody(String body);

    @Deprecated
    Map<String, String> buildSuccessResponse();

    @Deprecated
    boolean isMockMode();
}
