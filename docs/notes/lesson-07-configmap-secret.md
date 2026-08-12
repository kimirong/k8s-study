# 第 7 课：ConfigMap 与 Secret

> 目标：把配置和凭据从镜像里拿出来，实现「镜像不可变，配置可变」
> 关联：结合 Spring Boot 外部化配置理解；和 Nacos 是互补关系（见文末）

## 概念

| | ConfigMap | Secret |
|--|-----------|--------|
| 存什么 | **非敏感**配置（开关、级别、URL） | **敏感**信息（密码、密钥、证书） |
| 怎么存 | 明文 | **Base64 编码** |
| 典型用途 | 环境差异配置、功能开关 | 数据库/Redis 密码、API Key |

**⚠️ Secret 是 base64，不是加密**：
```bash
# 看到的是一串 base64
kubectl get secret app-secret -o jsonpath="{.data.DB_PASSWORD}"
# 一条命令还原成明文
kubectl get secret app-secret -o jsonpath="{.data.DB_PASSWORD}" | base64 -d
```
base64 只防"路过的人瞄一眼"，真正的安全靠 **RBAC 权限**。别把 Secret 当保险箱。

## 三种注入方式

```yaml
# ① 整包注入成环境变量（最常用）
containers:
- name: hello-spring
  envFrom:
  - configMapRef: { name: app-config }
  - secretRef:    { name: app-secret }

# ② 挂载成文件（Spring Boot 读 application.yml 的方式）
  volumeMounts:
  - { name: config, mountPath: /config }
volumes:
- name: config
  configMap: { name: app-yml }

# ③ 单个变量精确注入
  env:
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: app-secret
        key: DB_PASSWORD
```

## 核心心智：镜像不可变，配置可变

```
同一个镜像 hello-spring:1.0
  ├─ 挂 dev 配置  → 开发环境
  ├─ 挂 prod 配置 → 生产环境
  └─ 改配置 → kubectl rollout restart → 生效（不用重新打镜像！）
```

**验证过的实操**：
```bash
# 创建
kubectl create configmap app-config --from-literal=APP_MODE=dev --from-literal=LOG_LEVEL=INFO
kubectl create secret generic app-secret --from-literal=DB_PASSWORD=SuperSecret123

# 改配置（dev→prod），重启生效
kubectl create configmap app-config --from-literal=APP_MODE=prod --from-literal=LOG_LEVEL=DEBUG --dry-run=client -o yaml | kubectl apply -f -
kubectl rollout restart deployment/hello-spring

# 验证注入
kubectl exec <pod> -- env | grep -E "APP_MODE|DB_PASSWORD"
```

> ⚠️ 注意：envFrom 注入的配置**修改后要重启 Pod 才生效**（env 是启动时读取的）。挂载成文件的配置有延迟但会自动更新。

## 和 Nacos 的关系（重要判断）

```
应用层配置（业务开关、动态刷新）  → Nacos / Spring Cloud Config
平台层配置（镜像密码、TLS 证书、连 Nacos 的凭据） → k8s ConfigMap/Secret
```

- **Nacos 能替代 ConfigMap 里"应用配置"的部分**，但**替代不了 Secret**：
  - 镜像仓库密码（imagePullSecrets）→ 必须 k8s Secret
  - Ingress TLS 证书 → 必须 k8s Secret
  - 应用连 Nacos 自己的账号 → 往往也要 k8s Secret（鸡生蛋问题）
- 两者**互补**，不冲突。国产团队常见组合：Nacos 管业务配置，k8s Secret 管平台凭据。

## 常用命令

```bash
kubectl get configmaps / secrets            # 看列表
kubectl get secret app-secret -o yaml      # 看 base64 内容
kubectl describe configmap app-config      # 看键值
kubectl delete configmap app-config        # 删除
```

## 小结

1. **ConfigMap=非敏感配置，Secret=敏感信息**，Secret 是 base64 不是加密。
2. **三种注入**：envFrom（整包）、volumeMount（文件）、valueFrom（单个）。
3. **镜像不可变、配置可变**是 k8s 环境隔离的基石，改配置永远不重建镜像。
4. 和 Nacos 互补：Nacos 管应用配置，ConfigMap/Secret 管平台层，谁也替代不了谁。
