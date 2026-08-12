# 第 2 课：YAML 与声明式

> 目标：读懂并编写 Deployment / Service 的 YAML，理解 k8s 核心思想——声明式 + 调谐

## YAML 通用骨架

每个 k8s 资源都是这个结构：

```yaml
apiVersion: <API 组/版本>   # Deployment 是 apps/v1；Service 是 v1（core 组）
kind: <资源类型>            # Deployment / Service / Pod / ConfigMap ...
metadata:
  name: <名字>              # 资源唯一标识
spec:                       # ★ 从这里开始，全是"期望状态"的描述
  ...
```

## Deployment 逐段解读

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: hello
spec:
  replicas: 2                # 期望副本数 ← 调谐循环盯的就是这个数
  selector:                  # Deployment 找"自己管的 Pod"的方式
    matchLabels:
      app: hello
  template:                  # ★ Pod 的"模具"（Supplier<Pod>）
    metadata:
      labels:
        app: hello           # 给新 Pod 打的标签（和 selector 配对）
    spec:
      containers:
      - name: nginx
        image: docker.m.daocloud.io/library/nginx:alpine
        ports:
        - containerPort: 80
```

## Service 逐段解读

```yaml
apiVersion: v1
kind: Service
metadata:
  name: hello
spec:
  type: NodePort             # 暴露方式：NodePort / ClusterIP / LoadBalancer
  selector:
    app: hello               # ★ Service 靠标签找后端 Pod
  ports:
  - port: 80                 # Service 自己的端口（集群内访问 hello:80）
    targetPort: 80           # 转发到 Pod 的端口
    nodePort: 30080          # 每台节点上开的对外端口
```

## ★ 核心概念：labels / selector（k8s 的"外键"）

```text
Deployment ──selector: app=hello──► 一批 Pod（带 app=hello 标签）
Service    ──selector: app=hello──► 同一批 Pod
```

- **一切关联都靠标签，不靠名字/IP** → 所以 Pod 死了换新 IP，Service 不受影响
- 标签是松耦合的关键，也是 k8s 能自我修复的根基

## 调谐循环（Reconciliation）—— k8s 的心脏

```text
盯着 etcd：期望状态 = "2 个 Pod"（来自 YAML）
盯着集群：实际状态 = 当前的 Pod 数
两者不等 → 调谐（补建或回收）→ 回到起点继续盯
```

- 执行者：ReplicaSet 控制器（跑在 controller-manager 里）
- **双向**：少了补、多了删，永远向期望收敛
- 这就是"自愈"，也是滚动更新、扩缩容的地基

## 声明式 vs 命令式

| 方式 | 例子 | 本质 |
|------|------|------|
| 命令式 | `kubectl scale deploy hello --replicas=3`、`kubectl create` | 你指挥"做这一步" |
| **声明式** | 改 YAML 的 replicas → `kubectl apply -f xxx.yaml` | 你只描述"最终长这样"，系统 diff 后改差异 |

> **k8s 的正道：YAML 文件是唯一"期望状态"来源，一切变更都改文件再 apply。**

## 动手实验记录

```bash
# ① 自愈验证：删 Pod，秒级重建（新名字、新 IP）
kubectl get pods -l app=hello -o wide
kubectl delete pod hello-xxxx
kubectl get pods -l app=hello -o wide   # 新 Pod 出现

# ② 扩容：期望 3 → 补 1 个
kubectl patch deployment hello -p '{"spec":{"replicas":3}}'   # 或改 YAML 后 apply
kubectl get pods -l app=hello -o wide

# ③ 缩容：期望 2 → 回收 1 个
kubectl scale deployment hello --replicas=2
kubectl get pods -l app=hello -o wide
```

> 建议：练习时用 `kubectl apply` 而不是 patch/scale，养成声明式习惯。

## 小结

1. **YAML = 期望状态的声明**，`kubectl apply` 是唯一的发布按钮。
2. **labels/selector** 让 Deployment 和 Service 都能"找到"Pod，这是松耦合的关键。
3. **replicas + 调谐循环** 实现了自愈和扩缩容——少了补、多了删。
4. Pod 会死会换 IP，但 Service 稳定不变，所以访问永远走 Service。
