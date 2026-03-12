package com.hsd.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 附件数据传输对象
 *
 * 在数据库中存储：objectKey（MinIO 对象路径），name，type
 * 返回给前端时：objectKey 已替换为预签名 URL（url 字段）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDTO {

    /**
     * MinIO 对象路径，例如 1/claw_xxx/msg_xxx/1709123456789_0.png
     * 存储到数据库时使用，不暴露给前端
     */
    private String objectKey;

    /**
     * 对外访问 URL（预签名 URL），仅在返回历史消息时填充
     * 数据库中不存此字段，为 null
     */
    private String url;

    /** 文件原始名称，例如 screenshot.png */
    private String name;

    /** MIME 类型，例如 image/png */
    private String type;
}
