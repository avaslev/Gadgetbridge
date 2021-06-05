/*  Copyright (C) 2015-2021 0nse, 115ek, Andreas Shimokawa, Carsten
    Pfeiffer, Daniel Dakhno, José Rebelo, Julien Pivotto, Sebastian Kranz,
    Steffen Liebergeld

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package nodomain.freeyourgadget.gadgetbridge.service.devices.sn60plus;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.format.DateFormat;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.SettingsActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.miband.MiBandSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.miband.MiBandService;
import nodomain.freeyourgadget.gadgetbridge.devices.sn60plus.Sn60PlusConst;
import nodomain.freeyourgadget.gadgetbridge.devices.sn60plus.Sn60PlusSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.tlw64.TLW64Constants;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.MiBandActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.Sn60PlusActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.CalendarEventSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CannedMessagesSpec;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetDeviceBusyAction;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetDeviceStateAction;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.IntentListener;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.battery.BatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.battery.BatteryInfoProfile;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfo;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfoProfile;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.heartrate.HeartRateProfile;
import nodomain.freeyourgadget.gadgetbridge.service.devices.miband.RealtimeSamplesSupport;
import nodomain.freeyourgadget.gadgetbridge.service.serial.GBDeviceProtocol;
import nodomain.freeyourgadget.gadgetbridge.util.AlarmUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

import static java.time.LocalDateTime.now;
import static java.util.TimeZone.getDefault;
import static org.apache.commons.lang3.math.NumberUtils.min;

public class Sn60PlusSupport extends AbstractBTLEDeviceSupport {

    private static final Logger LOG = LoggerFactory.getLogger(Sn60PlusSupport.class);

    private final BatteryInfoProfile<Sn60PlusSupport> batteryInfoProfile;
    private final IntentListener listener = new IntentListener() {
        @Override
        public void notify(Intent intent) {
            String action = intent.getAction();
            if (action.equals(DeviceInfoProfile.ACTION_DEVICE_INFO)) {
                handleDeviceInfo((DeviceInfo) intent.getParcelableExtra(DeviceInfoProfile.EXTRA_DEVICE_INFO));
            } else if (action.equals(BatteryInfoProfile.ACTION_BATTERY_INFO)) {
                handleBatteryInfo((BatteryInfo) intent.getParcelableExtra(BatteryInfoProfile.EXTRA_BATTERY_INFO));
            } else {
                LOG.warn("Unhandled intent given to listener");
            }
        }
    };

    private final HeartRateProfile<Sn60PlusSupport> heartRateProfile;
    private final DeviceInfoProfile<Sn60PlusSupport> deviceInfoProfile;

    private final GBDeviceEventBatteryInfo batteryCmd = new GBDeviceEventBatteryInfo();
    private final GBDeviceEventVersionInfo versionCmd = new GBDeviceEventVersionInfo();
    public BluetoothGattCharacteristic ctrlCharacteristic = null;
    public BluetoothGattCharacteristic notifyCharacteristic = null;
    private List<Sn60PlusActivitySample> samples = new ArrayList<>();
    private Sn60PlusRealtimeSamplesSupport realtimeSamplesSupport;

    public Sn60PlusSupport() {
        super(LOG);
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ACCESS);
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ATTRIBUTE);
        addSupportedService(GattService.UUID_SERVICE_HUMAN_INTERFACE_DEVICE);
        addSupportedService(Sn60PlusConst.UUID_SERVICE_ACTIVITY);
        addSupportedService(Sn60PlusConst.UUID_SERVICE_US1);

        addSupportedService(GattService.UUID_SERVICE_HEART_RATE);
        heartRateProfile = new HeartRateProfile<>(this);
        heartRateProfile.addListener(listener);
        addSupportedProfile(heartRateProfile);

        addSupportedService(GattService.UUID_SERVICE_DEVICE_INFORMATION);
        deviceInfoProfile = new DeviceInfoProfile<>(this);
        deviceInfoProfile.addListener(listener);
        addSupportedProfile(deviceInfoProfile);

        addSupportedService(GattService.UUID_SERVICE_BATTERY_SERVICE);
        batteryInfoProfile = new BatteryInfoProfile<>(this);
        batteryInfoProfile.addListener(listener);
        addSupportedProfile(batteryInfoProfile);
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        LOG.info("Initializing");

        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZING, getContext()));

//        ctrlCharacteristic = getCharacteristic(TLW64Constants.UUID_CHARACTERISTIC_CONTROL);
//        notifyCharacteristic = getCharacteristic(TLW64Constants.UUID_CHARACTERISTIC_NOTIFY);


//        builder.notify(notifyCharacteristic, true);

//        setTime(builder);
//        setDisplaySettings(builder);
//        sendSettings(builder);
        builder.read(getCharacteristic(Sn60PlusConst.UUID_CHARACTERISTIC_ACTIVITY_DATA));
        builder.notify(getCharacteristic(Sn60PlusConst.UUID_CHARACTERISTIC_ACTIVITY_DATA), true);
//        builder.notify(getCharacteristic(Sn60PlusConst.UUID_CHARACTERISTIC_US1_DATA), true);

//        builder.write(activityDataCharacteristic, new byte[]{TLW64Constants.CMD_BATTERY});
//        builder.write(ctrlCharacteristic, new byte[]{TLW64Constants.CMD_FIRMWARE_VERSION});

        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZED, getContext()));
        builder.setGattCallback(this);

        LOG.debug("Requesting device info!");
        deviceInfoProfile.requestDeviceInfo(builder);
        batteryInfoProfile.requestBatteryInfo(builder);
        heartRateProfile.requestBodySensorLocation(builder);

        LOG.info("Initialization Done");

        return builder;
    }

    @Override
    public boolean useAutoConnect() {
        return true;
    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);

        UUID characteristicUUID = characteristic.getUuid();
        if (Sn60PlusConst.UUID_CHARACTERISTIC_ACTIVITY_DATA.equals(characteristicUUID)) {
            handleRealtimeSteps(characteristic.getValue());
            return true;
        }  else if (MiBandService.UUID_CHARACTERISTIC_HEART_RATE_MEASUREMENT.equals(characteristicUUID)) {
            handleHeartrate(characteristic.getValue());
            return true;
        } else if (Sn60PlusConst.UUID_CHARACTERISTIC_US1_DATA.equals(characteristicUUID)) {
            logMessageContent(characteristic.getValue());
            return true;
        } else {
            LOG.info("Unhandled characteristic changed: " + characteristicUUID);
            logMessageContent(characteristic.getValue());
        }

        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public boolean onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        UUID characteristicUUID = characteristic.getUuid();
        if (Sn60PlusConst.UUID_CHARACTERISTIC_ACTIVITY_DATA.equals(characteristicUUID)) {
            handleSteps(characteristic.getValue());
            return true;
        }
        return super.onCharacteristicRead(gatt, characteristic, status);
    }

    @Override
    public void onNotification(NotificationSpec notificationSpec) {
        switch (notificationSpec.type) {
            case GENERIC_SMS:
                showNotification(TLW64Constants.NOTIFICATION_SMS, notificationSpec.sender);
                setVibration(1, 1);
                break;
            case WECHAT:
                showIcon(TLW64Constants.ICON_WECHAT);
                setVibration(1, 1);
                break;
            default:
                showIcon(TLW64Constants.ICON_MAIL);
                setVibration(1, 1);
                break;
        }
    }

    @Override
    public void onDeleteNotification(int id) {

    }

    @Override
    public void onSetTime() {
        try {
            TransactionBuilder builder = performInitialized("setTime");
            setTime(builder);
            builder.queue(getQueue());
        } catch (IOException e) {
            GB.toast(getContext(), "Error setting time: " + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
        }
    }

    @Override
    public void onSetAlarms(ArrayList<? extends Alarm> alarms) {
        try {
            TransactionBuilder builder = performInitialized("Set alarm");
            boolean anyAlarmEnabled = false;
            for (Alarm alarm : alarms) {
                anyAlarmEnabled |= alarm.getEnabled();
                Calendar calendar = AlarmUtils.toCalendar(alarm);

                int maxAlarms = 3;
                if (alarm.getPosition() >= maxAlarms) {
                    if (alarm.getEnabled()) {
                        GB.toast(getContext(), "Only 3 alarms are supported.", Toast.LENGTH_LONG, GB.WARN);
                    }
                    return;
                }

                byte repetition = 0x00;

                switch (alarm.getRepetition()) {
                    // TODO: case Alarm.ALARM_ONCE is not supported! Need to notify user somehow...
                    case Alarm.ALARM_MON:
                        repetition |= TLW64Constants.ARG_SET_ALARM_REMINDER_REPEAT_MONDAY;
                    case Alarm.ALARM_TUE:
                        repetition |= TLW64Constants.ARG_SET_ALARM_REMINDER_REPEAT_TUESDAY;
                    case Alarm.ALARM_WED:
                        repetition |= TLW64Constants.ARG_SET_ALARM_REMINDER_REPEAT_WEDNESDAY;
                    case Alarm.ALARM_THU:
                        repetition |= TLW64Constants.ARG_SET_ALARM_REMINDER_REPEAT_THURSDAY;
                    case Alarm.ALARM_FRI:
                        repetition |= TLW64Constants.ARG_SET_ALARM_REMINDER_REPEAT_FRIDAY;
                    case Alarm.ALARM_SAT:
                        repetition |= TLW64Constants.ARG_SET_ALARM_REMINDER_REPEAT_SATURDAY;
                    case Alarm.ALARM_SUN:
                        repetition |= TLW64Constants.ARG_SET_ALARM_REMINDER_REPEAT_SUNDAY;
                        break;

                    default:
                        LOG.warn("invalid alarm repetition " + alarm.getRepetition());
                        break;
                }

                byte[] alarmMessage = new byte[]{
                        TLW64Constants.CMD_ALARM,
                        (byte) repetition,
                        (byte) calendar.get(Calendar.HOUR_OF_DAY),
                        (byte) calendar.get(Calendar.MINUTE),
                        (byte) (alarm.getEnabled() ? 2 : 0),    // vibration duration
                        (byte) (alarm.getEnabled() ? 10 : 0),   // vibration count
                        (byte) (alarm.getEnabled() ? 2 : 0),    // unknown
                        (byte) 0x00,
                        (byte) (alarm.getPosition() + 1)
                };
                builder.write(ctrlCharacteristic, alarmMessage);
            }
            builder.queue(getQueue());
            if (anyAlarmEnabled) {
                GB.toast(getContext(), getContext().getString(R.string.user_feedback_miband_set_alarms_ok), Toast.LENGTH_SHORT, GB.INFO);
            } else {
                GB.toast(getContext(), getContext().getString(R.string.user_feedback_all_alarms_disabled), Toast.LENGTH_SHORT, GB.INFO);
            }
        } catch (IOException ex) {
            GB.toast(getContext(), getContext().getString(R.string.user_feedback_miband_set_alarms_failed), Toast.LENGTH_LONG, GB.ERROR, ex);
        }
    }

    @Override
    public void onSetCallState(CallSpec callSpec) {
        if (callSpec.command == CallSpec.CALL_INCOMING) {
            showNotification(TLW64Constants.NOTIFICATION_CALL, callSpec.name);
            setVibration(1, 30);
        } else {
            stopNotification();
            setVibration(0, 0);
        }
    }

    @Override
    public void onSetCannedMessages(CannedMessagesSpec cannedMessagesSpec) {

    }

    @Override
    public void onSetMusicState(MusicStateSpec stateSpec) {

    }

    @Override
    public void onSetMusicInfo(MusicSpec musicSpec) {

    }

    @Override
    public void onEnableRealtimeSteps(boolean enable) {
        enableRealtimeSamplesTimer(enable);
    }

    @Override
    public void onInstallApp(Uri uri) {

    }

    @Override
    public void onAppInfoReq() {

    }

    @Override
    public void onAppStart(UUID uuid, boolean start) {

    }

    @Override
    public void onAppDelete(UUID uuid) {

    }

    @Override
    public void onAppConfiguration(UUID appUuid, String config, Integer id) {

    }

    @Override
    public void onAppReorder(UUID[] uuids) {

    }

    @Override
    public void onFetchRecordedData(int dataTypes) {
        sendFetchCommand(TLW64Constants.CMD_FETCH_STEPS);
    }

    @Override
    public void onReset(int flags) {
        if (flags == GBDeviceProtocol.RESET_FLAGS_FACTORY_RESET) {
            try {
                TransactionBuilder builder = performInitialized("factoryReset");
                byte[] msg = new byte[]{
                        TLW64Constants.CMD_FACTORY_RESET,
                };
                builder.write(ctrlCharacteristic, msg);
                builder.queue(getQueue());
            } catch (IOException e) {
                GB.toast(getContext(), "Error during factory reset: " + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
            }
        }
    }

    @Override
    public void onHeartRateTest() {

    }

    @Override
    public void onEnableRealtimeHeartRateMeasurement(boolean enable) {

    }

    @Override
    public void onFindDevice(boolean start) {
        if (start) {
            setVibration(1, 3);
        }
    }

    @Override
    public void onSetConstantVibration(int integer) {

    }

    @Override
    public void onScreenshotReq() {

    }

    @Override
    public void onEnableHeartRateSleepSupport(boolean enable) {

    }

    @Override
    public void onSetHeartRateMeasurementInterval(int seconds) {

    }

    @Override
    public void onAddCalendarEvent(CalendarEventSpec calendarEventSpec) {

    }

    @Override
    public void onDeleteCalendarEvent(byte type, long id) {

    }

    @Override
    public void onSendConfiguration(String config) {

    }

    @Override
    public void onReadConfiguration(String config) {

    }

    @Override
    public void onTestNewFunction() {

    }

    @Override
    public void onSendWeather(WeatherSpec weatherSpec) {

    }

    private void setVibration(int duration, int count) {
        try {
            TransactionBuilder builder = performInitialized("vibrate");
            byte[] msg = new byte[]{
                    TLW64Constants.CMD_ALARM,
                    (byte) 0x00,
                    (byte) 0x00,
                    (byte) 0x00,
                    (byte) duration,
                    (byte) count,
                    (byte) 0x07,       // unknown, sniffed by original app
                    (byte) 0x01
            };
            builder.write(ctrlCharacteristic, msg);
            builder.queue(getQueue());
        } catch (IOException e) {
            LOG.warn("Unable to set vibration", e);
        }
    }

    private void setTime(TransactionBuilder transaction) {
        Calendar c = GregorianCalendar.getInstance();
        byte[] datetimeBytes = new byte[]{
                TLW64Constants.CMD_DATETIME,
                (byte) (c.get(Calendar.YEAR) / 256),
                (byte) (c.get(Calendar.YEAR) % 256),
                (byte) (c.get(Calendar.MONTH) + 1),
                (byte) c.get(Calendar.DAY_OF_MONTH),
                (byte) c.get(Calendar.HOUR_OF_DAY),
                (byte) c.get(Calendar.MINUTE),
                (byte) c.get(Calendar.SECOND)
        };
        transaction.write(ctrlCharacteristic, datetimeBytes);
    }

    private void setDisplaySettings(TransactionBuilder transaction) {
        byte[] displayBytes = new byte[]{
                TLW64Constants.CMD_DISPLAY_SETTINGS,
                (byte) 0x00,   // 1 - display distance in kilometers, 2 - in miles
                (byte) 0x00    // 1 - display 24-hour clock, 2 - for 12-hour with AM/PM
        };
        String units = GBApplication.getPrefs().getString(SettingsActivity.PREF_MEASUREMENT_SYSTEM, getContext().getString(R.string.p_unit_metric));
        if (units.equals(getContext().getString(R.string.p_unit_metric))) {
            displayBytes[1] = 1;
        } else {
            displayBytes[1] = 2;
        }

        String timeformat = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).getString(DeviceSettingsPreferenceConst.PREF_TIMEFORMAT, "auto");
        switch (timeformat) {
            case "24h":
                displayBytes[2] = 1;
                break;
            case "am/pm":
                displayBytes[2] = 2;
                break;
            case "auto":
            default:
                if (DateFormat.is24HourFormat(getContext())) {
                    displayBytes[2] = 1;
                } else {
                    displayBytes[2] = 2;
                }
        }

        transaction.write(ctrlCharacteristic, displayBytes);
        return;
    }

    private void sendSettings(TransactionBuilder builder) {
        // TODO Create custom settings page for changing hardcoded values

        // set user data
        ActivityUser activityUser = new ActivityUser();
        byte[] userBytes = new byte[]{
                TLW64Constants.CMD_USER_DATA,
                (byte) 0x00,  // unknown
                (byte) 0x00,  // step length [cm]
                (byte) 0x00,  // unknown
                (byte) activityUser.getWeightKg(),
                (byte) 0x05,  // screen on time / display timeout
                (byte) 0x00,  // unknown
                (byte) 0x00,  // unknown
                (byte) (activityUser.getStepsGoal() / 256),
                (byte) (activityUser.getStepsGoal() % 256),
                (byte) 0x00,  // raise hand to turn on screen, ON = 1, OFF = 0
                (byte) 0xff,  // unknown
                (byte) 0x00,  // unknown
                (byte) activityUser.getAge(),
                (byte) 0x00,  // gender
                (byte) 0x00,  // lost function, ON = 1, OFF = 0 TODO: find out what this does
                (byte) 0x02   // unknown
        };

        if (GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).getBoolean(DeviceSettingsPreferenceConst.PREF_LIFTWRIST_NOSHED, false)) {
            userBytes[10] = (byte) 0x01;
        }

        if (activityUser.getGender() == ActivityUser.GENDER_FEMALE) {
            userBytes[14] = 2; // female
            // default and factor from https://livehealthy.chron.com/determine-stride-pedometer-height-weight-4518.html
            if (activityUser.getHeightCm() != 0)
                userBytes[2] = (byte) Math.ceil(activityUser.getHeightCm() * 0.413);
            else
                userBytes[2] = 70; // default
        } else {
            userBytes[14] = 1; // male
            if (activityUser.getHeightCm() != 0)
                userBytes[2] = (byte) Math.ceil(activityUser.getHeightCm() * 0.415);
            else
                userBytes[2] = 78; // default
        }

        builder.write(ctrlCharacteristic, userBytes);

        // device settings
        byte[] deviceBytes = new byte[]{
                TLW64Constants.CMD_DEVICE_SETTINGS,
                (byte) 0x00,   // 1 - turns on inactivity alarm
                (byte) 0x3c,   // unknown, sniffed by original app
                (byte) 0x02,   // unknown, sniffed by original app
                (byte) 0x03,   // unknown, sniffed by original app
                (byte) 0x01,   // unknown, sniffed by original app
                (byte) 0x00    // unknown, sniffed by original app
        };

        if (GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).getBoolean(DeviceSettingsPreferenceConst.PREF_LONGSIT_SWITCH_NOSHED, false)) {
            deviceBytes[1] = (byte) 0x01;
        }

        builder.write(ctrlCharacteristic, deviceBytes);
    }

    private void showIcon(int iconId) {
        try {
            TransactionBuilder builder = performInitialized("showIcon");
            byte[] msg = new byte[]{
                    TLW64Constants.CMD_ICON,
                    (byte) iconId
            };
            builder.write(ctrlCharacteristic, msg);
            builder.queue(getQueue());
        } catch (IOException e) {
            GB.toast(getContext(), "Error showing icon: " + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
        }
    }

    private void showNotification(int type, String text) {
        try {
            TransactionBuilder builder = performInitialized("showNotification");
            int length;
            byte[] bytes;
            byte[] msg;

            // send text
            bytes = text.getBytes("EUC-JP");
            length = min(bytes.length, 18);
            msg = new byte[length + 2];
            msg[0] = TLW64Constants.CMD_NOTIFICATION;
            msg[1] = TLW64Constants.NOTIFICATION_HEADER;
            System.arraycopy(bytes, 0, msg, 2, length);
            builder.write(ctrlCharacteristic, msg);

            // send notification type
            msg = new byte[2];
            msg[0] = TLW64Constants.CMD_NOTIFICATION;
            msg[1] = (byte) type;
            builder.write(ctrlCharacteristic, msg);

            builder.queue(getQueue());
        } catch (IOException e) {
            GB.toast(getContext(), "Error showing notificaton: " + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
        }
    }

    private void stopNotification() {
        try {
            TransactionBuilder builder = performInitialized("clearNotification");
            byte[] msg = new byte[]{
                    TLW64Constants.CMD_NOTIFICATION,
                    TLW64Constants.NOTIFICATION_STOP
            };
            builder.write(ctrlCharacteristic, msg);
            builder.queue(getQueue());
        } catch (IOException e) {
            LOG.warn("Unable to stop notification", e);
        }
    }

    private void sendFetchCommand(byte type) {
        samples.clear();
        try {
            TransactionBuilder builder = performInitialized("fetchActivityData");
            builder.add(new SetDeviceBusyAction(getDevice(), getContext().getString(R.string.busy_task_fetch_activity_data), getContext()));
            byte[] msg = new byte[]{
                    type,
                    (byte) 0xfa
            };
            builder.write(ctrlCharacteristic, msg);
            builder.queue(getQueue());
        } catch (IOException e) {
            GB.toast(getContext(), "Error fetching activity data: " + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
        }
    }

    private void handleActivityData(byte[] data) {
        LOG.debug("Handle activity data: " + Arrays.toString(data));
        try (DBHandler handler = GBApplication.acquireDB()) {
            DaoSession session = handler.getDaoSession();

            Device device = DBHelper.getDevice(getDevice(), session);
            User user = DBHelper.getUser(session);
            int timestampInSeconds = (int) (System.currentTimeMillis() / 1000);
            Sn60PlusSampleProvider provider = new Sn60PlusSampleProvider(getDevice(), session);
            Sn60PlusActivitySample sample = new Sn60PlusActivitySample();
            sample.setDevice(device);
            sample.setUser(user);
            sample.setTimestamp(timestampInSeconds);
            sample.setProvider(provider);

            sample.setHeartRate(75);
            sample.setRawIntensity(ActivitySample.NOT_MEASURED);
            sample.setRawKind(MiBandSampleProvider.TYPE_ACTIVITY); // to make it visible in the charts TODO: add a MANUAL kind for that?
            sample.setSteps(10);

            provider.addGBActivitySample(sample);
        } catch (Exception ex) {
            GB.toast(getContext(), "Error saving activity data: " + ex.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
            GB.updateTransferNotification(null, "Data transfer failed", false, 0, getContext());
        }
    }

    private void requestBatteryInfo(TransactionBuilder builder) {
        LOG.debug("Requesting Battery Info!");
        LOG.debug("Requesting Battery Info!");
        BluetoothGattCharacteristic characteristic = getCharacteristic(GattService.UUID_SERVICE_BATTERY_SERVICE);
        builder.read(characteristic);
    }

    private void handleBatteryInfo(BatteryInfo info) {
        LOG.info("Received SN60-plus battery info");
        batteryCmd.level = (short) info.getPercentCharged();
        handleGBDeviceEvent(batteryCmd);
    }

    private void handleDeviceInfo(DeviceInfo info) {
        LOG.info("Received SN60-plu device info");
        LOG.info(String.valueOf(info));
        GBDeviceEventVersionInfo versionInfo = new GBDeviceEventVersionInfo();
        if (info.getHardwareRevision() != null) {
            versionInfo.hwVersion = info.getHardwareRevision();
        }
        if (info.getFirmwareRevision() != null) {
            versionInfo.fwVersion = info.getFirmwareRevision();
        }

        handleGBDeviceEvent(versionInfo);
    }

    private void handleHeartrate(byte[] value) {
        if (value.length == 2 && value[0] == 6) {
            int hrValue = (value[1] & 0xff);
            if (LOG.isDebugEnabled()) {
                LOG.debug("heart rate: " + hrValue);
            }
            RealtimeSamplesSupport realtimeSamplesSupport = getRealtimeSamplesSupport();
            realtimeSamplesSupport.setHeartrateBpm(hrValue);
            if (!realtimeSamplesSupport.isRunning()) {
                // single shot measurement, manually invoke storage and result publishing
                realtimeSamplesSupport.triggerCurrentSample();
            }
        }
    }

    private void handleSteps(byte[] value) {

        int tsTo = (int) (System.currentTimeMillis() / 1000);
        Calendar nowCalendar = new GregorianCalendar();
        int tsForm = tsTo - (nowCalendar.get(Calendar.SECOND));
        tsForm = tsForm - nowCalendar.get(Calendar.MINUTE) * 60;
        tsForm = tsForm - nowCalendar.get(Calendar.HOUR) * 60 * 60;
        LOG.debug("Get timestamp from - to: " + tsForm + '-' + tsTo + ' ' + nowCalendar);

        ActivityDTO activity = byteToActivity(value);
        ActivityDTO toDayActivity = new ActivityDTO(0, 0, 0);

        try (DBHandler handler = GBApplication.acquireDB()) {
            DaoSession session = handler.getDaoSession();
            Sn60PlusSampleProvider provider = new Sn60PlusSampleProvider(getDevice(), session);
            List<Sn60PlusActivitySample> latestSamples = provider.getActivitySamples(tsForm, tsTo);
            for(Sn60PlusActivitySample latestSample: latestSamples) {
                if (latestSample.getSteps() > 0) {
                    toDayActivity.steps = toDayActivity.steps + latestSample.getSteps();
                }
                if (latestSample.getDistanceMeters() > 0) {
                    toDayActivity.meters = toDayActivity.meters + latestSample.getDistanceMeters();
                }
                if (latestSample.getCaloriesBurnt() > 0) {
                    toDayActivity.calories = toDayActivity.calories + latestSample.getCaloriesBurnt();
                }
            }
            LOG.debug("Sum today activity: " + toDayActivity.toString());

            if (toDayActivity.steps < activity.steps) {
                Device device = DBHelper.getDevice(getDevice(), session);
                User user = DBHelper.getUser(session);

                Sn60PlusActivitySample sample = createActivitySample(device, user, provider);
                sample.setSteps(activity.steps - toDayActivity.steps);
                sample.setDistanceMeters(activity.meters - toDayActivity.meters);
                sample.setCaloriesBurnt(activity.calories - toDayActivity.calories);
                sample.setRawKind(ActivityKind.TYPE_ACTIVITY);

                provider.addGBActivitySample(sample);
                toDayActivity = activity;
            }

            getRealtimeSamplesSupport().setSteps(toDayActivity.steps);
            getRealtimeSamplesSupport().setMeters(toDayActivity.meters);
            getRealtimeSamplesSupport().setCalories(toDayActivity.calories);
            realtimeSamplesSupport.triggerCurrentSample();
            LOG.debug("Set base activity to realtime: " + activity.toString());
        } catch (Exception e) {
            LOG.warn("Unable to acquire db for saving realtime samples", e);
        }
    }

    private void handleRealtimeSteps(byte[] value) {
        ActivityDTO activity = byteToActivity(value);

        getRealtimeSamplesSupport().setSteps(activity.steps);
        getRealtimeSamplesSupport().setMeters(activity.meters);
        getRealtimeSamplesSupport().setCalories(activity.calories);
    }

    private void enableRealtimeSamplesTimer(boolean enable) {
        if (enable) {
            getRealtimeSamplesSupport().start();
        } else {
            if (realtimeSamplesSupport != null) {
                realtimeSamplesSupport.stop();
            }
        }
    }

    private Sn60PlusRealtimeSamplesSupport getRealtimeSamplesSupport() {
        if (realtimeSamplesSupport == null) {
            realtimeSamplesSupport = new Sn60PlusRealtimeSamplesSupport(1000, 1000) {

                @Override
                public void doCurrentSample() {

                    try (DBHandler handler = GBApplication.acquireDB()) {
                        DaoSession session = handler.getDaoSession();

                        Device device = DBHelper.getDevice(getDevice(), session);
                        User user = DBHelper.getUser(session);

                        Sn60PlusSampleProvider provider = new Sn60PlusSampleProvider(getDevice(), session);
                        Sn60PlusActivitySample sample = createActivitySample(device, user, provider);

                        sample.setSteps(getSteps());
                        sample.setDistanceMeters(getMeters());
                        sample.setCaloriesBurnt(getCalories());
                        sample.setRawKind(ActivityKind.TYPE_ACTIVITY);

                        provider.addGBActivitySample(sample);

                        if (LOG.isDebugEnabled()) {
                            LOG.debug("realtime sample: " + sample);
                        }

                        Intent intent = new Intent(DeviceService.ACTION_REALTIME_SAMPLES)
                                .putExtra(DeviceService.EXTRA_REALTIME_SAMPLE, sample);
                        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);

                    } catch (Exception e) {
                        LOG.warn("Unable to acquire db for saving realtime samples", e);
                    }
                }
            };
        }
        return realtimeSamplesSupport;
    }

    private ActivityDTO byteToActivity(byte[] value)
    {
        int steps = (value[0] & 0xff) | ((value[1] & 0xff) << 8) | ((value[2] & 0xff) << 16);
        int meters = (value[3] & 0xff) | ((value[4] & 0xff) << 8) | ((value[5] & 0xff) << 16);
        int calories = (value[6] & 0xff) | ((value[7] & 0xff) << 8) | ((value[8] & 0xff) << 16);

        ActivityDTO activityDTO = new ActivityDTO(steps, meters, calories);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Create activity: " + activityDTO.toString());
        }

        return activityDTO;
    }

    public Sn60PlusActivitySample createActivitySample(Device device, User user, Sn60PlusSampleProvider provider) {
        int timestampInSeconds = (int) (System.currentTimeMillis() / 1000);
        Sn60PlusActivitySample sample = provider.createActivitySample();
        sample.setDevice(device);
        sample.setUser(user);
        sample.setTimestamp(timestampInSeconds);
        sample.setProvider(provider);
        sample.setHeartRate(ActivitySample.NOT_MEASURED);

        return sample;
    }
}
