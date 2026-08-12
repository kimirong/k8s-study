# K8s 学习集群状态

> 部署时间：2026-08-12 · 拓扑：1 Master + 2 Worker · kubeadm 部署

## 集群信息

| 节点 | 内网 IP | 公网 IP | 角色 |
|------|---------|---------|------|
| k8s-master | 10.0.0.194 | 47.115.224.120 | control-plane |
| k8s-worker1 | 10.0.0.195 | 47.120.43.151 | worker |
| k8s-worker2 | 10.0.0.196 | 47.115.227.24 | worker |

- Kubernetes：v1.31.14 · 运行时 containerd 2.2.1 · 网络插件 Calico 3.29
- SSH 登录：root 密码在 `linux-service.md`（本机已配免密密钥）

## 常用操作（在 Master 上，已配好 kubectl）

```bash
kubectl get nodes            # 看节点
kubectl get pods -A          # 看全部 Pod
kubectl get pods -o wide     # 看 Pod 分布在哪台节点
kubectl logs <pod>           # 看日志
kubectl delete deployment hello   # 删掉示例应用
```

## 示例应用

- Deployment `hello`（nginx，2 副本，跑在 worker1+worker2）+ NodePort Service
- 访问：`http://<任一节点公网IP>:30080`

## 国内网络注意事项（关键经验）

- **docker.io 直连被墙**，k8s 官方源 registry.k8s.io 拉大镜像也会超时
- k8s 核心镜像：kubeadm 用 `--image-repository=registry.aliyuncs.com/google_containers`
- docker.io 系列镜像：改用 `docker.m.daocloud.io/` 前缀（如 `docker.m.daocloud.io/library/nginx:alpine`）
- pause 沙箱镜像已预拉到各节点本地并改名，无需再联网拉取
- containerd 2.x 的 hosts.toml 镜像加速配置在 `ctr` 上实测不生效，因此统一用「直接改镜像地址」方案

## Kubernetes Dashboard（Web 界面）

- 访问：`https://<任一节点公网IP>:30443`（浏览器会提示自签证书不安全，点"高级→继续访问"即可）
- 登录：选择 **Token** 登录方式，粘贴 token（存在 `docs/dashboard-token.txt`，1 年有效）
- 重新取 token：`kubectl -n kubernetes-dashboard create token admin-user`
- 管理员账号 `admin-user` 已绑定 `cluster-admin`（最高权限）
- ⚠️ 安全提醒：Dashboard 是集群最高权限，目前暴露在公网。学习没问题，但**不学习时建议关掉实例或收紧安全组**。

## Ingress（域名/路径入口）

- Ingress Controller：nginx（`ingress-nginx-controller`），HTTP 入口 **NodePort 31552**（443 是 31575）
- 假域名（本机 hosts 文件已可配置）：`hello.test.com` / `spring.test.com` / `app.test.com`
- ⚠️ **访问必须带端口**（NodePort 类型，无云负载均衡器）：`http://hello.test.com:31552`
- 访问示例：
  - `http://<节点公网IP>:31552` + Host 头 `hello.test.com` → nginx
  - `http://<节点公网IP>:31552` + Host 头 `spring.test.com` + `/hello` → Spring Boot
  - `http://<节点公网IP>:31552` + Host 头 `app.test.com` + `/spring/hello` → 路径分流
- 相关清单：`hello-springboot/app-ingress.yaml`（域名分流）、`app-path-ingress.yaml`（路径分流）

## 费用提醒

按量付费实例在运行就会计费，不学习时建议在控制台**停止实例**（停止后 Pod 会停，下次先启动实例再恢复）。
