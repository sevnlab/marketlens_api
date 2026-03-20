package com.marketlens.util;


import com.sun.management.OperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;

@Slf4j
public class SystemUtil {

    private static final OperatingSystemMXBean osBean =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    /** 시스템 상태 출력 */
    public static void printStatus(String title) {
        Runtime rt = Runtime.getRuntime();

        double usedMem = (rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0);
        double totalMem = rt.totalMemory() / (1024.0 * 1024.0);
        double maxMem = rt.maxMemory() / (1024.0 * 1024.0);
        double cpuJvm = osBean.getProcessCpuLoad() * 100; // JVM 프로세스 자체의 CPU 사용률
        double cpuSys = osBean.getSystemCpuLoad() * 100; // 시스템 전체 CPU 사용률

        log.info("===========================================");
        log.info(String.format("[%s]", title));
        log.info(String.format("JVM 사용 메모리: %.2f MB / 전체: %.2f MB (최대: %.2f MB)", usedMem, totalMem, maxMem));
        log.info(String.format("CPU 사용률: JVM %.1f%%, System %.1f%%", cpuJvm, cpuSys));
        log.info(String.format("Thread count: %d", Thread.activeCount())); // JVM 내 활성 스레드 개수 표시
        log.info("===========================================");

        // 임계값 경고 (선택)
        if (cpuSys > 85) log.warn("System CPU가 85% 초과 - 루프 속도 조절 필요!"); // 해당값이 높아지면 DB 쪽 부하를 의심
        if (usedMem / maxMem * 100 > 80) log.warn("JVM 메모리 80% 초과 - GC 또는 청크 크기 조절 필요!");

        log.info("===========================================");
    }
}
