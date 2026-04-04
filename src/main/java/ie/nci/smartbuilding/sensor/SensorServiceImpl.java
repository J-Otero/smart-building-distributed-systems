/*
 * Student: Jefferson Ferreira
 * ID: 21223467
 * Subject: Distributed Systems
 * Lecturer: Catriona Nic Lughadha
 * National College of Ireland
 CA - Climate Action
 Java files
 */

package ie.nci.smartbuilding.sensor;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.Random;

public class SensorServiceImpl extends SensorServiceGrpc.SensorServiceImplBase {

    private final Random random = new Random();

    @Override
    public void getCurrentReading(ReadingRequest request, StreamObserver<Reading> responseObserver) {

        String roomId = request.getRoomId();

        if (roomId == null || roomId.trim().isEmpty()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("room_id must not be empty.")
                            .asRuntimeException()
            );
            return;
        }

        Reading reading = buildReading(roomId);

        System.out.println("[SensorService] GetCurrentReading: "
            + roomId + " -> temp=" + reading.getTemperatureC()
            + "C, humidity=" + reading.getHumidity()
            + "%, occupied=" + reading.getOccupied());

        responseObserver.onNext(reading);
        responseObserver.onCompleted();
    }

    @Override
    public void streamReadings(StreamRequest request, StreamObserver<Reading> responseObserver) {

        String roomId = request.getRoomId();
        int intervalSeconds = request.getIntervalSeconds();

        if (roomId == null || roomId.trim().isEmpty()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("room_id must not be empty.")
                            .asRuntimeException()
            );
            return;
        }

        if (intervalSeconds < 1) intervalSeconds = 2;

        System.out.println("[SensorService] StreamReadings started: " + roomId + " every " + intervalSeconds + "s");

        try{
            for (int i = 0; i < 20; i++) {
                Reading reading = buildReading(roomId);
                responseObserver.onNext(reading);
                Thread.sleep(intervalSeconds * 1000L);
            }
            responseObserver.onCompleted();
            System.out.println("[SensorService] StreamReadings completed: " + roomId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(
                    Status.CANCELLED
                            .withDescription("Streaming was interrupted.")
                            .asRuntimeException()
            );
        }
    }

    private Reading buildReading(String roomId) {
        float temp = 18.0f + random.nextFloat() * 8.0f;
        float humidity = 35.0f + random.nextFloat() * 30.0f;
        boolean occupied = random.nextBoolean();

        return Reading.newBuilder()
                .setRoomId(roomId)
                .setTemperatureC(Math.round(temp * 10) / 10.0f)
                .setHumidity(Math.round(humidity * 10) / 10.0f)
                .setOccupied(occupied)
                .setTimestampMs(System.currentTimeMillis())
                .build();
    }
}
