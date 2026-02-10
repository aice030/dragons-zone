package com.dragons.core.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 统一响应结果类
 * 所有接口统一使用此格式返回数据
 * 
 * @author aice
 * @since 2026-01-18
 */
@Getter
@Setter
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 响应状态码
     * 200: 成功
     * 400: 请求参数错误
     * 401: 未授权（未登录或Token过期）
     * 403: 无权限
     * 404: 资源不存在
     * 500: 服务器内部错误
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 时间戳（可选，用于记录响应时间）
     * 如果需要记录响应时间，可以在创建Result时设置
     */
    private Long timestamp;

    /**
     * 私有构造函数，防止直接实例化
     */
    private Result() {
    }

    /**
     * 私有构造函数，用于创建Result实例
     * 
     * @param code 状态码
     * @param message 消息
     * @param data 数据
     */
    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis(); // 自动设置时间戳
    }

    /**
     * 成功响应（无数据）
     * 
     * @return Result实例
     */
    public static <T> Result<T> success() {
        return new Result<>(ResponseCode.SUCCESS.getCode(), ResponseCode.SUCCESS.getMessage(), null);
    }

    /**
     * 成功响应（带数据）
     * 
     * @param data 响应数据
     * @return Result实例
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResponseCode.SUCCESS.getCode(), ResponseCode.SUCCESS.getMessage(), data);
    }

    /**
     * 成功响应（自定义消息）
     * 
     * @param message 自定义消息
     * @return Result实例
     */
    public static <T> Result<T> success(String message) {
        return new Result<>(ResponseCode.SUCCESS.getCode(), message, null);
    }

    /**
     * 成功响应（自定义消息和数据）
     * 
     * @param message 自定义消息
     * @param data 响应数据
     * @return Result实例
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResponseCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 失败响应（使用默认错误消息）
     * 
     * @return Result实例
     */
    public static <T> Result<T> error() {
        return new Result<>(ResponseCode.INTERNAL_SERVER_ERROR.getCode(), 
                ResponseCode.INTERNAL_SERVER_ERROR.getMessage(), null);
    }

    /**
     * 失败响应（自定义消息）
     * 
     * @param message 错误消息
     * @return Result实例
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(ResponseCode.INTERNAL_SERVER_ERROR.getCode(), message, null);
    }

    /**
     * 失败响应（使用ResponseCode）
     * 
     * @param responseCode 响应码枚举
     * @return Result实例
     */
    public static <T> Result<T> error(ResponseCode responseCode) {
        return new Result<>(responseCode.getCode(), responseCode.getMessage(), null);
    }

    /**
     * 失败响应（自定义状态码和消息）
     * 
     * @param code 状态码
     * @param message 错误消息
     * @return Result实例
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 判断是否成功
     * 
     * @return true表示成功，false表示失败
     */
    public boolean isSuccess() {
        return ResponseCode.SUCCESS.getCode().equals(this.code);
    }
}
