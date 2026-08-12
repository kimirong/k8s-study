# 第 4 课：滚动更新、回滚与自愈

> 目标：把前几课的知识串起来，亲手验证 k8s 的三大生产级能力：零停机发布、一键回滚、自动自愈
> 实验：hello-spring 应用 v1.0 → v2.0 → 回滚 v1.0

## 1. 滚动更新（Rolling Update）

### 机制

k8s 升级应用不是"停服换新"，而是**滚动**替换：

```text
1. 用新模板创建一个新的 ReplicaSet，replicas 先 +1
2. 新 Pod 启动 → readinessProbe 探测通过（确认能接流量了）
3. 旧的 ReplicaSet 缩 1 个（杀一个旧 Pod）
4. 重复，直到新 RS 全就绪、旧 RS 缩到 0
```

**关键：先起新、等就绪、再杀旧** —— 这就是零停机的秘密，也全靠你之前写的 readinessProbe。

### 实操

```bash
# 方式一（声明式，推荐）：改 YAML 的 image 版本 → apply
kubectl apply -f hello-spring.yaml

# 方式二（命令式）：直接改镜像
kubectl set image deployment/hello-spring hello-spring=hello-spring:2.0

# 观察
kubectl rollout status deployment/hello-spring   # 看滚动进度
kubectl get rs -l app=hello-spring               # 新旧两个 RS 并存，旧缩到 0
kubectl rollout history deployment/hello-spring  # 版本历史
```

### 我们的验证结果

- 更新期间持续 curl，**0 次失败**，接口从 v1.0 无缝切到 v2.0
- 新 Pod 名带新 hash（`7d8d856c67`），旧 Pod 名带旧 hash（`5b494f5c54`）→ 一眼看出属于哪个 RS

## 2. 回滚（Rollback）

```bash
kubectl rollout undo deployment/hello-spring   # 一键回滚到上一个版本
kubectl rollout history deployment/hello-spring  # 看所有版本
kubectl rollout undo deployment/hello-spring --to-revision=1   # 回滚到指定版本
```

- 回滚不是"覆盖历史"，而是**生成一个新 REVISION 指向旧模板**，历史全保留
- 这是线上出问题时的黄金逃生通道

## 3. 自愈（Self-Healing）—— 两个层级

| 层级 | 触发条件 | 结果 | 证据 |
|------|---------|------|------|
| ① 容器崩溃 | 容器内进程死掉 | kubelet 重启容器，**Pod 不变** | 同一 Pod，RESTARTS 变 1 |
| ② Pod 被删/节点挂 | Pod 消失 | ReplicaSet 重建**全新 Pod** | 新名字、新 IP |

```bash
# ① 容器重启（在 Worker 上杀容器 runtime task）
ctr -n k8s.io c list | grep <pod名>          # 找到容器 ID
ctr -n k8s.io task kill <容器ID> -s SIGKILL
kubectl get pods                             # RESTARTS 变成 1

# ② Pod 重建
kubectl delete pod <pod名>
kubectl get pods -o wide                     # 新 Pod 出现
```

### 踩坑记录：容器里 `kill -9 1` 杀不死 Java

尝试在容器内 `kubectl exec <pod> -- kill -9 1` 或 `pkill -9 java`，Java 进程**都存活**（容器内 PID 1 有内核信号保护，且 Jib 最小化镜像工具不全）。最后用节点上的 `ctr task kill` 从 runtime 层面杀才生效。
> 生产排障经验：**容器内信号不好使时，直接用容器运行时工具操作**（`crictl`/`ctr`），或配置 livenessProbe 让 k8s 自己发现不健康。

## 小结

1. **滚动更新 = 零停机发布**：先起新、等就绪、再杀旧，readinessProbe 是前提。
2. **rollout undo = 一键回滚**：版本历史全保留，出问题 3 秒逃生。
3. **自愈分两层**：容器崩了重启容器，Pod 没了重建 Pod，用户无感知。
4. 这三项能力叠加，就是 k8s 被称为"生产级"的底气——升级、故障都不需要人工介入。

## 动手挑战

1. 把 v2.0 的接口加一个 `/health` 故意报错，配合 livenessProbe 看 k8s 怎么自动"杀+重启"不健康的容器。
2. 试试 `kubectl rollout pause/resume` 暂停滚动（灰度发布）。
