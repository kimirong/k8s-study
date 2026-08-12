# 第 3 课：部署 Spring Boot 应用到集群

> 目标：把一个真实的 Spring Boot 应用跑进 k8s 集群，完整走一遍「代码 → 镜像 → 部署 → 访问」
> 项目代码：`hello-springboot/`（一个 `/hello` 接口，返回 Pod 主机名，用来演示负载均衡）

## 完整流程

```text
写代码 → Maven 打包 jar → Jib 构建镜像 → 导入 containerd → 部署 → 访问
```

## 关键技术点

### 1. Jib：没有 Docker 也能打镜像

Jib 是 Google 的 Maven/Gradle 插件，**纯 Java 直接构建 OCI 镜像**，不依赖 Docker daemon。
- `mvn jib:buildTar` → 生成 `target/jib-image.tar`
- 基础镜像用 daocloud 镜像源（docker.io 被墙）：`docker.m.daocloud.io/eclipse-temurin:17-jre`
- 生产里常用 `mvn jib:build` 直接推镜像仓库

### 2. 镜像必须在运行 Pod 的节点上（或仓库里）—— 踩过的坑

```
图片：Pod 被调度到 worker1/worker2，但镜像只导入了 master
结果：worker 本地没有 → 尝试从 docker.io 拉 → 被墙 → ImagePullBackOff
修复：把镜像 tar 传到两台 worker 并 ctr import
```

> **生产环境的正解**：镜像推到仓库（如阿里云 ACR），所有节点都能拉。
> 学习阶段没有仓库，就把镜像文件分发到每台节点。

### 3. imagePullPolicy 三兄弟

| 值 | 行为 | 什么时候用 |
|----|------|-----------|
| `Always` | 每次都从仓库拉 | 正式环境/调试镜像 |
| `IfNotPresent` | 本地没有才拉 | **本地导入镜像时的选择** |
| `Never` | 只用本地 | 完全离线 |

### 4. readinessProbe 就绪探针

```yaml
readinessProbe:
  httpGet:
    path: /hello
    port: 8080
  initialDelaySeconds: 5   # 启动 5 秒后开始探测
  periodSeconds: 5         # 每 5 秒探一次
```

- k8s 靠它判断"这个 Pod 能不能接流量"，探测通过才把 Pod 标记为 Ready
- **探测失败 → Pod 从 Service 里摘掉，不转发流量**（不会把请求打到坏 Pod 上）
- 滚动更新靠它判断"新的这个可用了没"，可用才杀旧的 → 实现零停机

## 动手实验记录

```bash
# ① 构建（在 master，装有 JDK17 + Maven）
cd /root/hello-springboot
mvn -q -DskipTests package          # 打 jar
mvn -q jib:buildTar                 # 构建镜像 tar

# ② 导入镜像（master）
ctr -n k8s.io images import target/jib-image.tar

# ③ 分发到 worker（走内网，先给 master 配好到 worker 的免密）
scp /root/hello-springboot/target/jib-image.tar root@10.0.0.195:/root/
scp /root/hello-springboot/target/jib-image.tar root@10.0.0.196:/root/
# 在每台 worker 上：
ctr -n k8s.io images import /root/jib-image.tar

# ④ 部署
kubectl apply -f hello-spring.yaml   # Deployment + NodePort Service

# ⑤ 验证
kubectl get pods -l app=hello-spring -o wide
curl http://127.0.0.1:30081/hello      # 多调几次，看 Pod 名交替 = 负载均衡
```

## 验证结果

- `/hello` 返回 Pod 主机名，连点多次在 `...-qwdc9`（worker2）和 `...-znhz2`（worker1）之间交替 → **Service 负载均衡实锤**
- 外网访问：`http://<任一节点公网IP>:30081/hello` → 200
- 排错经验：Pod 出问题先 `kubectl describe pod` 看 Events（ImagePullBackOff → Failed to pull → 一看便知）

## 小结

1. **Jib 让"没有 Docker 也能构建镜像"成为现实**——你以后公司里也可能遇到。
2. **镜像分发是 k8s 的地基问题**：镜像要么在节点本地、要么在都能访问的仓库。
3. **readinessProbe 是生产级必备**：让 k8s 知道"这个 Pod 能干活了"。
4. 你已经具备完整的上线能力：**写代码 → 打镜像 → 部署 → 验证**。

## 下一步可选

- **滚动更新**：改镜像版本重新构建，看 k8s 如何逐个替换、零停机
- **Ingress**：给应用配域名访问（替代裸 NodePort）
- **ConfigMap / Secret**：把配置和密码从镜像里拿出来
- **持久化**：给 MySQL 等有状态应用配存储
