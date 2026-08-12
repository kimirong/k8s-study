# 第 8 课：Ingress HTTPS（TLS 证书）

> 目标：给 Ingress 配 HTTPS，理解「TLS 证书 = Secret」以及生产里怎么自动签发证书
> 关联：用到了第 7 课的 Secret——**TLS 证书正是"Nacos 替代不了"的那个 Secret**

## HTTPS 原理（k8s 视角）

```
浏览器 ──TLS握手──► Ingress Controller ──► Service ──► Pod
                        │
                        └── 出示证书（存在 Secret 里）
```

- **TLS 证书 = 证书(crt) + 私钥(key)**，两个文件，打包成一个 Secret
- Secret 类型：`kubernetes.io/tls`（专门存证书）
- Ingress 的 `spec.tls` 段引用这个 Secret，Controller 自动加载

## 三步配置 HTTPS

### ① 生成证书（学习用自签，生产用真实 CA）

```bash
# 自签一张 *.test.com 的通配证书
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout /root/tls.key -out /root/tls.crt \
  -subj "/CN=*.test.com" \
  -addext "subjectAltName=DNS:*.test.com,DNS:hello.test.com,DNS:spring.test.com,DNS:app.test.com"
```

### ② 创建 TLS Secret

```bash
kubectl create secret tls app-tls --cert=/root/tls.crt --key=/root/tls.key
```

### ③ Ingress 加 tls 段

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: app-ingress
  annotations:
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"   # 强制 HTTP→HTTPS
spec:
  ingressClassName: nginx
  tls:                               # ★ HTTPS 配置
  - hosts:
    - hello.test.com
    - spring.test.com
    secretName: app-tls              # 证书存在这个 Secret
  rules:
  - host: hello.test.com
    ...
```

## 验证

```bash
# HTTPS 访问（443 的 NodePort 是 31575）
curl -sk https://127.0.0.1:31575/ -H "Host: hello.test.com"         # nginx
curl -sk https://127.0.0.1:31575/hello -H "Host: spring.test.com"   # Spring Boot

# 看 nginx 出示的证书
echo | openssl s_client -connect 127.0.0.1:31575 -servername hello.test.com 2>/dev/null \
  | openssl x509 -noout -subject -issuer

# 强制跳转：HTTP(31552) 访问 → 308 跳到 https://域名/
curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" http://127.0.0.1:31552/ -H "Host: hello.test.com"
```

浏览器访问：`https://hello.test.com:31575`（自签证书会提示不安全 → 高级 → 继续访问）

> ⚠️ 学习环境说明：强制跳转的目标是标准 443，但我们的 Ingress 在 31575，所以浏览器跟随跳转会失败，需要**手动带上端口**访问。生产环境 443 就是真实端口，无此问题。

## 生产方案：自动签发真实证书（cert-manager）

学习用自签证书，但**生产必须用真实 CA 签发的证书**，手动申请太麻烦，标准做法是：

```
cert-manager（一个 k8s 控制器，和 Ingress Controller 同类的角色）
   │ 监听 Ingress 里的 annotation
   │ 自动向 Let's Encrypt 申请证书
   ▼
自动创建/更新 TLS Secret ← 然后 Ingress 照常引用
```

```yaml
# Ingress 里只需加两行注解，cert-manager 自动搞定一切
annotations:
  cert-manager.io/cluster-issuer: "letsencrypt-prod"
```

## 常用命令

```bash
kubectl get secret app-tls -o yaml        # 看证书 Secret
kubectl describe ingress app-ingress      # 看 TLS 配置生效情况
kubectl delete secret app-tls             # 删除
```

## 小结

1. **HTTPS = 证书存 Secret + Ingress 引用**，三步搞定。
2. 自签证书用于学习；**生产用 cert-manager + Let's Encrypt 自动签发**，不用手动管证书。
3. `force-ssl-redirect` 强制 HTTP→HTTPS，生产必备。
4. 证书正是第 7 课说的"Nacos 替代不了"的 Secret——这也验证了 ConfigMap/Secret 在平台层的必要性。
