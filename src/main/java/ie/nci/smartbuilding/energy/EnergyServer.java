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

import ie.nci.smartbuilding.naming.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.util.UUID;

public class EnergyServer {

    public static final int PORT = 50052;
    public static final String SERVICE_NAME = "EnergyService";
    public static final String HOST = "localhost";
    public static final int NAMING_PORT = 50050;

    public static void main(String[] args) throws Exception {
        Server server = ServerBuilder.forPort(PORT)
                .addService(new EnergyServiceImpl())
                .build()
                .start();

        System.out.println("[EnergyServer] Started on port" + PORT);

        registerWithNamingService();

        Runtime.getRuntime().addShutdownHook(new Thread (() -> {
            System.out.println("[EnergyServer] Shutting down...");
            server.shutdown();
        }));

        server.awaitTermination();
    }

    private static void registerWithNamingService() {
        try {
            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress(HOST, NAMING_PORT)
                    .usePlaintext()
                    .build();

            NamingServiceGrpc.NamingServiceBlockingStub stub =
                    NamingServiceGrpc.newBlockingStub(channel);

            ServiceInstance instance = ServiceInstance.newBuilder()
                    .setServiceName(SERVICE_NAME)
                    .setHost(HOST)
                    .setPort(PORT)
                    .setInstanceId(UUID.randomUUID().toString())
                    .setRegisteredAtMs(System.currentTimeMillis())
                    .build();

            RegisterReply reply = stub.register(
                    RegisterRequest.newBuilder()
                            .setInstance(instance)
                            .build()
            );

            System.out.println("[EnergyServer] Naming registration: " + reply.getMessage());

            channel.shutdown();
        } catch (Exception e) {
            System.err.println("[EnergyServer] Could not register: " + e.getMessage());
        }
    }
}
