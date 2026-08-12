# 第 10 课：StatefulSet —— 有状态应用的"正规军"

> 目标：理解 StatefulSet 与 Deployment 的本质区别，亲手验证四大特性
> 实战：把上节课的 Redis 换成 StatefulSet（2 副本），对比立现

## StatefulSet vs Deployment

| | Deployment（上节课的 Redis） | StatefulSet |
|--|-----------------------------|-------------|
| Pod 名字 | `redis-5b58f4c6c4-xxx` 随机后缀 | `redis-0`、`redis-1` 有序编号 |
| 存储 | 所有副本共享一个 PVC | **每副本独立 PVC**（`data-redis-0`）|
| 部署/销毁 | 无序 | **按编号有序**，销毁逆序 |
| 网络身份 | 无固定 DNS | **稳定 DNS**：`redis-0.redis-svc.ns.svc` |

> 一句话：**Deployment 管"一群人"，谁是谁无所谓；StatefulSet 管"每个有名有姓、有自己的盘"的个体。**

## 四大特性逐一验证（实测结果）

### ① 稳定名字
```
kubectl get pods -l app=redis-sts
redis-sts-0   1/1   Running   k8s-worker2
redis-sts-1   1/1   Running   k8s-worker2
```
对比 Deployment：`redis-5b58f4c6c4-krzdr`（随机后缀，每次重建都变）。

### ② 每副本独立存储（volumeClaimTemplates）
```
kubectl get pvc | grep redis-sts
data-redis-sts-0   Bound   redis-sts-pv-0
data-redis-sts-1   Bound   redis-sts-pv-1
```
每个副本的 PVC 自动命名为 `data-<pod名>`，数据跟"身份"绑定。

### ③ 稳定身份
```bash
kubectl exec redis-sts-0 -- redis-cli set identity i-am-zero
kubectl delete pod redis-sts-0          # 删掉 0 号
kubectl get pods                          # 新的还是 redis-sts-0！（不是随机名）
kubectl exec redis-sts-0 -- redis-cli get identity   # i-am-zero，数据还在
```
Pod 重建后**名字、PVC、数据都跟着身份走**——这是有状态应用最需要的。

### ④ 稳定 DNS（headless service）
```bash
kubectl exec redis-sts-0 -- getent hosts redis-sts-1.redis-sts.default.svc.cluster.local
# 192.168.126.21  redis-sts-1.redis-sts.default.svc.cluster.local
```
每个 Pod 有固定域名 `redis-sts-N.redis-sts.default.svc.cluster.local`，主从/集群间靠域名互找，IP 变了没关系。

## 关键 YAML

```yaml
# Headless Service（clusterIP: None）→ 给 Pod 稳定 DNS
kind: Service
spec:
  clusterIP: None
  selector: { app: redis-sts }
  ports: [{ port: 6379, targetPort: 6379 }]

# StatefulSet
kind: StatefulSet
spec:
  serviceName: redis-sts        # 关联 headless service
  replicas: 2
  selector: { matchLabels: { app: redis-sts } }
  template:
    metadata: { labels: { app: redis-sts } }
    spec:
      containers:
      - name: redis
        image: docker.m.daocloud.io/library/redis:7-alpine
        args: ["redis-server", "--appendonly", "yes"]
        volumeMounts: [{ name: data, mountPath: /data }]
  volumeClaimTemplates:         # ★ 每副本自动生成 PVC（data-<pod名>）
  - metadata: { name: data }
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: manual
      resources: { requests: { storage: 1Gi } }
```

## 什么时候用哪个

| 场景 | 用 |
|------|----|
| 无状态、可随便换（nginx、API 服务、我们的 hello-spring） | **Deployment** |
| 单实例有状态（上节课的 MySQL，1 副本） | Deployment 够用 |
| 多副本有状态、需要身份（**MySQL 主从、Redis 集群、ES**） | **StatefulSet** |

## 小结

1. **StatefulSet 给了 Pod"身份"**：稳定名字 + 独立存储 + 稳定 DNS。
2. `volumeClaimTemplates` 让**每副本有自己的盘**，数据跟身份绑定。
3. headless service（`clusterIP: None`）是稳定 DNS 的前提，也是主从互相发现的通道。
4. 选择口诀：**无状态用 Deployment，有身份需求用 StatefulSet**。
