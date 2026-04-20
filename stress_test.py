"""
ChillChat QPS 压力测试：MySQL 直接查询 vs Redis Cache-Aside
=============================================================
用法：
    pip install requests
    python stress_test.py

前置条件：
    后端服务已在 http://localhost:8080 运行（mvn spring-boot:run）
"""

import time
import statistics
import requests
from concurrent.futures import ThreadPoolExecutor, as_completed

BASE_URL = "http://localhost:8080"
MYSQL_URL = f"{BASE_URL}/api/benchmark/mysql?page=1&size=20"
REDIS_URL = f"{BASE_URL}/api/benchmark/redis?page=1&size=20"
CLEAR_URL = f"{BASE_URL}/api/benchmark/cache"

CONCURRENCY = 50       # 并发线程数
TOTAL_REQUESTS = 500   # 每组总请求数
WARMUP_REQUESTS = 20   # 预热请求数（不计入统计）


def single_request(url: str) -> tuple[bool, float]:
    """发送单次请求，返回 (是否成功, 耗时ms)"""
    t0 = time.perf_counter()
    try:
        resp = requests.get(url, timeout=10)
        elapsed_ms = (time.perf_counter() - t0) * 1000
        return resp.status_code == 200, elapsed_ms
    except Exception:
        elapsed_ms = (time.perf_counter() - t0) * 1000
        return False, elapsed_ms


def run_benchmark(label: str, url: str, total: int, concurrency: int) -> dict:
    """并发压测，返回统计结果"""
    latencies: list[float] = []
    success = 0
    failure = 0

    start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(single_request, url) for _ in range(total)]
        for fut in as_completed(futures):
            ok, ms = fut.result()
            if ok:
                success += 1
                latencies.append(ms)
            else:
                failure += 1
    elapsed = time.perf_counter() - start

    qps = total / elapsed
    latencies.sort()
    p50 = statistics.median(latencies) if latencies else 0
    p95 = latencies[int(len(latencies) * 0.95)] if latencies else 0
    p99 = latencies[int(len(latencies) * 0.99)] if latencies else 0
    avg = statistics.mean(latencies) if latencies else 0

    return {
        "label": label,
        "total": total,
        "success": success,
        "failure": failure,
        "elapsed_s": round(elapsed, 2),
        "qps": round(qps, 1),
        "avg_ms": round(avg, 1),
        "p50_ms": round(p50, 1),
        "p95_ms": round(p95, 1),
        "p99_ms": round(p99, 1),
    }


def print_result(r: dict):
    print(f"\n{'='*50}")
    print(f"  {r['label']}")
    print(f"{'='*50}")
    print(f"  请求总数    : {r['total']}  (成功 {r['success']} / 失败 {r['failure']})")
    print(f"  总耗时      : {r['elapsed_s']} s")
    print(f"  ★ QPS      : {r['qps']} req/s")
    print(f"  平均延迟    : {r['avg_ms']} ms")
    print(f"  P50 延迟    : {r['p50_ms']} ms")
    print(f"  P95 延迟    : {r['p95_ms']} ms")
    print(f"  P99 延迟    : {r['p99_ms']} ms")


def check_server():
    try:
        r = requests.get(MYSQL_URL, timeout=5)
        return r.status_code == 200
    except Exception as e:
        print(f"  连接失败: {e}")
        return False


def main():
    print("\n┌─────────────────────────────────────────────────┐")
    print("│        ChillChat Redis vs MySQL 压力测试          │")
    print(f"│  并发: {CONCURRENCY} 线程  总请求: {TOTAL_REQUESTS}/组  预热: {WARMUP_REQUESTS}     │")
    print("└─────────────────────────────────────────────────┘")

    print("\n[检查] 等待后端就绪...")
    if not check_server():
        print("❌ 后端未响应，请先启动: cd backend && mvn spring-boot:run")
        return

    print("✅ 后端连接正常\n")

    # ── 第一组：纯 MySQL ─────────────────────────────────────
    print(f"[1/2] 预热 MySQL 接口 ({WARMUP_REQUESTS} 次)...")
    with ThreadPoolExecutor(max_workers=10) as pool:
        list(pool.map(lambda _: single_request(MYSQL_URL), range(WARMUP_REQUESTS)))

    print(f"[1/2] 压测 MySQL 接口 ({TOTAL_REQUESTS} 请求 × {CONCURRENCY} 并发)...")
    mysql_result = run_benchmark(
        "纯 MySQL（无缓存）", MYSQL_URL, TOTAL_REQUESTS, CONCURRENCY
    )
    print_result(mysql_result)

    # ── 第二组：Redis Cache-Aside ────────────────────────────
    # 先清除旧缓存，确保公平起点（第一次 miss，后续 hit）
    try:
        requests.delete(CLEAR_URL, timeout=5)
        print("\n[cache] 已清除旧 bench 缓存")
    except Exception:
        pass

    print(f"\n[2/2] 预热 Redis 接口 ({WARMUP_REQUESTS} 次，触发首次 cache-miss 写入)...")
    with ThreadPoolExecutor(max_workers=10) as pool:
        list(pool.map(lambda _: single_request(REDIS_URL), range(WARMUP_REQUESTS)))

    print(f"[2/2] 压测 Redis 接口 ({TOTAL_REQUESTS} 请求 × {CONCURRENCY} 并发)...")
    redis_result = run_benchmark(
        "Redis Cache-Aside（命中缓存）", REDIS_URL, TOTAL_REQUESTS, CONCURRENCY
    )
    print_result(redis_result)

    # ── 对比摘要 ─────────────────────────────────────────────
    qps_gain = redis_result["qps"] / mysql_result["qps"] if mysql_result["qps"] > 0 else 0
    lat_reduce = (1 - redis_result["avg_ms"] / mysql_result["avg_ms"]) * 100 if mysql_result["avg_ms"] > 0 else 0

    print("\n")
    print("╔══════════════════════════════════════════════════╗")
    print("║                  对比摘要                         ║")
    print("╠══════════════════════════════════════════════════╣")
    print(f"║  MySQL  QPS : {mysql_result['qps']:>8} req/s                     ║")
    print(f"║  Redis  QPS : {redis_result['qps']:>8} req/s                     ║")
    print(f"║  QPS 提升   : {qps_gain:>7.1f}x                           ║")
    print(f"║  延迟降低   : {lat_reduce:>6.1f}%                            ║")
    print(f"║  MySQL P99  : {mysql_result['p99_ms']:>6} ms                         ║")
    print(f"║  Redis P99  : {redis_result['p99_ms']:>6} ms                         ║")
    print("╚══════════════════════════════════════════════════╝")
    print("\n面试参考话术：")
    print(f"  「在 {CONCURRENCY} 并发下，引入 Redis Cache-Aside 缓存后，")
    print(f"   GET /api/posts 接口 QPS 从 {mysql_result['qps']} 提升至 {redis_result['qps']}，")
    print(f"   提升约 {qps_gain:.1f} 倍，平均延迟从 {mysql_result['avg_ms']} ms 降至 {redis_result['avg_ms']} ms。」\n")


if __name__ == "__main__":
    main()
