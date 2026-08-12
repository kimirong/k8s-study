# K8s 学习笔记

> 配套集群：docs/cluster-status.md · 配套架构图：docs/k8s-cluster-architecture.html
> 学习方法：每课一个笔记，先读概念，再跟着动手做实验，最后看小结

## 课程目录

| 课 | 主题 | 状态 | 关键收获 |
|----|------|------|----------|
| [第 0 课](lesson-00-cluster-setup.md) | 环境准备与集群搭建 | ✅ 已完成 | kubeadm 3 节点集群，国内镜像五大坑 |
| [第 1 课](lesson-01-kubectl-basics.md) | kubectl 三板斧 | ✅ 已完成 | get / describe / logs，资源层级，Events 时间线 |
| [第 2 课](lesson-02-yaml-declarative.md) | YAML 与声明式 | ✅ 已完成 | Deployment/Service 结构，labels/selector，调谐自愈 |
| [第 3 课](lesson-03-springboot.md) | 部署 Spring Boot | ✅ 已完成 | 用 Jib 打包镜像，完整上线流程 |
| [第 4 课](lesson-04-rolling-update-selfhealing.md) | 滚动更新 / 回滚 / 自愈 | ✅ 已完成 | 零停机发布、一键回滚、双层级自愈 |
| [第 5 课](lesson-05-dashboard.md) | 部署 Dashboard Web 界面 | ✅ 已完成 | 网页看集群，镜像换源 + NodePort 暴露 |
| [第 6 课](lesson-06-ingress.md) | Ingress 7 层入口 | ✅ 已完成 | 按域名/路径分流，完整数据面闭环 |
| [第 7 课](lesson-07-configmap-secret.md) | ConfigMap 与 Secret | ✅ 已完成 | 配置与凭据管理，镜像不可变配置可变 |
| [第 8 课](lesson-08-https.md) | Ingress HTTPS | ✅ 已完成 | TLS 证书即 Secret，强制 HTTPS，cert-manager 自动签发 |
| [第 9 课](lesson-09-persistent-storage.md) | 持久化 PV/PVC | ✅ 已完成 | 部署 MySQL/Redis 有状态应用，Pod 死数据不死 |
| [第 10 课](lesson-10-statefulset.md) | StatefulSet | ✅ 已完成 | 稳定身份+独立存储+DNS，有状态应用的编排 |
| [第 11 课](lesson-11-helm.md) | Helm 包管理器 | ✅ 已完成 | YAML 模板化，一条命令部署/升级/回滚 |
| [第 12 课](lesson-12-monitoring.md) | 监控 Prometheus + Grafana | ✅ 已完成 | 集群+应用指标，Spring Boot Actuator + ServiceMonitor |

## 学习路径

1. 看懂架构图（控制面怎么工作、请求怎么到达应用）
2. 用 kubectl 操作集群（本课）
3. 读懂和编写 YAML（声明式思想）
4. 部署真实的 Spring Boot 应用（目标）
5. 进阶：Ingress、ConfigMap/Secret、持久化、滚动更新
