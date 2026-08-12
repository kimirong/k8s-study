# 第 5 课：部署 Kubernetes Dashboard（Web 界面）

> 目标：装一个网页版管理界面，图形化查看集群
> 版本：kubernetes-dashboard v2.7.0（兼容 k8s 1.31，经典 Token 登录）

## 部署流程（完整记录）

```text
下载清单 → 改写镜像源 → kubectl apply → 配管理员 → 改 NodePort → 登录
```

### ① 下载清单

```bash
# 在能访问 GitHub 的机器上（本机 Mac）下载
curl -fsSL "https://raw.githubusercontent.com/kubernetes/dashboard/v2.7.0/aio/deploy/recommended.yaml" -o /tmp/dashboard.yaml
```

> 坑：v3.x 的下载路径变了（`aio/deploy/recommended.yaml` 只在 v2.x 存在），且 v3 对 k8s 1.31 也没有明显优势，故选 v2.7.0。
> 坑：Master 节点访问不了 raw.githubusercontent.com（被墙），所以**在本机下载后 scp 上去**。

### ② 改写镜像源（国内网络关键一步）

```bash
# docker.io 被墙 → 改成 daocloud 镜像
sed -i 's|image: kubernetesui/|image: docker.m.daocloud.io/kubernetesui/|g' /tmp/dashboard.yaml

# 检查改成了什么（应该看到两个 daocloud 镜像）
grep -oE 'image: [^ ]+' /tmp/dashboard.yaml | sort -u
```

用到的镜像：
- `docker.m.daocloud.io/kubernetesui/dashboard:v2.7.0`
- `docker.m.daocloud.io/kubernetesui/metrics-scraper:v1.0.8`

### ③ 上传并部署

```bash
scp /tmp/dashboard.yaml root@<master>:/tmp/dashboard.yaml
ssh root@<master> 'export KUBECONFIG=/etc/kubernetes/admin.conf; kubectl apply -f /tmp/dashboard.yaml'
```

### ④ 创建管理员账号并取 token

```bash
# 创建服务账号 + 绑定集群最高权限
kubectl create serviceaccount admin-user -n kubernetes-dashboard
kubectl create clusterrolebinding admin-user \
  --clusterrole=cluster-admin \
  --serviceaccount=kubernetes-dashboard:admin-user

# 生成长效 token（1 年有效；不加 --duration 默认只有 1 小时）
kubectl -n kubernetes-dashboard create token admin-user --duration=8760h
```

### ⑤ 暴露为 NodePort（默认是 ClusterIP，外部访问不到）

```bash
kubectl patch svc kubernetes-dashboard -n kubernetes-dashboard \
  -p '{"spec":{"type":"NodePort","ports":[{"port":443,"targetPort":8443,"nodePort":30443}]}}'
```

Dashboard 服务监听 HTTPS 443，映射节点端口 30443。

### ⑥ 验证

```bash
# 集群内
curl -sk https://127.0.0.1:30443/    # 返回 200（-k 因为自签证书）
# 外网
curl -sk https://<公网IP>:30443/     # 返回 200
```

## 登录使用

- 地址：`https://<任一节点公网IP>:30443`
- 浏览器提示证书不安全 → 点"高级 → 继续访问"（自签证书，正常现象）
- 登录选 **Token** 方式，粘贴第④步生成的 token

## Dashboard 里能看什么

| 菜单 | 对应 kubectl 命令 | 用途 |
|------|------------------|------|
| 集群 | `kubectl get nodes` | 节点状态、资源使用 |
| 工作负载 | `kubectl get deploy/pods` | 看我们的 hello、hello-spring |
| 服务与网络 | `kubectl get svc` | Service、Ingress |
| 配置与存储 | `kubectl get cm/secret` | ConfigMap、Secret、PVC |

> Dashboard 上的 YAML 视图，就是你前几课学的字段的图形化——**建议点开 hello-spring 的 Deployment 对照着看**。

## 常用操作

```bash
# 重新取 token（token 过期/丢失时）
kubectl -n kubernetes-dashboard create token admin-user

# 卸载 Dashboard
kubectl delete -f /tmp/dashboard.yaml
kubectl delete serviceaccount admin-user -n kubernetes-dashboard
kubectl delete clusterrolebinding admin-user
```

## 安全提醒

- Dashboard 绑定了 `cluster-admin`（集群最高权限），**暴露在公网有风险**。
- 建议：不学习时关掉实例；或收紧安全组只对自己的电脑 IP 开放 30443。
- 生产环境通常会配合 Ingress + 认证（OIDC/基本认证）再做外网暴露。

## 小结

1. Dashboard 部署的本质：**一个 Deployment + 一个 Service + 一个管理员账号**，和部署普通应用没区别。
2. 国内网络三步走：**GitHub 下载用本机 → 镜像换 daocloud → scp 上去 apply**。
3. Token 是 Dashboard 的钥匙，`kubectl create token` 随时能重新生成。
