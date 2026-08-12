# hello-springboot 示例项目

一个极简 Spring Boot 3.3（Java 17）应用，作为 k8s 学习全过程的"试验品"。`/hello` 接口返回 Pod 主机名（用来演示负载均衡）。

## 代码

```
src/main/java/com/example/hello/
├── HelloApplication.java    # 启动类
└── HelloController.java     # GET /hello，返回 Pod 主机名
src/main/resources/application.yml   # 端口 8080 + Actuator 指标
```

**构建**（本机没有 Docker，用 Jib 打包成镜像 tar，在 k8s 节点上导入）：
```bash
mvn -q -DskipTests package
mvn jib:buildTar -Djib.to.image=hello-spring:1.0
```

## k8s 清单一览（对应各课）

| 文件 | 内容 | 对应课程 |
|------|------|----------|
| `hello-spring.yaml` | 基础 Deployment + NodePort Service | 第 2/3 课 |
| `app-ingress.yaml` | 域名分流 Ingress + HTTPS + 强制跳转 | 第 6/8 课 |
| `app-path-ingress.yaml` | 同一域名按路径分流（rewrite） | 第 6 课 |
| `config-demo.yaml` | ConfigMap + Secret + envFrom 注入 | 第 7 课 |
| `mysql-persistent.yaml` | PV/PVC + MySQL 持久化 | 第 9 课 |
| `redis-persistent.yaml` | PV/PVC + Redis（Deployment） | 第 9 课 |
| `redis-sts.yaml` | Redis 用 StatefulSet 部署 | 第 10 课 |
| `hello-servicemonitor.yaml` | ServiceMonitor，让 Prometheus 抓应用指标 | 第 12 课 |

## 镜像说明

- 本地镜像名：`hello-spring:1.0` / `hello-spring:2.0`（v2.0 加了 Actuator 监控指标）
- 镜像通过 Jib 构建后分发到 3 台节点（不推公共仓库）
- 部署时用 `imagePullPolicy: IfNotPresent`（本地已有镜像，不联网拉取）
