/*
 * Student: Jefferson Ferreira
 * ID: 21223467
 * Subject: Distributed Systems
 * Lecturer: Catriona Nic Lughadha
 * National College of Ireland
 CA - Climate Action
 Java files
 */


package ie.nci.smartbuilding.gui;

import ie.nci.smartbuilding.advisor.AdvisorServiceGrpc;
import ie.nci.smartbuilding.advisor.BuildingEvent;
import ie.nci.smartbuilding.advisor.ClimateAlert;
import ie.nci.smartbuilding.advisor.EnergyBudgetReply;
import ie.nci.smartbuilding.advisor.EnergyBudgetRequest;
import ie.nci.smartbuilding.advisor.SolarStatus;
import ie.nci.smartbuilding.energy.CommandResult;
import ie.nci.smartbuilding.energy.DeviceCommand;
import ie.nci.smartbuilding.energy.DeviceId;
import ie.nci.smartbuilding.energy.DeviceStatus;
import ie.nci.smartbuilding.energy.EnergyServiceGrpc;
import ie.nci.smartbuilding.energy.UsageSample;
import ie.nci.smartbuilding.energy.UsageSummary;
import ie.nci.smartbuilding.naming.DiscoverReply;
import ie.nci.smartbuilding.naming.DiscoverRequest;
import ie.nci.smartbuilding.naming.NamingServiceGrpc;
import ie.nci.smartbuilding.naming.ServiceInstance;
import ie.nci.smartbuilding.sensor.Reading;
import ie.nci.smartbuilding.sensor.ReadingRequest;
import ie.nci.smartbuilding.sensor.SensorServiceGrpc;
import ie.nci.smartbuilding.sensor.StreamRequest;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SmartBuildingGUI extends JFrame {

    private ManagedChannel namingChannel;
    private ManagedChannel sensorChannel;
    private ManagedChannel energyChannel;
    private ManagedChannel advisorChannel;

    private NamingServiceGrpc.NamingServiceBlockingStub namingStub;
    private SensorServiceGrpc.SensorServiceBlockingStub sensorStub;
    private EnergyServiceGrpc.EnergyServiceBlockingStub energyStub;
    private AdvisorServiceGrpc.AdvisorServiceBlockingStub advisorStub;
    private AdvisorServiceGrpc.AdvisorServiceStub advisorAsyncStub;

    private JTextArea discoveryOutput;
    private JTextArea sensorOutput;
    private JTextArea energyOutput;
    private JTextArea advisorOutput;

    private JTextField sensorRoomField;
    private JComboBox<String> deviceCombo;
    private JComboBox<String> actionCombo;
    private JTextField advisorDeviceField;
    private JTextField wattsField;
    private JTextField durationField;

    private StreamObserver<BuildingEvent> monitorRequestObserver;

    public SmartBuildingGUI() {
        super("Smart Climate Controller - NCI | Jefferson Ferreira 21223467");
        initChannels();
        buildUI();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
        setSize(900, 700);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initChannels() {
        namingChannel = ManagedChannelBuilder
                .forAddress("localhost", 50050)
                .usePlaintext()
                .build();
        sensorChannel = ManagedChannelBuilder
                .forAddress("localhost", 50051)
                .usePlaintext()
                .build();
        energyChannel = ManagedChannelBuilder
                .forAddress("localhost", 50052)
                .usePlaintext()
                .build();
        advisorChannel = ManagedChannelBuilder
                .forAddress("localhost", 50053)
                .usePlaintext()
                .build();

        namingStub = NamingServiceGrpc
                .newBlockingStub(namingChannel);
        sensorStub = SensorServiceGrpc
                .newBlockingStub(sensorChannel);
        energyStub = EnergyServiceGrpc
                .newBlockingStub(energyChannel);
        advisorStub = AdvisorServiceGrpc
                .newBlockingStub(advisorChannel);
        advisorAsyncStub = AdvisorServiceGrpc
                .newStub(advisorChannel);
    }

    private void buildUI() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Discovery", buildDiscoveryTab());
        tabs.addTab("Sensor", buildSensorTab());
        tabs.addTab("Energy", buildEnergyTab());
        tabs.addTab("Advisor", buildAdvisorTab());
        add(tabs);
    }

    private JPanel buildDiscoveryTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        discoveryOutput = new JTextArea();
        discoveryOutput.setEditable(false);
        discoveryOutput.setFont(
                new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JButton discoverBtn = new JButton("Discover All Services");
        discoverBtn.addActionListener(e -> discoverServices());

        panel.add(new JScrollPane(discoveryOutput),
                BorderLayout.CENTER);
        panel.add(discoverBtn, BorderLayout.SOUTH);
        return panel;
    }

    private void discoverServices() {
        try {
            DiscoverReply reply = namingStub.discover(
                    DiscoverRequest.newBuilder()
                            .setServiceName("")
                            .build());

            StringBuilder sb = new StringBuilder(
                    "=== Registered Services ===\n");

            for (ServiceInstance inst : reply.getInstancesList()) {
                sb.append(String.format(
                        "  %-20s @ %s:%d  (id: %s)\n",
                        inst.getServiceName(),
                        inst.getHost(),
                        inst.getPort(),
                        inst.getInstanceId().substring(0, 8)));
            }

            if (reply.getInstancesList().isEmpty()) {
                sb.append("  (none registered yet)\n");
            }

            discoveryOutput.setText(sb.toString());

        } catch (StatusRuntimeException ex) {
            discoveryOutput.setText("ERROR: "
                    + ex.getStatus().getDescription());
        }
    }

    private JPanel buildSensorTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("Room ID:"));
        sensorRoomField = new JTextField("A201", 8);
        controls.add(sensorRoomField);

        JButton readBtn = new JButton("Get Reading (Unary)");
        JButton streamBtn = new JButton("Stream Readings");
        controls.add(readBtn);
        controls.add(streamBtn);

        sensorOutput = new JTextArea();
        sensorOutput.setEditable(false);
        sensorOutput.setFont(
                new Font(Font.MONOSPACED, Font.PLAIN, 12));

        readBtn.addActionListener(e -> getSensorReading());
        streamBtn.addActionListener(e -> streamSensorReadings());

        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(sensorOutput), BorderLayout.CENTER);
        return panel;
    }

    private void getSensorReading() {
        String roomId = sensorRoomField.getText().trim();
        try {
            Reading r = sensorStub.getCurrentReading(
                    ReadingRequest.newBuilder()
                            .setRoomId(roomId)
                            .build());
            sensorOutput.append(String.format(
                    "[Unary] Room: %s | Temp: %.1fC | "
                            + "Humidity: %.1f%% | Occupied: %s\n",
                    r.getRoomId(),
                    r.getTemperatureC(),
                    r.getHumidity(),
                    r.getOccupied()));
        } catch (StatusRuntimeException ex) {
            sensorOutput.append("ERROR: "
                    + ex.getStatus().getDescription() + "\n");
        }
    }

    private void streamSensorReadings() {
        String roomId = sensorRoomField.getText().trim();
        new Thread(() -> {
            try {
                Iterator<Reading> stream = sensorStub
                        .withDeadlineAfter(30, TimeUnit.SECONDS)
                        .streamReadings(StreamRequest.newBuilder()
                                .setRoomId(roomId)
                                .setIntervalSeconds(2)
                                .build());
                int count = 0;
                while (stream.hasNext() && count < 5) {
                    Reading r = stream.next();
                    String line = String.format(
                            "[Stream] Room: %s | Temp: %.1fC | "
                                    + "Humidity: %.1f%% | Occupied: %s\n",
                            r.getRoomId(),
                            r.getTemperatureC(),
                            r.getHumidity(),
                            r.getOccupied());
                    SwingUtilities.invokeLater(
                            () -> sensorOutput.append(line));
                    count++;
                }
                SwingUtilities.invokeLater(() ->
                        sensorOutput.append(
                                "--- Stream complete ---\n"));
            } catch (StatusRuntimeException ex) {
                SwingUtilities.invokeLater(() ->
                        sensorOutput.append("ERROR: "
                                + ex.getStatus().getDescription()
                                + "\n"));
            }
        }).start();
    }

    private JPanel buildEnergyTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));

        String[] devices = {
                "LIGHTS_A201", "HEAT_A201", "AC_A201",
                "VENT_A201", "LIGHTS_B101", "HEAT_B101"
        };

        deviceCombo = new JComboBox<>(devices);
        actionCombo = new JComboBox<>(new String[]{"ON", "OFF"});

        JButton controlBtn = new JButton("Control Device (Unary)");
        JButton statusBtn = new JButton("Get Status (Unary)");
        JButton usageBtn = new JButton("Send Usage Samples (Client Stream)");

        controls.add(new JLabel("Device:"));
        controls.add(deviceCombo);
        controls.add(new JLabel("Action:"));
        controls.add(actionCombo);
        controls.add(controlBtn);
        controls.add(statusBtn);
        controls.add(usageBtn);

        energyOutput = new JTextArea();
        energyOutput.setEditable(false);
        energyOutput.setFont(
                new Font(Font.MONOSPACED, Font.PLAIN, 12));

        controlBtn.addActionListener(e -> controlDevice());
        statusBtn.addActionListener(e -> getDeviceStatus());
        usageBtn.addActionListener(e -> sendUsageSamples());

        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(energyOutput), BorderLayout.CENTER);
        return panel;
    }

    private void controlDevice() {
        String deviceId = (String) deviceCombo.getSelectedItem();
        String action = (String) actionCombo.getSelectedItem();
        try {
            CommandResult result = energyStub.controlDevice(
                    DeviceCommand.newBuilder()
                            .setDeviceId(deviceId)
                            .setAction(action)
                            .setRequestedBy("GUI")
                            .build());
            energyOutput.append("[Control] "
                    + result.getMessage() + "\n");
        } catch (StatusRuntimeException ex) {
            energyOutput.append("ERROR: "
                    + ex.getStatus().getDescription() + "\n");
        }
    }

    private void getDeviceStatus() {
        String deviceId = (String) deviceCombo.getSelectedItem();
        try {
            DeviceStatus status = energyStub.getDeviceStatus(
                    DeviceId.newBuilder()
                            .setDeviceId(deviceId)
                            .build());
            energyOutput.append(String.format(
                    "[Status] %s -> %s @ %.1fW\n",
                    status.getDeviceId(),
                    status.getState(),
                    status.getWatts()));
        } catch (StatusRuntimeException ex) {
            energyOutput.append("ERROR: "
                    + ex.getStatus().getDescription() + "\n");
        }
    }

    private void sendUsageSamples() {
        String deviceId = (String) deviceCombo.getSelectedItem();
        new Thread(() -> {
            try {
                CountDownLatch latch = new CountDownLatch(1);

                EnergyServiceGrpc.EnergyServiceStub asyncStub =
                        EnergyServiceGrpc.newStub(energyChannel);

                StreamObserver<UsageSummary> responseObserver =
                        new StreamObserver<UsageSummary>() {
                            @Override
                            public void onNext(UsageSummary s) {
                                String line = String.format(
                                        "[UsageSummary] Samples: %d | "
                                                + "Avg: %.1fW | Peak: %.1fW | "
                                                + "Total: %.4f kWh\n",
                                        s.getSamples(),
                                        s.getAvgWatts(),
                                        s.getPeakWatts(),
                                        s.getTotalKwh());
                                SwingUtilities.invokeLater(
                                        () -> energyOutput.append(line));
                            }
                            @Override
                            public void onError(Throwable t) {
                                SwingUtilities.invokeLater(() ->
                                        energyOutput.append(
                                                "ERROR: " + t.getMessage()
                                                        + "\n"));
                                latch.countDown();
                            }
                            @Override
                            public void onCompleted() {
                                latch.countDown();
                            }
                        };

                StreamObserver<UsageSample> requestObserver =
                        asyncStub.reportUsage(responseObserver);

                for (int i = 0; i < 5; i++) {
                    float watts = 200f
                            + (float)(Math.random() * 800f);
                    requestObserver.onNext(UsageSample.newBuilder()
                            .setDeviceId(deviceId)
                            .setWatts(watts)
                            .setTimestampMs(
                                    System.currentTimeMillis())
                            .build());
                    Thread.sleep(300);
                }
                requestObserver.onCompleted();
                latch.await(10, TimeUnit.SECONDS);

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        energyOutput.append("ERROR: "
                                + ex.getMessage() + "\n"));
            }
        }).start();
    }

    private JPanel buildAdvisorTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton solarBtn = new JButton("Solar Status (Unary)");
        advisorDeviceField = new JTextField("AC_A201", 8);
        wattsField = new JTextField("2000", 6);
        durationField = new JTextField("30", 4);

        JButton budgetBtn = new JButton("Request Budget (Unary)");
        JButton startMonitorBtn = new JButton("Start Monitor (Bidi)");
        JButton sendEventBtn = new JButton("Send Building Event");
        JButton stopMonitorBtn = new JButton("Stop Monitor");

        controls.add(solarBtn);
        controls.add(new JLabel("  Device:"));
        controls.add(advisorDeviceField);
        controls.add(new JLabel("Watts:"));
        controls.add(wattsField);
        controls.add(new JLabel("Mins:"));
        controls.add(durationField);
        controls.add(budgetBtn);
        controls.add(startMonitorBtn);
        controls.add(sendEventBtn);
        controls.add(stopMonitorBtn);

        advisorOutput = new JTextArea();
        advisorOutput.setEditable(false);
        advisorOutput.setFont(
                new Font(Font.MONOSPACED, Font.PLAIN, 12));

        solarBtn.addActionListener(e -> getSolarStatus());
        budgetBtn.addActionListener(e -> requestBudget());
        startMonitorBtn.addActionListener(e -> startMonitor());
        sendEventBtn.addActionListener(e -> sendBuildingEvent());
        stopMonitorBtn.addActionListener(e -> stopMonitor());

        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(advisorOutput),
                BorderLayout.CENTER);
        return panel;
    }

    private void getSolarStatus() {
        try {
            SolarStatus s = advisorStub.getSolarStatus(
                    Empty.getDefaultInstance());
            advisorOutput.append(String.format(
                    "[Solar] Generation: %.1fW | Battery: %.1f%%\n",
                    s.getSolarWatts(),
                    s.getBatteryPercent()));
        } catch (StatusRuntimeException ex) {
            advisorOutput.append("ERROR: "
                    + ex.getStatus().getDescription() + "\n");
        }
    }

    private void requestBudget() {
        try {
            float watts = Float.parseFloat(
                    wattsField.getText().trim());
            int mins = Integer.parseInt(
                    durationField.getText().trim());

            EnergyBudgetReply r = advisorStub.requestEnergyBudget(
                    EnergyBudgetRequest.newBuilder()
                            .setDeviceId(advisorDeviceField
                                    .getText().trim())
                            .setWattsNeeded(watts)
                            .setDurationMinutes(mins)
                            .build());

            advisorOutput.append(String.format(
                    "[Budget] Approved: %s | %s | "
                            + "Allocated: %.1fW\n",
                    r.getApproved(),
                    r.getReason(),
                    r.getAllocatedWatts()));

        } catch (StatusRuntimeException ex) {
            advisorOutput.append("ERROR: "
                    + ex.getStatus().getDescription() + "\n");
        }
    }

    private void startMonitor() {
        StreamObserver<ClimateAlert> alertObserver =
                new StreamObserver<ClimateAlert>() {
                    @Override
                    public void onNext(ClimateAlert alert) {
                        String line = String.format(
                                "[Alert][%s] %s -> %s\n",
                                alert.getSeverity(),
                                alert.getMessage(),
                                alert.getSuggestedAction());
                        SwingUtilities.invokeLater(
                                () -> advisorOutput.append(line));
                    }
                    @Override
                    public void onError(Throwable t) {
                        SwingUtilities.invokeLater(() ->
                                advisorOutput.append("ERROR: "
                                        + t.getMessage() + "\n"));
                    }
                    @Override
                    public void onCompleted() {
                        SwingUtilities.invokeLater(() ->
                                advisorOutput.append(
                                        "Monitor stream closed.\n"));
                    }
                };

        monitorRequestObserver =
                advisorAsyncStub.monitor(alertObserver);
        advisorOutput.append(
                "[Monitor] Bidirectional stream opened.\n");
    }

    private void sendBuildingEvent() {
        if (monitorRequestObserver == null) {
            advisorOutput.append("Start monitor first!\n");
            return;
        }
        String[] types = {
                "OCCUPANCY", "POWER_SPIKE", "TEMP_HIGH", "SOLAR_LOW"
        };
        String type = types[(int)(Math.random() * types.length)];

        BuildingEvent event = BuildingEvent.newBuilder()
                .setRoomId("A201")
                .setType(type)
                .setDetails("Simulated event from GUI")
                .setTimestampMs(System.currentTimeMillis())
                .build();

        monitorRequestObserver.onNext(event);
        advisorOutput.append("[Event Sent] Type: "
                + type + " | Room: A201\n");
    }

    private void stopMonitor() {
        if (monitorRequestObserver != null) {
            monitorRequestObserver.onCompleted();
            monitorRequestObserver = null;
            advisorOutput.append(
                    "[Monitor] Stream closed by client.\n");
        }
    }

    private void shutdown() {
        if (monitorRequestObserver != null) {
            monitorRequestObserver.onCompleted();
        }
        namingChannel.shutdown();
        sensorChannel.shutdown();
        energyChannel.shutdown();
        advisorChannel.shutdown();
        dispose();
        System.exit(0);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SmartBuildingGUI::new);
    }
}



