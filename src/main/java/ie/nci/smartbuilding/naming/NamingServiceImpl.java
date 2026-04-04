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

import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class NamingServiceImpl extends NamingServiceGrpc.NamingServiceImplBase {
    private final Map<String, List<ServiceInstance>> registry
            = new ConcurrentHashMap<>();

    private final List<StreamObserver<ServiceInstance>> watchers
            = new CopyOnWriteArrayList<>();

    @Override
    public void register (RegisterRequest request,
                          StreamObserver<RegisterReply> responseObserver) {
        ServiceInstance instance = request.getInstance();
        String name = instance.getServiceName();

        registry.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>()).add(instance);
        System.out.println("[NamingService] Registered: " + name + "@" + instance.getHost() + ":" + instance.getPort());

        for (StreamObserver<ServiceInstance> watcher : watchers) {
            try {
                watcher.onNext(instance);
            } catch (Exception e) {
                watchers.remove(watcher);
            }
        }

        RegisterReply reply = RegisterReply.newBuilder()
                .setSuccess(true)
                .setMessage("Registered" + name + "successfully.")
                .build();

        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    @Override
    public void discover(DiscoverRequest request,
                         StreamObserver<DiscoverReply> responseObserver) {
        String name = request.getServiceName();
        List<ServiceInstance> found = new ArrayList <>();

        if (name == null || name.isEmpty()) {
            registry.values().forEach(found::addAll);
        } else {
            List<ServiceInstance> instances= registry.get(name);
            if (instances != null) found.addAll(instances);
        }

        System.out.println("[NamingService] Discover request for '" + name + "' -> found " + found.size() + " instance(s).");

        DiscoverReply reply = DiscoverReply.newBuilder()
                .addAllInstances(found)
                .build();

        responseObserver.onNext(reply);
        responseObserver.onCompleted();

    }

    @Override
    public void watchServices(WatchRequest request,
                              StreamObserver<ServiceInstance> responseObserver) {
        String filterName = request.getServiceName();

        System.out.println("[NamingService] New watcher for: '" + (filterName.isEmpty() ? "ALL" : filterName) + "'");

        registry.forEach((name, instances) -> {
            if (filterName.isEmpty() || filterName.equals(name)) {
                for (ServiceInstance inst : instances) {
                    responseObserver.onNext(inst);
                }
            }
        });

        watchers.add(responseObserver);
    }
}
