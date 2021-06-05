package nodomain.freeyourgadget.gadgetbridge.service.devices.sn60plus;

import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.sn60plus.Sn60PlusSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.Sn60PlusActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.service.devices.miband.RealtimeSamplesSupport;

public abstract class Sn60PlusRealtimeSamplesSupport extends RealtimeSamplesSupport {

    protected int meters;
    protected int calories;

    private int lastMeters;
    private int lastCalories;

    public Sn60PlusRealtimeSamplesSupport(long delay, long period) {
        super(delay, period);
    }

    public synchronized void setMeters(int metersPerMinute) {
        this.meters = this.setMetric(metersPerMinute);
    }

    public synchronized int getMeters() {
        return getMetric(meters, lastMeters);
    }

    public synchronized void setCalories(int caloriesPerMinute) {
        this.meters = this.setMetric(caloriesPerMinute);
    }

    public synchronized int getCalories() {
        return getMetric(meters, lastMeters);
    }

    private int setMetric(int metricPerMinute) {
        if (metricPerMinute == ActivitySample.NOT_MEASURED || metricPerMinute >= 0) {
            return metricPerMinute;
        }
        return ActivitySample.NOT_MEASURED;
    }

    private int getMetric(int current, int last) {
        if (current == ActivitySample.NOT_MEASURED) {
            return ActivitySample.NOT_MEASURED;
        }
        if (last == 0) {
            return ActivitySample.NOT_MEASURED; // wait until we have a delta between two samples
        }
        int delta = current - last;
        if (delta < 0) {
            return 0;
        }
        return delta;
    }

    @Override
    protected synchronized void resetCurrentValues() {
        super.resetCurrentValues();
        if (meters >= lastMeters) {
            lastMeters = meters;
        }
        meters = ActivitySample.NOT_MEASURED;

        if (calories >= lastCalories) {
            lastCalories = calories;
        }
        calories = ActivitySample.NOT_MEASURED;
    }
}
