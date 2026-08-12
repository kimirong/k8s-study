# 第 9 课：持久化（PV/PVC）——部署有状态应用

> 目标：理解 k8s 的存储抽象，把 MySQL 这种有状态应用跑起来，验证「Pod 会死，数据不死」
> 关联：MySQL 密码用了第 7 课的 Secret，Pod 调度用了 PV 的 nodeAffinity

## 为什么需要持久化

**容器层文件系统随 Pod 消亡**——Pod 一删，容器里写的东西全没了。
- 无状态应用（nginx、我们的 hello-spring）无所谓
- 有状态应用（MySQL、Redis、Elasticsearch）数据没了就完了

## 三层存储抽象

```
StorageClass（"怎么造盘"，管理员配好）   → 生产：云盘/NFS，动态供给
   │ 自动创建
PersistentVolume（PV，真实的一块盘，集群级）
   │ 绑定
PersistentVolumeClaim（PVC，应用的"申请单"）
   │ 挂载
Pod 里 mount 到目录
```

> 一句话：**PVC=我要多大的盘，PV=真实存在的盘，StorageClass=自动造盘的工厂**。应用只认识 PVC。

## 本次实验：hostPath PV（学习用）

hostPath = 把节点上的一个目录直接映射给 Pod。绑定单台节点，生产不推荐，但理解机制最直观。

```yaml
# ① PV：真实盘（在 worker1 的 /data/mysql）
apiVersion: v1
kind: PersistentVolume
metadata:
  name: mysql-pv
spec:
  capacity: { storage: 5Gi }
  accessModes: ["ReadWriteOnce"]
  storageClassName: manual
  nodeAffinity:                      # ★ 这块盘只在 worker1
    required:
      nodeSelectorTerms:
      - matchExpressions:
        - { key: kubernetes.io/hostname, operator: In, values: ["k8s-worker1"] }
  hostPath: { path: /data/mysql }

# ② PVC：申请 2G
apiVersion: v1
kind: PersistentVolumeClaim
metadata: { name: mysql-pvc }
spec:
  accessModes: ["ReadWriteOnce"]
  storageClassName: manual
  resources: { requests: { storage: 2Gi } }

# ③ Deployment：挂到 /var/lib/mysql
spec:
  containers:
  - name: mysql
    image: docker.m.daocloud.io/library/mysql:8.0
    volumeMounts:
    - { name: data, mountPath: /var/lib/mysql }
  volumes:
  - name: data
    persistentVolumeClaim: { claimName: mysql-pvc }
```

**调度联动**：Pod 用了带 nodeAffinity 的 PV，调度器会自动把它排到 worker1——你不需要在 Pod 上写 nodeSelector。

## 验证：Pod 会死，数据不死

> MySQL 密码来自第 7 课的 Secret（`app-secret` 的 `DB_PASSWORD` = `SuperSecret123`），部署时用 `valueFrom` 注入，所以命令行里直接写这个值。

```bash
# ① 写数据（注意 MySQL 容器刚启动要等几秒才能连）
kubectl exec <pod> -- mysql -uroot -pSuperSecret123 \
  -e "CREATE DATABASE IF NOT EXISTS mydb; CREATE TABLE IF NOT EXISTS mydb.users(id INT PRIMARY KEY, name VARCHAR(50)); INSERT INTO mydb.users VALUES (1, 'kimi'), (2, 'spring'); SELECT * FROM mydb.users;"

# ② 删 Pod
kubectl delete pod <pod>

# ③ 新 Pod 起来后，数据还在
kubectl get pods -l app=mysql
kubectl exec <新pod> -- mysql -uroot -pSuperSecret123 -e "SELECT * FROM mydb.users;"

# ④ 真实数据文件在节点上（Pod 之外）
ls /data/mysql/mydb/     # users.ibd 等
```

**结果**：Pod 名字变了（b89tm→srjj4），但 `kimi`、`spring` 两条数据一条不少。

## Redis 实战（同样的模式，验证"任何节点都成立"）

MySQL 用了 worker1 的盘，Redis 用 **worker2** 的盘——证明这套模式跟节点无关。

**Redis 关键点：必须开 AOF 持久化**，否则进程重启内存数据就没了：

```yaml
args: ["redis-server", "--appendonly", "yes"]   # AOF：把写操作追加落盘
```

验证（和 MySQL 完全一样）：
```bash
kubectl exec <pod> -- redis-cli set course k8s
kubectl delete pod <pod>
kubectl exec <新pod> -- redis-cli get course   # 返回 k8s，数据还在
```

**结果**：Pod 从 `t8gx6` 换成 `krzdr`，`course=k8s`、`level=9` 一条不少。

## 生产存储方案

| 方案 | 特点 | 适用 |
|------|------|------|
| hostPath | 简单，绑单节点，Pod 换节点就丢 | 单机/学习 |
| **local PV** | hostPath 的升级版，安全清理 | 单节点生产 |
| **云盘**（阿里云云盘/NFS/EFS） | 动态供给、多副本、容量弹性 | 生产标配 |
| **StorageClass** | 定义"怎么造盘"，PVC 一提就自动建 PV | 生产标配 |

生产正确姿势：配好 **StorageClass（指向云盘/NFS）** → 应用只写 PVC → 盘自动创建。我们手动建 PV 只是为了看清机制。

## 踩坑记录

1. **镜像分发**：mysql:8.0 从 daocloud 拉，worker1 拉得极慢（7KB/s）。解决：master 上把镜像导出成 tar → 内网 scp → import。**以后大镜像都用这套**：
   ```bash
   # master 上导出（镜像必须先在 master 上存在）
   [master] ctr -n k8s.io images export /root/mysql.tar docker.m.daocloud.io/library/mysql:8.0
   # master 走内网分发（配好免密后）
   [master] scp /root/mysql.tar root@10.0.0.195:/root/
   # 目标节点导入
   [worker] ctr -n k8s.io images import /root/mysql.tar
   ```
2. **PVC 暂时 Pending**：会提示 `storageclass "manual" not found`（没定义 StorageClass，动态供给失败）——但静态 PV 会匹配绑定，等一两秒就 Bound。嫌吵可以建个空 StorageClass。
3. **权限**：MySQL 容器以 uid 999 运行，节点目录要 `chown 999:999 /data/mysql`。

## 小结

1. **三层抽象**：PVC（申请单）→ PV（真盘）→ StorageClass（造盘工厂）。
2. **数据必须活在 Pod 之外**，这就是持久化的全部意义。
3. hostPath 只适合学习；**生产用 StorageClass + 云盘/NFS**。
4. MySQL/Redis 等有状态应用，同样的 PVC 模式，只是 MySQL 用 Deployment 单副本、Redis 可能用 StatefulSet（下一课）。

## 动手挑战

1. 给 hello-spring 加一个 MySQL 数据源（Service `mysql:3306` 已经建好了），连上试试。
2. 试试 Redis（镜像 `docker.m.daocloud.io/library/redis:7`），一样的 PVC 模式。
