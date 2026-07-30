#!/usr/bin/env bash
# MeterSphere「服务端执行」无响应排查脚本（在应用机 aliy-docker01 上执行）
# 用法: bash diagnose-server-exec.sh

set -euo pipefail

LOG_DIR="${MS_LOG_DIR:-/data/metersphere/logs/metersphere}"
INFO_LOG="${LOG_DIR}/info.log"

echo "======== 1) MeterSphere / Kafka / 相关容器 ========"
docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep -iE 'meter|kafka|task|runner|jmeter|nginx' || docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'

echo
echo "======== 2) 最近服务端执行相关日志 ========"
if [[ -f "$INFO_LOG" ]]; then
  grep -E '开始发送请求|接收到执行结果|推送执行结果|RESOURCE_POOL|EXECUTE_RESOURCE_POOL|调试消息推送失败|INVALID_RESOURCE_POOL|WebSocket' \
    "$INFO_LOG" | tail -n 60 || true
else
  echo "未找到 $INFO_LOG，请设置 MS_LOG_DIR"
fi

echo
echo "======== 3) Flyway 是否仍导致启动失败 ========"
if [[ -f "$INFO_LOG" ]]; then
  grep -E 'failed migration|3\.7\.2\.8|FlywayMigrateException|Error Code : 1071' "$INFO_LOG" | tail -n 20 || true
fi

echo
echo "======== 4) 本机 Kafka 端口 ========"
(ss -lntp 2>/dev/null || netstat -lntp 2>/dev/null) | grep -E '9092|9093' || echo "未监听 9092（Kafka 可能在别的机器）"

echo
echo "======== 5) 提示：资源池节点健康检查 ========"
echo "在「系统设置 → 资源池」查看节点 IP:Port，然后执行："
echo "  curl -s http://<节点IP>:<端口>/status"
echo "期望返回 data=OK。若失败，节点未启动或网络不通。"
echo
echo "======== 完成：请把以上完整输出发回 ========"
