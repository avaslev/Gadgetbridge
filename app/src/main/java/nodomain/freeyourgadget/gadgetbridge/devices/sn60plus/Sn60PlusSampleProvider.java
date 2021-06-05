package nodomain.freeyourgadget.gadgetbridge.devices.sn60plus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.MiBandActivitySampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.Sn60PlusActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.Sn60PlusActivitySampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.TLW64ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.TLW64ActivitySampleDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;

public class Sn60PlusSampleProvider extends AbstractSampleProvider<Sn60PlusActivitySample> {

    public static final int TYPE_DEEP_SLEEP = 4;
    public static final int TYPE_LIGHT_SLEEP = 5;
    public static final int TYPE_ACTIVITY = 1;
    public static final int TYPE_UNKNOWN = -1;

    private GBDevice mDevice;
    private DaoSession mSession;

    public Sn60PlusSampleProvider(GBDevice device, DaoSession session) {
        super(device, session);

        mSession = session;
        mDevice = device;
    }

    @Override
    public AbstractDao<Sn60PlusActivitySample, ?> getSampleDao() {
        return getSession().getSn60PlusActivitySampleDao();
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        return Sn60PlusActivitySampleDao.Properties.Timestamp;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        return Sn60PlusActivitySampleDao.Properties.DeviceId;
    }

    @Nullable
    @Override
    protected Property getRawKindSampleProperty() {
        return Sn60PlusActivitySampleDao.Properties.RawKind;
    }

    @Override
    public int normalizeType(int rawType) {
        return rawType;
    }

    @Override
    public int toRawActivityKind(int activityKind) {
        return activityKind;
    }

    @Override
    public float normalizeIntensity(int rawIntensity) {
        return rawIntensity / (float) 4000.0;
    }

    @Override
    public Sn60PlusActivitySample createActivitySample() {
        return new Sn60PlusActivitySample();
    }

}