package Utils;

import Utils.Utils.*;

public final class NoMetrics implements Metrics {

    public static final NoMetrics INSTANCE = new NoMetrics();

    private NoMetrics() {}

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public long getCurrentTime() {
        return 0;
    }

    @Override
    public void addTime(TimeMetric metric, long time) {
        // intentionally empty
    }

    @Override
    public void addNormalizedCount(int count) {
        // intentionally empty
    }

    @Override
    public void updateMaxCount(int count) {
        // intentionally empty
    }
}
