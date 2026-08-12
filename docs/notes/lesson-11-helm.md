# 第 11 课：Helm —— k8s 的包管理器

> 目标：理解 Chart/Values/Release，把 hello-spring 打包成 Helm chart，学会一条命令部署+升级+回滚

## Helm 是什么

把一堆 k8s YAML（Deployment/Service/ConfigMap/Secret/Ingress...）**打包成模板 + 参数**，解决：
- 10 个 YAML 文件管理混乱
- dev/prod 环境差异要改来改去
- 部署/升级/回滚没有版本管理

## 三剑客

| 概念 | 是什么 | Java 类比 |
|------|--------|----------|
| **Chart** | 打包的一整套资源模板 | Maven 模块 |
| **Values** | 可配置参数（环境差异） | `application-{profile}.yml` |
| **Release** | 一次部署的实例 | 一次发布记录 |

## Chart 目录结构

```
hello-chart/
├── Chart.yaml          # 元数据（名字、版本）
├── values.yaml         # ★ 参数默认值（环境差异都在这）
└── templates/          # ★ Go 模板（{{ .Values.xxx }} 引用参数）
    ├── deployment.yaml
    └── service.yaml
```

## 模板语法（核心就这几个）

```yaml
# values.yaml
replicaCount: 2
image:
  repository: hello-spring
  tag: "1.0"

# templates/deployment.yaml
replicas: {{ .Values.replicaCount }}
image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
name: {{ .Release.Name }}      # Release 名
name: {{ .Chart.Name }}        # Chart 名
```

## 常用命令

```bash
helm install hello-app ./hello-chart          # 部署（创建 Release）
helm list                                     # 看所有 Release
helm upgrade hello-app ./hello-chart --set replicaCount=3   # 升级（--set 覆盖参数）
helm history hello-app                        # 发布历史（像 git log）
helm rollback hello-app 1                     # 回滚到某个 REVISION
helm uninstall hello-app                      # 卸载（自动清掉所有资源）
helm template hello-app ./hello-chart         # 只看渲染结果，不部署（调试用）
```

## 实战记录

```bash
# 部署：一条命令创建 Deployment + Service（NodePort 30082）
helm install hello-app ./hello-chart

# 升级：不改任何 YAML，只改参数 → REVISION 2，副本 2→3
helm upgrade hello-app ./hello-chart --set replicaCount=3

# 历史 + 回滚
helm history hello-app     # REVISION 1(superseded), 2(deployed)
helm rollback hello-app 1  # 一键回到 REVISION 1 → REVISION 3，副本 3→2
```

## 常用 Chart 仓库

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami   # 官方流行仓库
helm search repo bitnami/redis                              # 搜现成 chart
helm install my-redis bitnami/redis                          # 一条命令装 Redis
```

> 现成 chart 生态（bitnami、artifacthub.io）就像 npm/Maven 中央仓库，数据库、中间件都能一行装。

## 小结

1. **Helm = YAML 的包管理器**，模板化 + 参数化解决环境差异。
2. **三剑客**：Chart（模板包）、Values（参数）、Release（部署实例）。
3. **升级/回滚像 Git 一样有版本**：`helm upgrade --set`、`helm rollback`。
4. 生产里几乎每个服务都用 Helm 发布——这是你以后看公司部署脚本必见的东西。
