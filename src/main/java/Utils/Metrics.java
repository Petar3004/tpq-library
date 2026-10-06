package Utils;

import Utils.Utils.*;

public interface Metrics {

    boolean enabled();

    long getCurrentTime();

    void addTime(TimeMetric metric, long time);

    void addNormalizedCount(int count);

    void updateMaxCount(int count);
}
