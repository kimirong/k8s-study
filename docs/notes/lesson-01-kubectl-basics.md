# 第 1 课：kubectl 三板斧

> 实验环境：1 Master + 2 Worker，k8s v1.31.14，Calico 网络

## 心智模型

```text
kubectl 动作 资源
   │      │
   │      └─ nodes / pods / deploy / svc / rs ...
   └─ get / describe / logs / apply / delete / scale ...
```

**95% 的时间只需要 4 个动作**：get（看）、describe（查详情）、logs（看日志）、apply/delete（改）。

## 三个核心命令

| 命令                             | 用途                     | 什么时候用     |
| -------------------------------- | ------------------------ | -------------- |
| `kubectl get <资源>`             | 快速看清单               | 每天 100 次    |
| `kubectl get <资源> -o wide`     | 多显示 Pod IP 和所在节点 | 想看调度分布   |
| `kubectl get <资源> -o yaml`     | 看系统里存的完整对象     | 排查/学习      |
| `kubectl describe <资源> <名字>` | 详情 + **Events 时间线** | 出问题第一反应 |
| `kubectl logs <Pod>`             | 看容器 stdout 日志       | 应用报错       |

### 常用资源名

`nodes`、`pods`（简称 `po`）、`deploy`、`rs`（ReplicaSet）、`svc`（Service）、`ns`（namespace）、`cm`（ConfigMap）、`secret`。

## 资源层级（最重要的一张图）

```text
Deployment  hello                    ← 你声明的："我要 2 个实例"（期望状态）
   └─> ReplicaSet  hello-86b9d9cf88  ← 中间人：盯住永远有 2 个 Pod 存活（调谐）
         └─> Pod  hello-86b9d9cf88-xxx  ← 真正跑起来的实例（可随时被重建）

Service  hello                       ← 稳定入口：不管 Pod 怎么换，访问它都通
```

> Java 类比：Deployment ≈ 你声明"线程池大小=2"；ReplicaSet ≈ 线程池实现；Pod ≈ 线程。**你只需要对着 Deployment 说话。**

**血缘识别**：名字里的 hash 段（`86b9d9cf88`）相同 → 属于同一个 ReplicaSet 模板。

## describe 的 Events 段 = Pod 的一生

Events 把 Pod 从创建到现在的每一步都记录下来，按时间顺序读，就是完整因果链：

```
Scheduled  → scheduler 决定"放哪台节点"
Pulling    → kubelet 开始拉镜像
Pulled     → 拉取完成
Created    → containerd 创建容器
Started    → 容器启动
```

> **排错铁律：任何 Pod 有问题，先 `kubectl describe pod xxx` 看 Events 段。** 它不撒谎。

## 动手实验记录

> 下面命令基于 nginx 的 `hello` 应用（第 2 课会部署它，文件在仓库 `hello-springboot/hello.yaml`）。
> 如果还没部署，先按第 2 课"部署示例应用"创建；或把命令里的 `hello` 换成集群里已有的资源。

```bash
kubectl get pods                       # 看 Pod
kubectl get pods -o wide               # 看 IP 和节点
kubectl get deploy,rs,svc              # 一次看三种资源
kubectl describe pod hello-xxxx        # 看详情 + Events
kubectl logs hello-xxxx                # 看 nginx 启动日志
kubectl delete pod hello-xxxx          # 删一个，看 ReplicaSet 秒级重建
```

## 小结

1. `get` 看状态、`describe` 查原因、`logs` 看日志——三板斧走天下。
2. Pod 的 IP 是内网的（192.168.x.x，Calico 分配），只有集群内可见，外面必须走 Service。
3. 一切以"期望状态"为锚：删了 Pod 它会自己补，这是自愈的开端（下节课详解）。
