/**
 * 跨实例转发（预留）：单机部署时不需要；上多副本后，把 {@code RealtimeDeliveryPort}
 * 的实现换成"Redis Pub/Sub 或 RabbitMQ 广播 + 本机注册表落库"，连接层对外签名不变。
 *
 * <p>B-1 只把目录占住，不做实现，避免在第一版就把复杂度堆上去。</p>
 */
package com.group4.kaoyan.platform.realtime.cluster;
