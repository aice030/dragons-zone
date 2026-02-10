# 后浪粉丝互动平台 - Dragons Zone

## 项目介绍

**Dragons Zone** 是一款专为粉丝打造的对象存储和互动平台。为粉丝提供安全、便捷的图片/视频存储服务，以及私密的留言互动功能。

## 核心功能

### 🔐 安全认证
- JWT无状态登录认证
- BCrypt密码加密存储
- 多角色权限管理（作者、管理员、普通用户、游客）

### 📸 媒体文件管理
- **图片/视频上传**: 支持常见格式的图片和视频文件上传
- **云端存储**: 使用阿里云OSS对象存储，安全可靠
- **权限控制**: 支持公开、部分可见、仅自己可见等多种权限设置
- **评论互动**: 支持对媒体文件进行评论和互动

### 💬 粉丝留言树洞
- **私密树洞**: 创建专属的留言树洞
- **匿名留言**: 粉丝可以匿名或实名向树洞投递留言
- **权限管理**: 灵活控制树洞的可见性和留言权限

## 技术架构

### 后端技术栈
- **框架**: Spring Boot 3.3.1
- **语言**: Java 17
- **数据库**: MySQL 8.0
- **ORM**: MyBatis-Plus
- **安全**: Spring Security + JWT
- **存储**: 阿里云OSS对象存储

### 前端技术栈
- **框架**: Vue
- 开发中...

## 快速开始

### 环境要求
- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- 阿里云OSS账号

### 安装步骤

1. **克隆项目**
```bash
git clone [项目地址]
cd dragons-zone
```

2. **配置数据库**
   - 创建MySQL数据库
   - 修改 `dragons-core-server/src/main/resources/application.yml` 中的数据库连接信息

3. **配置阿里云OSS**
   - 在 `application.yml` 中配置OSS相关信息（Endpoint、AccessKey、Bucket等）

4. **运行项目**
```bash
cd dragons-core-server
mvn spring-boot:run
```

## 项目结构

```
dragons-zone/
├── dragons-core-server/    # 后端服务（Spring Boot）
├── README.md              # 项目介绍文档
└── development.md         # 开发文档（详细）
```

## 功能规划

### MVP版本（当前开发中）
- ✅ 基础框架搭建
- ✅ 数据库设计
- 🔄 JWT认证系统
- 🔄 媒体文件管理
- 🔄 粉丝留言树洞

### 后续优化
- 缓存系统（Redis）
- 大文件分片上传（Go语言实现）
- 断点续传功能
- 更多用户体验优化

## 开发文档

详细的开发文档、API接口设计、开发路线等请查看 [development.md](./development.md)

## 许可证

[待定]

## 联系方式

如有问题或建议，欢迎反馈。
