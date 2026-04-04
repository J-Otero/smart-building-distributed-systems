/*
 * Student: Jefferson Ferreira
 * ID: 21223467
 * Subject: Distributed Systems
 * Lecturer: Catriona Nic Lughadha
 * National College of Ireland
 CA - Climate Action
 Java files
 */

package ie.nci.smartbuilding.advisor;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import java.util.Random;

public class AdvisorServiceImpl extends AdvisorServiceGrpc.AdvisorServiceImplBase {

    private final Random random = new Random();

    private float solarWatts = 1200f;
    private float batteryPercent = 72f;

    @Override
    public void getSolarStatus(Empty request, StreamObserver<SolarStatus> responseObserver) {
        solarWatts = Math.max(0, solarWatts + (random.nextFloat() - 0.5f) * 200f);

        batteryPercent = Math.min(100, Math.max(0, batteryPercent + (random.nextFloat() - 0.4f) * 3f));

        SolarStatus status = SolarStatus.newBuilder()
                .setSolarWatts(Math.round(solarWatts * 10) / 10.0f)
                .setBatteryPercent(Math.round(batteryPercent * 10) / 10.0f)
                .setTimestampMs(System.currentTimeMillis())
                .build();

        System.out.println("[AdvisorService] GetSolarStatus -> solar=" + status.getSolarWatts() + "W, battery="
         + status.getBatteryPercent() + "%" );

        responseObserver.onNext(status);
        responseObserver.onCompleted();

    }

    @Override
    public void requestEnergyBudget(EnergyBudgetRequest request, StreamObserver<EnergyBudgetReply> responseObserver) {

        String deviceId = request.getDeviceId();
        float wattsNeeded = request.getWattsNeeded();
        int durationMins = request.getDurationMinutes();

        System.out.println("[AdvisorService] EnergyBudget request: " + deviceId + " needs "
         + wattsNeeded + "W for " + durationMins + "mins");

        EnergyBudgetReply.Builder reply = EnergyBudgetReply.newBuilder();

        if (batteryPercent < 10f) {
            reply.setApproved(false)
                    .setReason("Battery critically low (" + batteryPercent + "%). Request denied.")
                    .setAllocatedWatts(0);

        } else if (solarWatts >= wattsNeeded) {
            reply.setApproved(true)
                    .setReason("Solar generation (" + solarWatts + "W) sufficient.")
                    .setAllocatedWatts(wattsNeeded);

        } else if (batteryPercent >= 30f) {
            float allocated = Math.min(wattsNeeded, solarWatts + (batteryPercent / 100f) * 500f);
            reply.setApproved(true)
                    .setReason("Partial solar + battery used. Allocated " + allocated + "W. ")
                    .setAllocatedWatts(allocated);
        } else {
            reply.setApproved(false)
                    .setReason("Insufficient solar energy, and battery too low.")
                    .setAllocatedWatts(0);
        }

        responseObserver.onNext(reply.build());
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<BuildingEvent> monitor (
            StreamObserver<ClimateAlert> responseObserver) {

        System.out.println("[AdvisorService] Monitor stream opened.");

        return new StreamObserver<BuildingEvent>() {

            @Override
            public void onNext(BuildingEvent event) {
                System.out.println("[AdvisorService] Event received: " + event.getType() + " in " + event.getRoomId());

                ClimateAlert alert = buildAlert(event);
                responseObserver.onNext(alert);

            }

            @Override
            public void onError(Throwable t) {
                System.out.println("[AdvisorService] Monitor error: " + t.getMessage());

            }

            @Override
            public void onCompleted() {
                System.out.println("[AdvisorService] Monitor closed by client.");
                responseObserver.onCompleted();
            }
        };
    }

    private ClimateAlert buildAlert(BuildingEvent event) {
        String severity;
        String message;
        String action;

        switch (event.getType().toUpperCase()) {
            case "OCCUPANCY":
                severity = "LOW";
                message = "Room " + event.getRoomId()
                        + " is now occupied.";
                action = "TURN_OFF_LIGHTS_AND_HEAT";
                break;
            case "POWER_SPIKE":
                severity = "HIGH";
                message = "Power spike detected in " + event.getRoomId()
                        + ". " + event.getDetails();
                action = "REDUCE_HVAC_LOAD";
                    break;
            case "TEMP_HIGH":
                severity = "MEDIUM";
                message = "High temperature in " + event.getRoomId()
                        + ": " + event.getDetails();
                action = "ACTIVATE_VENTILATION";
                break;
            case "SOLAR_LOW":
                severity = "MEDIUM";
                message = "Solar generation dropping. " + "Switching to battery reserve.";
                action = "USE_BATTERY";
                break;
            default:
                severity = "LOW";
                message = "Event from" + event.getRoomId() + ": " + event.getDetails();
                action = "MONITOR";
        }

        return ClimateAlert.newBuilder()
                .setSeverity(severity)
                .setMessage(message)
                .setSuggestedAction(action)
                .setTimestampMs(System.currentTimeMillis())
                .build();
    }
}


