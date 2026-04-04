/*
 * Student: Jefferson Ferreira
 * ID: 21223467
 * Subject: Distributed Systems
 * Lecturer: Catriona Nic Lughadha
 * National College of Ireland
 CA - Climate Action
 Java files
 */

package ie.nci.smartbuilding.energy;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class EnergyServiceImpl extends EnergyServiceGrpc.EnergyServiceImplBase {

    private final Random random = new Random();

    private final Map<String, String> deviceStates
        = new ConcurrentHashMap<>();

    private final Map<String, Float> deviceWatts
            = new ConcurrentHashMap<>();

    public EnergyServiceImpl() {
        deviceStates.put("LIGHTS_A201", "OFF");
        deviceWatts.put("LIGHTS_A201", 60f);
        deviceStates.put("HEAT_A201", "OFF");
        deviceWatts.put("HEAT_A201", 1500f);
        deviceStates.put("AC_A201", "OFF");
        deviceWatts.put("AC_A201", 2000f);
        deviceStates.put("VENT_A201", "OFF");
        deviceWatts.put("VENT_A201", 300f);
        deviceStates.put("LIGHTS_B101", "OFF");
        deviceWatts.put("LIGHTS_B101", 60f);
        deviceStates.put("HEAT_B101", "OFF");
        deviceWatts.put("HEAT_B101", 1500f);
    }

    @Override
    public void controlDevice(DeviceCommand request, StreamObserver<CommandResult> responseObserver) {

        String deviceId = request.getDeviceId();
        String action = request.getAction().toUpperCase();

        if (!deviceStates.containsKey(deviceId)) {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription("Device not found: " + deviceId)
                            .asRuntimeException()
            );
            return;
        }

        if (!action.equals("ON") && !action.equals("OFF")) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Action must be ON or OFF, got: " + action)
                            .asRuntimeException()
            );
            return;
        }

        deviceStates.put(deviceId, action);

        String msg = deviceId + " turned " + action + " by " + request.getRequestedBy();
        System.out.println("[EnergyService] Control Device: " + msg);

        responseObserver.onNext(CommandResult.newBuilder()
                .setSuccess(true)
                .setMessage(msg)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getDeviceStatus(DeviceId request, StreamObserver<DeviceStatus> responseObserver) {

        String deviceId = request.getDeviceId();

        if (!deviceStates.containsKey(deviceId)) {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription("Device not found: " + deviceId)
                            .asRuntimeException()
            );
            return;
        }

        String state = deviceStates.get(deviceId);
        float watts = state.equals("ON")
                ? deviceWatts.getOrDefault(deviceId, 0f)
                : 0f;

        System.out.println("[EnergyService] GetDeviceStatus: " + deviceId + " -> " + state + " @ " + watts + "W");

        responseObserver.onNext(DeviceStatus.newBuilder()
                .setDeviceId(deviceId)
                .setState(state)
                .setWatts(watts)
                .setTimestampMs(System.currentTimeMillis())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<UsageSample> reportUsage(
            StreamObserver<UsageSummary> responseObserver) {
        return new StreamObserver<UsageSample>() {

            int count = 0;
            float sumWatts = 0;
            float peakWatts = 0;
            long firstTimestamp = -1;
            long lastTimestamp = -1;

            @Override
            public void onNext(UsageSample sample) {
                count++;
                sumWatts += sample.getWatts();
                peakWatts = Math.max(peakWatts, sample.getWatts());
                if (firstTimestamp == -1) {
                    firstTimestamp = sample.getTimestampMs();
                }
                lastTimestamp = sample.getTimestampMs();
                System.out.println("[EnergyService] UsageSample: " + sample.getDeviceId() + " @ " + sample.getWatts() + "W");
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("[EnergyService] ReportUsage error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                float avgWatts = count > 0 ? sumWatts / count: 0f;

                long durationMs = (lastTimestamp > firstTimestamp)
                        ? lastTimestamp - firstTimestamp
                        : 60000L;
                float totalKwh = avgWatts * (durationMs / 3_600_000.0f);

                System.out.println("[EnergyService] UsageSummary: " + "samples=" + count + ", avg=" + avgWatts + "W" + ", peak=" + peakWatts + "W");

                responseObserver.onNext(UsageSummary.newBuilder()
                        .setSamples(count)
                        .setAvgWatts(avgWatts)
                        .setPeakWatts(peakWatts)
                        .setTotalKwh(totalKwh)
                        .build());
                responseObserver.onCompleted();
            }
        };
    }
}


