# Kubernetes 学习笔记 ☸️

> 从零搭建 3 节点 Kubernetes 集群，并以**一个 Java 后端的视角**系统学习 k8s。
> 配套真实集群：1 Master + 2 Worker（kubeadm 部署），全部课程均在真实集群上动手验证。

## 为什么有这份仓库

作者是 Java 后端开发者，对 k8s 零基础。通过**亲手部署一套真实集群 + 部署自己的 Spring Boot 应用**来学习，而非只看文档。每课都有**可复现的操作记录 + 踩坑总结**。

## 课程地图（12 课 + 前置）

| 阶段 | 课程 | 核心收获 |
|------|------|----------|
| 搭建 | [第 0 课](docs/notes/lesson-00-cluster-setup.md) | kubeadm 搭 3 节点集群，国内网络镜像方案 |
| 基础 | [第 1 课](docs/notes/lesson-01-kubectl-basics.md) | kubectl 三板斧、资源层级、Events 时间线 |
| | [第 2 课](docs/notes/lesson-02-yaml-declarative.md) | Deployment/Service YAML、labels、调谐自愈 |
| 应用 | [第 3 课](docs/notes/lesson-03-springboot.md) | 用 Jib 打包并部署 Spring Boot |
| | [第 4 课](docs/notes/lesson-04-rolling-update-selfhealing.md) | 滚动更新、一键回滚、双层级自愈 |
| 平台 | [第 5 课](docs/notes/lesson-05-dashboard.md) | Kubernetes Dashboard 网页界面 |
| | [第 6 课](docs/notes/lesson-06-ingress.md) | Ingress 按域名/路径分流 |
| | [第 7 课](docs/notes/lesson-07-configmap-secret.md) | ConfigMap/Secret 配置管理 |
| | [第 8 课](docs/notes/lesson-08-https.md) | Ingress HTTPS，TLS 证书 |
| 存储 | [第 9 课](docs/notes/lesson-09-persistent-storage.md) | PV/PVC 持久化，部署 MySQL/Redis |
| | [第 10 课](docs/notes/lesson-10-statefulset.md) | StatefulSet 有状态编排 |
| 工程化 | [第 11 课](docs/notes/lesson-11-helm.md) | Helm 打包发布 |
| | [第 12 课](docs/notes/lesson-12-monitoring.md) | Prometheus + Grafana 监控 |

## 目录结构

```
├── docs/
│   ├── k8s-cluster-architecture.html    # 集群架构图（浏览器打开）
│   ├── cluster-status.md                # 集群速查（节点/端口/访问方式）
│   └── notes/                           # 12 课 + 前置课笔记
├── hello-springboot/                    # 示例 Spring Boot 项目 + 全部 k8s 清单
├── hello-chart/                         # 把 hello-spring 打包的 Helm chart
└── linux-service.md                     # ⚠️ 本地敏感文件（服务器密码，不提交）
```

## 怎么用这份笔记

1. 先打开 [集群架构图](docs/k8s-cluster-architecture.html) 建立全局认知
2. 按课程顺序学习，**每课都有可复现的命令**，对照自己的集群动手
3. 部署实战跟着 [第 3 课](docs/notes/lesson-03-springboot.md) 把示例项目跑起来

## 环境

- 集群：Ubuntu 22.04 × 3（1 Master + 2 Worker），k8s v1.31.14，containerd 2.2.1，Calico 3.29
- 应用：Spring Boot 3.3（Java 17），MySQL 8.0，Redis 7
- 部署工具：kubeadm、Helm、Jib（无 Docker 打镜像）

## ⚠️ 安全声明

`linux-service.md`（服务器密码）与 `docs/dashboard-token.txt`（集群 token）**仅存在于本地**，已通过 `.gitignore` 排除，不会进入仓库。请勿将真实服务器凭据上传到任何公开仓库。
