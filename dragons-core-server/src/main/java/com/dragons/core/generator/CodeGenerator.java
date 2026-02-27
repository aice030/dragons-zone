package com.dragons.core.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

/**
 * @author aice
 * 2026.01.16
 * 代码生成器
 */
public class CodeGenerator {
    public static void main(String[] args) {
        FastAutoGenerator.create("jdbc:mysql://localhost:3306/dragons?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8",
                        "root", "123456")
                .globalConfig(builder -> {
                    builder.author("aice") // 设置作者
                            .outputDir("dragons-core-server/src/main/java"); // 指定输出目录
                })
                .packageConfig(builder -> {
                    builder.parent("com.dragons.core")
                            .entity("entity")
                            .mapper("dao")
                            .service("service")
                            .serviceImpl("serviceImpl")
                            .xml("mapper")
                            .controller("controller");
                })
                .strategyConfig(builder -> {
                    builder.addInclude("user", "tree_hole", "tree_hole_message", "tree_hole_message_visible",
                                    "media", "media_visible", "tree_hole_blacklist",
                                    "user_like_record", "user_promise") // 设置需要生成的表名
                            .entityBuilder()
                            .enableLombok() // 启用 Lombok
                            .enableTableFieldAnnotation() // 启用字段注解
                            .controllerBuilder()
                            .enableRestStyle(); // 启用 REST 风格
                })
                // 使用 Freemarker 模板引擎
                .templateEngine(new FreemarkerTemplateEngine())
                .execute(); // 执行生成
    }
}
