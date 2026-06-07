package com.novel2screenplay.pipeline;

/**
 * 只负责：把流水线各阶段的进展实时回调给调用方（如 SSE 流式输出）。
 * 不需要进度（普通同步接口）时传 {@link #NOOP} 即可，流水线对两者一视同仁。
 */
@FunctionalInterface
public interface ProgressListener {

    /**
     * @param phase   阶段标识（如 split / bible / synopsis / extract / assemble / validate / episode / done）
     * @param message 给人看的进度文案
     */
    void onProgress(String phase, String message);

    /** 不关心进度时的空实现。 */
    ProgressListener NOOP = (phase, message) -> { };
}
