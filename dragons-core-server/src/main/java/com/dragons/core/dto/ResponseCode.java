package com.dragons.core.dto;

import lombok.Getter;

/**
 * 响应状态码枚举
 * 定义系统中所有可能的响应状态码和对应消息
 * 
 * @author aice
 * @since 2026-01-18
 */
@Getter
public enum ResponseCode {

    /**
     * 成功
     */
    SUCCESS(200, "操作成功"),

    /**
     * 请求参数错误
     */
    BAD_REQUEST(400, "请求参数错误"),

    /**
     * 未授权（未登录或Token过期）
     */
    UNAUTHORIZED(401, "未授权，请先登录"),

    /**
     * 无权限
     */
    FORBIDDEN(403, "无权限访问"),

    /**
     * 资源不存在
     */
    NOT_FOUND(404, "资源不存在"),

    /**
     * 服务器内部错误
     */
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),

    /**
     * 用户名已存在
     */
    USERNAME_EXISTS(4001, "用户名已存在"),

    /**
     * 用户名或密码错误
     */
    LOGIN_FAILED(4002, "用户名或密码错误"),

    /**
     * Token无效或已过期
     */
    TOKEN_INVALID(4003, "Token无效或已过期"),

    /**
     * 文件上传失败
     */
    FILE_UPLOAD_FAILED(4004, "文件上传失败"),

    /**
     * 文件格式不支持
     */
    FILE_FORMAT_NOT_SUPPORTED(4005, "文件格式不支持"),

    /**
     * 文件不存在
     */
    FILE_NOT_FOUND(4006, "文件不存在"),

    /**
     * 用户已被注销
     */
    USER_DELETED(4007, "用户已被注销"),

    /**
     * 用户已被拉黑
     */
    USER_BLACKLISTED(4008, "用户已被拉黑"),

    /**
     * 手机号已被注册
     */
    PHONE_EXISTS(4009, "手机号已被注册"),

    /**
     * 手机号格式不正确
     */
    PHONE_FORMAT_INVALID(4010, "手机号格式不正确"),

    /**
     * 手机号未注册（用于找回/重置密码）
     */
    PHONE_NOT_FOUND(4011, "手机号未注册"),

    /**
     * 树洞：该留言已有回复，禁止重复回复
     */
    TREE_HOLE_REPLY_ALREADY_EXISTS(4012, "禁止重复回复"),

    /**
     * 树洞：被回复的留言已被删除，无法回复
     */
    TREE_HOLE_MESSAGE_DELETED(4013, "该留言已被删除"),

    /**
     * 树洞：树洞已关闭，暂不接收投递
     */
    TREE_HOLE_CLOSED(4014, "树洞已关闭"),

    /**
     * 树洞：您已被该树洞拉黑
     */
    TREE_HOLE_SENDER_BLOCKED(4015, "您已被该树洞拉黑"),

    /**
     * 树洞：您在该树洞尚有未读留言，请先等待主人查看后再投递
     */
    TREE_HOLE_UNREAD_EXISTS(4016, "您在该树洞尚有未读留言，请先等待主人查看后再投递"),

    /**
     * 找回密码：登录名与手机号不匹配（不区分“用户不存在”与“手机号错误”，避免泄露信息）
     */
    LOGIN_NAME_PHONE_MISMATCH(4017, "登录名与手机号不匹配"),

    /**
     * 拉黑：被拉黑用户不存在
     */
    BLOCK_USER_NOT_FOUND(4018, "失败，该用户不存在"),

    /**
     * 树洞分享：投递消息的用户已被该树洞主人拉黑，无法分享
     */
    TREE_HOLE_SHARE_SENDER_BLOCKED(4020, "失败，投递消息的用户已被该树洞主人拉黑，无法分享"),

    /**
     * 树洞分享：部分或全部接收方无法接收
     */
    TREE_HOLE_SHARE_PARTIAL_FAIL(4021, "分享失败，部分用户无法接收");

    /**
     * 状态码
     */
    private final Integer code;

    /**
     * 状态消息
     */
    private final String message;

    /**
     * 构造函数
     * 
     * @param code 状态码
     * @param message 状态消息
     */
    ResponseCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
