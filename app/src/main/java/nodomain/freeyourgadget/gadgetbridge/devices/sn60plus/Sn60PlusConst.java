package nodomain.freeyourgadget.gadgetbridge.devices.sn60plus;

import java.util.UUID;
import static nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport.BASE_UUID;

public class Sn60PlusConst  {
    public static final UUID UUID_SERVICE_ACTIVITY = UUID.fromString(String.format(BASE_UUID, "FEEA"));
    public static final UUID UUID_SERVICE_US1 = UUID.fromString(String.format(BASE_UUID, "FEEA"));
    public static final UUID UUID_SERVICE_US2 = UUID.fromString(String.format(BASE_UUID, "FCBA"));

    public static final UUID UUID_CHARACTERISTIC_ACTIVITY_DATA = UUID.fromString(String.format(BASE_UUID, "FEE1"));
    public static final UUID UUID_CHARACTERISTIC_US1_DATA = UUID.fromString(String.format(BASE_UUID, "FEE1"));
}
