package com.example.hello;

import java.net.InetAddress;
import java.time.LocalDateTime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello() throws Exception {
        // 返回 Pod 主机名：用来直观演示 Service 的负载均衡
        return "Hello from k8s! v2.0 我是 Pod "
                + InetAddress.getLocalHost().getHostName()
                + "，现在是 " + LocalDateTime.now();
    }
}
