# 第 12 课：监控 —— Prometheus + Grafana

> 目标：搭起集群和应用的可观测体系：Prometheus 存指标，Grafana 画图
> 组件：Metrics Server + kube-prometheus-stack（Prometheus/Grafana/Alertmanager/各种 exporter）

## 监控体系分工

```
Prometheus（采集+存储）──定时抓取──► 各种 exporter
   │                                 ├─ node-exporter：节点 CPU/内存/磁盘
   │                                 ├─ kube-state-metrics：Pod/Deployment 状态
   │                                 ├─ kubelet：容器指标
   │                                 └─ 应用 /actuator/prometheus：Spring Boot
   ▼
Grafana（可视化）──查询 Prometheus──► 仪表盘

Metrics Server：轻量，只给 kubectl top 提供用量
```

**核心认知**：Prometheus 是"**拉**"的模式——它主动去抓每个 exporter 暴露的 `/metrics` 端点，而不是等数据送上门。

## 安装过程（含国内网络实战）

### ① Metrics Server（5 分钟）
```bash
# 清单在 GitHub，本机下载后换镜像上传
curl -fsSL .../metrics-server/releases/latest/download/components.yaml
sed -i 's|registry.k8s.io/|k8s.m.daocloud.io/|g' components.yaml
kubectl apply -f components.yaml

# 坑：kubeadm 的 kubelet 证书是自签的，要加 --kubelet-insecure-tls
kubectl patch deployment metrics-server -n kube-system -p '{"spec":{"template":{"spec":{"containers":[{"name":"metrics-server","args":["--cert-dir=/tmp","--secure-port=10250","--kubelet-insecure-tls","--kubelet-preferred-address-types=InternalIP","--kubelet-use-node-status-port","--metric-resolution=15s"]}]}}}}'

# 验证
kubectl top nodes
```

### ② kube-prometheus-stack（Prometheus 全家桶）
```bash
# Helm 仓库被墙 → 本机下载 chart 包上传
curl -fsSL .../helm-charts/releases/download/kube-prometheus-stack-66.0.0/...tgz -o kps.tgz
# 渲染 + 全局替换镜像前缀（quay.io 也被墙！）
helm template monitoring ./kube-prometheus-stack --set prometheus-adapter.enabled=false > monitoring.yaml
sed -i 's|quay.io/|quay.m.daocloud.io/|g; s|registry.k8s.io/|k8s.m.daocloud.io/|g; s|docker.io/|docker.m.daocloud.io/|g; s|ghcr.io/|ghcr.m.daocloud.io/|g' monitoring.yaml
# CRDs 在 charts/crds/crds/ 子目录
kubectl apply --server-side -f .../charts/crds/crds/
kubectl apply -f monitoring.yaml
```

## Grafana 使用

- 地址：`http://<公网IP>:30300`（NodePort 30300），账号 `admin`，密码从 Secret 取：
  ```bash
  kubectl get secret monitoring-grafana -o jsonpath="{.data.admin-password}" | base64 -d
  ```
- **集群仪表盘**（预置）：`Dashboards → Kubernetes / Compute Resources / Cluster`、`Node (Pods)`、`API server`、`etcd`...
- **看应用指标**：`Explore` → 选 Prometheus 数据源 → 查 `jvm_memory_used_bytes`、`http_server_requests_seconds_count`

## 给 Spring Boot 加应用指标（三件事）

### ① 代码加依赖 + 暴露端点
```xml
<dependency>spring-boot-starter-actuator</dependency>
<dependency>io.micrometer:micrometer-registry-prometheus</dependency>
```
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
```
验证：`curl <pod>:8080/actuator/prometheus` 能吐出 JVM 指标。

### ② Service 打标签 + 端口命名
```yaml
# Service 必须有 app 标签 + 端口命名，ServiceMonitor 才认
metadata:
  labels:
    app: hello-app
spec:
  ports:
  - name: http
    port: 8080
```

### ③ ServiceMonitor：告诉 Prometheus 去抓
```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: hello-app-monitor
  labels:
    release: monitoring        # ★ 必须匹配 Prometheus 的 selector
spec:
  selector:
    matchLabels:
      app: hello-app
  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 15s
```

## 踩坑记录（都是国内/新手高频坑）

1. **quay.io 也被墙**——用 `quay.m.daocloud.io`（daocloud 镜像站全家桶：docker/gcr/k8s/quay 都有镜像站）。
2. **Metrics Server 探针 500**——kubeadm 自签证书问题，加 `--kubelet-insecure-tls`。
3. **ServiceMonitor 目标被 dropped**——Service 缺 `app` 标签或端口没命名。诊断用：`/api/v1/targets` 看 active/dropped。
4. **scp -r 到已存在目录会嵌套**——先 `rm -rf` 目标再传。

## 小结

1. **监控 = Prometheus（拉数据）+ exporter（吐数据）+ Grafana（画图）**。
2. 集群指标开箱即用（kube-prometheus-stack），应用指标要自己做三件事：加依赖、Service 打标签、ServiceMonitor。
3. `kubectl top` 是日常最常用的快速查看命令（Metrics Server 提供）。
4. 国内装任何东西三步走：**GitHub 下载用本机 → 换 daocloud 镜像 → 上传部署**。
