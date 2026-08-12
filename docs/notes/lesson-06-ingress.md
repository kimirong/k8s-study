# 第 6 课：Ingress —— 7 层入口

> 目标：理解并亲手实现"浏览器 → Ingress → Service → Pod"的最后一块，用域名/路径分流
> 组件：NGINX Ingress Controller v1.11.4（k8s 1.31 兼容）

## 为什么要 Ingress？（对比 NodePort）

| | NodePort | Ingress |
|--|----------|---------|
| 访问方式 | `IP:30081` 记端口 | `域名/路径` 不用记端口 |
| 端口资源 | 30000-32767 有限，多了会撞 | 只占 80/443 两个 |
| 分流能力 | 无，只能一个服务一个端口 | **按域名、按路径分流** |
| 生产场景 | 学习/测试 | 生产标准 |

**流量路径升级**：`浏览器 → 节点IP:30080 → Service → Pod` 变成 `浏览器 → Ingress(80端口) → Service → Pod`

## 架构：Ingress = 规则 + Controller

```
Ingress 资源（你写的 YAML 规则）    ← 只是"愿望清单"
   ↓ 告诉 controller 该怎么转发
Ingress Controller（真正干活的进程）  ← nginx，跑在集群里的 Deployment
   ↓
Service → Pod
```

> 关键认知：**Ingress 资源本身不干活**，它只是声明规则；真正接收流量、转发的是 Controller。

## 安装 Ingress Controller

```bash
# 1. 下载清单（本机，GitHub 被墙节点下载不了）
curl -fsSL "https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.11.4/deploy/static/provider/baremetal/deploy.yaml" -o ingress-nginx.yaml

# 2. 国内网络：镜像换 daocloud（registry.k8s.io 直连超时）
#    registry.k8s.io/ingress-nginx/xxx  →  k8s.m.daocloud.io/ingress-nginx/xxx
sed -i 's|registry.k8s.io/|k8s.m.daocloud.io/|g; s|@sha256:[a-f0-9]\{64\}||g' ingress-nginx.yaml

# 3. 部署（baremetal 版自带 NodePort Service）
kubectl apply -f ingress-nginx.yaml
kubectl rollout status deployment/ingress-nginx-controller -n ingress-nginx
kubectl get svc -n ingress-nginx   # 看 nodePort（本次是 80:31552）
```

> 镜像坑：`registry.k8s.io` 是官方源但**直连超时**，daocloud 有它的镜像站 **`k8s.m.daocloud.io`**（注意和 docker.io 的 `docker.m.daocloud.io` 不同！）。

## 写 Ingress 规则（两种分流）

### ① 按域名（Host）分流

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: app-ingress
spec:
  ingressClassName: nginx          # 指定用哪个 Controller
  rules:
  - host: hello.test.com           # 域名 → 转发到 nginx
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: hello            # 按 Service 名字 + 端口转发
            port: { number: 80 }
  - host: spring.test.com          # 域名 → 转发到 Spring Boot
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: hello-spring
            port: { number: 8080 }
```

### ② 按路径（Path）分流 + rewrite

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: app-path-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /$2   # ★ 把前缀去掉再转发
spec:
  ingressClassName: nginx
  rules:
  - host: app.test.com
    http:
      paths:
      - path: /hello(/|$)(.*)          # 正则路径
        pathType: ImplementationSpecific
        backend:
          service: { name: hello, port: { number: 80 } }
      - path: /spring(/|$)(.*)
        pathType: ImplementationSpecific
        backend:
          service: { name: hello-spring, port: { number: 8080 } }
```

**rewrite 的作用**：`/spring/hello` 会被 nginx 去掉 `/spring` 前缀、转发 `/hello` 给 Spring Boot（因为它只认 `/hello` 这个路径）。

## 验证（无真实域名时）

```bash
# curl 用 -H 模拟域名，打到 Controller 的 NodePort
curl -H "Host: hello.test.com"  http://<节点IP>:31552/            # nginx 欢迎页
curl -H "Host: spring.test.com" http://<节点IP>:31552/hello       # Spring Boot
curl -H "Host: app.test.com"    http://<节点IP>:31552/spring/hello # 路径分流
curl -H "Host: unknown.com"     http://<节点IP>:31552/            # 404，不认的域名拒绝
```

浏览器访问：把假域名写进本机 hosts 文件

```bash
# 本机（Mac/Linux）执行，把域名指到任意节点的公网 IP
sudo sh -c 'echo "47.115.224.120 hello.test.com spring.test.com app.test.com" >> /etc/hosts'
```

⚠️ **学习环境访问必须带端口**：因为 Controller 是 **NodePort 类型**暴露的，所以要用 `:31552`：

```text
http://hello.test.com:31552            → nginx 欢迎页
http://spring.test.com:31552/hello     → Spring Boot
http://app.test.com:31552/spring/hello → 路径分流
```

**为什么生产环境不需要带端口？** 生产里 Ingress Controller 前面有云负载均衡器（LoadBalancer），会把 80/443 直接映射给域名，DNS 解析后 `http://域名` 就能访问。NodePort 只是学习环境的"无奈之举"——用节点的高位端口（30000-32767）暂时代替真实 80 端口。**Ingress 规则的逻辑不受影响**，无论走哪个端口，分流规则都一样生效。

## 常用命令

```bash
kubectl get ingress                    # 看所有 Ingress 规则
kubectl describe ingress app-ingress   # 看规则详情
kubectl delete -f app-ingress.yaml     # 删除规则
```

## 小结

1. **Ingress = 声明规则（资源） + Controller（干活的人）**，两个概念分清楚。
2. 两种分流：**按域名（多域名）**、**按路径（同域名多服务）**，rewrite-target 可改写转发路径。
3. 流量路径完整闭环了：`浏览器 → Ingress → Service → Pod`，整张架构图全部跑通。
4. 生产里 Ingress 前面往往还有云负载均衡器，但核心机制就是这节课这套。

## 进阶方向

- **HTTPS**：给 Ingress 配证书（`kubectl create secret tls ...` + `spec.tls`），做 `https://` 访问
- **灰度发布**：Ingress 注解按 Header 分流（canary），配合滚动更新做金丝雀发布
