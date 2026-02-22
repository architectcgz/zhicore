package com.zhicore.migration.infrastructure.gray;

/**
 * 灰度发布阶段
 */
public enum GrayPhase {

    /**
     * 初始阶段 - 5% 流量
     */
    INITIAL(5),

    /**
     * 扩展阶段 - 20% 流量
     */
    EXPANDING(20),

    /**
     * 半量阶段 - 50% 流量
     */
    HALF(50),

    /**
     * 全量阶段 - 100% 流量
     */
    FULL(100),

    /**
     * 已回滚
     */
    ROLLED_BACK(0);

    private final int ratio;

    GrayPhase(int ratio) {
        this.ratio = ratio;
    }

    public int getRatio() {
        return ratio;
    }

    /**
     * 获取下一个阶段
     */
    public GrayPhase next() {
        return switch (this) {
            case INITIAL -> EXPANDING;
            case EXPANDING -> HALF;
            case HALF -> FULL;
            case FULL, ROLLED_BACK -> this;
        };
    }

    /**
     * 是否可以进入下一阶段
     */
    public boolean canAdvance() {
        return this != FULL && this != ROLLED_BACK;
    }
}
