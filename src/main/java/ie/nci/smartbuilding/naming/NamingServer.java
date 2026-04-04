/*
 * Student: Jefferson Ferreira
 * ID: 21223467
 * Subject: Distributed Systems
 * Lecturer: Catriona Nic Lughadha
 * National College of Ireland
 CA - Climate Action
 Java files
 */

package ie.nci.smartbuilding.naming;

import io.grpc.Server;
import io.grpc.ServerBuilder;

public class NamingServer {

    public static final int PORT = 50050;

    public static void main(String[]args) throws Exception{
        Server server = ServerBuilder.forPort(PORT)
                .addService(new NamingServiceImpl())
                .build()
                .start();

        System.out.println("[NamingServer] Started on port" + PORT);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[NamingServer] Shutting down...");
            server.shutdown();
        }));

        server.awaitTermination();
    }
}
