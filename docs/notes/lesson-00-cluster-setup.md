# 第 0 课：环境准备与集群搭建

> 目标：从零用 kubeadm 搭出 3 节点集群（这是后面所有课的地基）
> 环境：阿里云按量付费 · Ubuntu 22.04 × 3 · k8s v1.31.14

## 一、服务器规划

| 节点 | 角色 | 规格 | 内网 IP |
|------|------|------|---------|
| k8s-master | control-plane | 4核/8G/40G | 10.0.0.194 |
| k8s-worker1 | worker | 4核/8G/40G | 10.0.0.195 |
| k8s-worker2 | worker | 4核/8G/40G | 10.0.0.196 |

**kubeadm 最低要求**：2 核/2G（Master），学习建议 2核/4G 起步，跑 Java 应用建议 4G+。

## 二、服务器申请流程（以阿里云为例）

> 完整申请 3 台实例，以下每一项配置 + 理由，照着买即可。**三台配置前 7 项完全一致，只改第 8 项实例名称和登录密码。**

### 购买入口
阿里云控制台 → 云服务器 ECS → **创建实例**

### 逐项配置清单

| # | 配置项 | 选择 | 理由 |
|---|--------|------|------|
| 1 | **地域 Region** | 华南2（河源） | 三台必须同一地域，否则无法内网互通 |
| 2 | **可用区 AZ** | 河源可用区A | 三台同一可用区：延迟最低、**免跨可用区流量费** |
| 3 | **付费方式** | 按量付费 | 学习用，随开随停，省钱 |
| 4 | **实例规格** | 经济型e / 4核8G（`ecs.e-c1m2.xlarge`） | 超最低要求，跑 Spring Boot + MySQL 都够 |
| 5 | **镜像** | Ubuntu 22.04 64位（**非安全加固**） | 选非加固，避免 sudo/密码策略干扰 |
| 6 | **系统盘** | ESSD Entry **60G**（40G 偏紧） | 装完 k8s + 几个镜像后 40G 余量不多 |
| 7 | **公网带宽** | 按使用流量 3Mbps | 能拉镜像，但偏慢（大镜像 3Mbps 很痛苦） |
| 8 | **实例名称** | `k8s-master` / `k8s-worker1` / `k8s-worker2` | 一眼分清角色 |
| 9 | **网络** | 三台放**同一个 VPC + 同一个交换机** | 内网互通的前提 |
| 10 | **安全组** | 默认安全组 → **需加 k8s 端口规则** | 见下方"安全组配置" |
| 11 | **登录凭证** | 自定义密码 | 简单直接（生产建议密钥） |

### 安全组配置（最容易卡住集群的一步）

阿里云默认安全组基本只放行 22 端口，**必须加规则**：

| 规则 | 端口 | 来源 |
|------|------|------|
| 集群内部互访 | **全部端口 1-65535** | 选择「安全组」→ 本安全组 |
| SSH | 22 | 自己的电脑 IP |
| 可选：外部访问 | 6443、30000-32767 | 自己的电脑 IP |

> ⚠️ 阿里云控制台"访问来源"默认值是 **IPv6（::/0）**！选 IPv4 或安全组，否则规则不生效（节点之间走 IPv4 内网）。
> 省心一招：加一条"**本安全组内全部端口互通**"，一次覆盖 k8s 所有内部端口，以后加节点不用再补。

### 购买时的注意事项（血泪总结）

1. **三台必须同一地域 + 同一 VPC + 同一可用区**——分开买容易买错，建议一台一台确认。
2. **每台都要公网 IP**——拉镜像、SSH 全靠它，没有公网出网会卡死在安装。
3. **系统盘别买 40G**——k8s 系统 + 几个镜像就没多少了，60G 起步。
4. **按量付费 = 跑着就计费**——不学习时去控制台"停止实例"（不是释放），数据还在。
5. **安全组 IPv6 坑**——默认来源 ::/0 对 IPv4 流量无效。
6. **实例释放保护**：默认"否"，别误删重要节点。

## 三、三台统一初始化

```bash
# ① 主机名 + hosts
hostnamectl set-hostname k8s-master    # worker 改为 k8s-worker1/2
cat >> /etc/hosts <<EOF
10.0.0.194 k8s-master
10.0.0.195 k8s-worker1
10.0.0.196 k8s-worker2
EOF

# ② 内核模块 + sysctl（容器网络必需）
modprobe overlay br_netfilter
cat > /etc/modules-load.d/k8s.conf <<EOF
overlay
br_netfilter
EOF
cat > /etc/sysctl.d/k8s.conf <<EOF
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF
sysctl --system

# ③ 安装 containerd + 配置（SystemdCgroup、pause 镜像）
apt-get install -y containerd
containerd config default > /etc/containerd/config.toml
# 改 SystemdCgroup = true，sandbox_image 指向可用镜像源
systemctl restart containerd

# ④ 安装 kubeadm/kubelet/kubectl（国内源 pkgs.k8s.io 可达）
curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.31/deb/Release.key | gpg --dearmor -o /etc/apt/keyrings/k8s-keyring.gpg
echo "deb [signed-by=/etc/apt/keyrings/k8s-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.31/deb/ /" > /etc/apt/sources.list.d/kubernetes.list
apt-get update && apt-get install -y kubelet kubeadm kubectl
apt-mark hold kubelet kubeadm kubectl
```

## 四、Master 初始化

```bash
# 镜像源用阿里云（registry.k8s.io 直连拉大镜像会超时）
kubeadm init \
  --apiserver-advertise-address=10.0.0.194 \
  --pod-network-cidr=192.168.0.0/16 \
  --image-repository=registry.aliyuncs.com/google_containers \
  --cri-socket=unix:///run/containerd/containerd.sock

# 配置 kubectl
export KUBECONFIG=/etc/kubernetes/admin.conf
mkdir -p $HOME/.kube && cp /etc/kubernetes/admin.conf $HOME/.kube/config

# 装网络插件 Calico（镜像走 daocloud）
kubectl apply -f calico.yaml   # 镜像已改 docker.m.daocloud.io/calico/
```

## 五、Worker 加入

```bash
# master 上生成的 join 命令，在两台 worker 上执行
kubeadm join 10.0.0.194:6443 --token <token> --discovery-token-ca-cert-hash sha256:<hash>
```

## 六、验证

```bash
kubectl get nodes          # 三台全部 Ready
kubectl get pods -A        # 控制面 + calico 全部 Running
```

## ⚠️ 国内网络五大坑（最值钱的经验）

1. **registry.k8s.io 直连超时**（Google 节点被墙）→ kubeadm 用 `registry.aliyuncs.com/google_containers`
2. **docker.io 被墙** → 所有 docker 镜像改 `docker.m.daocloud.io/` 前缀
3. **quay.io / gcr.io 也被墙** → daocloud 全家桶镜像站：`quay.m.daocloud.io`、`gcr.m.daocloud.io`
4. **GitHub raw 被墙** → 清单本机下载，scp 上传
5. **大镜像在部分节点拉得极慢** → 走"master 导出 → 内网 scp → import"分发

## 常见报错

| 报错 | 原因 | 解决 |
|------|------|------|
| `conntrack not found` | 缺依赖 | `apt-get install -y conntrack ethtool socat` |
| `ImagePullBackOff` | 镜像拉不下来/不在本节点 | 换镜像源或内网分发 |
| 控制面起不来（apiserver 超时） | pause 镜像被墙 | 预拉 pause 镜像改名到 registry.k8s.io/pause:3.10.1 |
| `kubectl top` 500 | kubelet 自签证书 | metrics-server 加 `--kubelet-insecure-tls` |
