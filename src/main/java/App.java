// File: /StudTrack/src/main/java/App.java
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.JComboBox;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class App {
    static List<Meeting> meetings = new ArrayList<>();
    static Map<String, Meeting> meetingMap = new LinkedHashMap<>(); // meeting_id -> Meeting
    static final String OVERALL = "All Meetings (Overall)";

    public static void main(String[] args) throws Exception {
        System.out.println("StudTrack - Meeting Analytics Dashboard (Multi-Meeting)");
        System.out.println("========================================================");

        // Load JSON file from resources
        InputStream inputStream = App.class.getResourceAsStream("/meet_data_multi.json");
        if (inputStream == null) {
            System.out.println("❌ JSON file not found!");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(inputStream);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Parse all meetings
        for (JsonNode meetingNode : root.get("meetings")) {
            String meetingId = meetingNode.get("meeting_id").asText();
            String title = meetingNode.get("title").asText();
            String date = meetingNode.get("date").asText();
            List<Participant> participants = new ArrayList<>();
            for (JsonNode person : meetingNode.get("participants")) {
                String name = person.get("name").asText();
                List<Session> sessions = new ArrayList<>();
                for (JsonNode session : person.get("sessions")) {
                    LocalDateTime join = LocalDateTime.parse(session.get("join").asText(), formatter);
                    LocalDateTime leave = LocalDateTime.parse(session.get("leave").asText(), formatter);
                    long sessionSeconds = Duration.between(join, leave).getSeconds();
                    sessions.add(new Session(join, leave, sessionSeconds));
                }
                participants.add(new Participant(name, sessions));
            }

            List<Chat> chats = new ArrayList<>();
            if (meetingNode.has("chats")) {
                for (JsonNode chatNode : meetingNode.get("chats")) {
                    LocalDateTime timestamp = LocalDateTime.parse(chatNode.get("timestamp").asText(), formatter);
                    String sender = chatNode.get("sender").asText();
                    String message = chatNode.get("message").asText();
                    chats.add(new Chat(timestamp, sender, message));
                }
            }
            
            Meeting meeting = new Meeting(meetingId, title, date, participants, chats);
            meetings.add(meeting);
            meetingMap.put(meetingId + " - " + title + " (" + date + ")", meeting);
        }

        // Print summary
        for (Meeting m : meetings) {
            System.out.println(m.title + " (" + m.date + ")");
            for (Participant p : m.participants) {
                long totalSeconds = p.sessions.stream().mapToLong(s -> s.durationSeconds).sum();
                long minutes = totalSeconds / 60;
                long seconds = totalSeconds % 60;
                System.out.println("  " + p.name + " was in the meet for: " + minutes + " minutes and " + seconds + " seconds.");
            }
            System.out.println("  Chat messages: " + m.chats.size());
        }

        // Create and display dashboard
        SwingUtilities.invokeLater(() -> {
            createDashboard();
        });
    }

    private static void createDashboard() {
        JFrame frame = new JFrame("StudTrack - Meeting Analytics Dashboard");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1400, 950);
        frame.setLayout(new GridLayout(3, 2, 10, 10));

        // Dropdown for meeting selection
        List<String> meetingOptions = new ArrayList<>();
        meetingOptions.add(OVERALL);
        meetingOptions.addAll(meetingMap.keySet());
        JComboBox<String> meetingSelector = new JComboBox<>(meetingOptions.toArray(new String[0]));
        meetingSelector.setFont(new Font("Arial", Font.BOLD, 16));
        JPanel selectorPanel = new JPanel();
        selectorPanel.add(meetingSelector);
        frame.add(selectorPanel);

        // Chart panels (placeholders, will be replaced on selection)
        JPanel barChartPanel = new JPanel();
        JPanel pieChartPanel = new JPanel();
        JPanel timelineChartPanel = new JPanel();
        JPanel heatmapPanel = new JPanel();
        JPanel chatChartPanel = new JPanel(); // New panel for chat chart

        frame.add(barChartPanel);
        frame.add(pieChartPanel);
        frame.add(timelineChartPanel);
        frame.add(heatmapPanel);
        frame.add(chatChartPanel); // Add new panel

        // Chart update logic
        ActionListener updateCharts = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String selected = (String) meetingSelector.getSelectedItem();
                Map<String, Long> participantTotalTime;
                Map<String, List<Session>> participantSessions;
                Map<String, Long> participantChatCounts; // New data for chat counts

                if (selected.equals(OVERALL)) {
                    participantTotalTime = getOverallTotalTime();
                    participantSessions = getOverallSessions();
                    participantChatCounts = getOverallChatCounts(); // Get overall chat counts
                } else {
                    Meeting m = meetingMap.get(selected);
                    participantTotalTime = getMeetingTotalTime(m);
                    participantSessions = getMeetingSessions(m);
                    participantChatCounts = getMeetingChatCounts(m); // Get meeting-specific chat counts
                }
                // Remove all components except the selectorPanel
                frame.getContentPane().removeAll();
                frame.add(selectorPanel);
                // Add new charts
                JPanel newBar = createBarChart(participantTotalTime);
                JPanel newPie = createPieChart(participantTotalTime);
                JPanel newTimeline = createTimelineChart(participantSessions);
                JPanel newHeatmap = createHeatmapChart(participantSessions);
                JPanel newChatChart = createChatChart(participantChatCounts); // Create new chat chart
                frame.add(newBar);
                frame.add(newPie);
                frame.add(newTimeline);
                frame.add(newHeatmap);
                frame.add(newChatChart); // Add new chat chart
                frame.revalidate();
                frame.repaint();
            }
        };
        meetingSelector.addActionListener(updateCharts);
        // Initial chart display
        meetingSelector.setSelectedIndex(0);
        updateCharts.actionPerformed(null);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        System.out.println("Dashboard opened! Use the dropdown to select a meeting or view overall trends.");
    }

    // --- Data aggregation helpers ---
    private static Map<String, Long> getMeetingTotalTime(Meeting m) {
        Map<String, Long> map = new HashMap<>();
        for (Participant p : m.participants) {
            long total = p.sessions.stream().mapToLong(s -> s.durationSeconds).sum();
            map.put(p.name, total);
        }
        return map;
    }
    private static Map<String, List<Session>> getMeetingSessions(Meeting m) {
        Map<String, List<Session>> map = new HashMap<>();
        for (Participant p : m.participants) {
            map.put(p.name, p.sessions);
        }
        return map;
    }
    private static Map<String, Long> getMeetingChatCounts(Meeting m) {
        Map<String, Long> map = new HashMap<>();
        for (Chat chat : m.chats) {
            map.put(chat.sender, map.getOrDefault(chat.sender, 0L) + 1);
        }
        return map;
    }

    private static Map<String, Long> getOverallTotalTime() {
        Map<String, Long> map = new HashMap<>();
        for (Meeting m : meetings) {
            for (Participant p : m.participants) {
                map.put(p.name, map.getOrDefault(p.name, 0L) + p.sessions.stream().mapToLong(s -> s.durationSeconds).sum());
            }
        }
        return map;
    }
    private static Map<String, List<Session>> getOverallSessions() {
        Map<String, List<Session>> map = new HashMap<>();
        for (Meeting m : meetings) {
            for (Participant p : m.participants) {
                map.computeIfAbsent(p.name, k -> new ArrayList<>()).addAll(p.sessions);
            }
        }
        return map;
    }
    private static Map<String, Long> getOverallChatCounts() {
        Map<String, Long> map = new HashMap<>();
        for (Meeting m : meetings) {
            for (Chat chat : m.chats) {
                map.put(chat.sender, map.getOrDefault(chat.sender, 0L) + 1);
            }
        }
        return map;
    }

    // --- Chart creation (same as before) ---
    private static JPanel createBarChart(Map<String, Long> participantTotalTime) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Map.Entry<String, Long> entry : participantTotalTime.entrySet()) {
            dataset.addValue(entry.getValue() / 60.0, "Meeting Time (minutes)", entry.getKey());
        }
        JFreeChart chart = ChartFactory.createBarChart(
            "Total Meeting Time by Participant",
            "Participants",
            "Time (minutes)",
            dataset,
            PlotOrientation.VERTICAL,
            true, true, false
        );
        chart.setBackgroundPaint(new Color(240, 248, 255));
        chart.getTitle().setPaint(new Color(25, 25, 112));
        chart.getTitle().setFont(new Font("Arial", Font.BOLD, 16));
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(600, 400));
        return chartPanel;
    }
    private static JPanel createPieChart(Map<String, Long> participantTotalTime) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Map.Entry<String, Long> entry : participantTotalTime.entrySet()) {
            dataset.addValue(entry.getValue() / 60.0, "Meeting Time", entry.getKey());
        }
        JFreeChart chart = ChartFactory.createBarChart(
            "Meeting Participation Distribution",
            "Participants",
            "Time (minutes)",
            dataset,
            PlotOrientation.HORIZONTAL,
            true, true, false
        );
        chart.setBackgroundPaint(new Color(255, 248, 220));
        chart.getTitle().setPaint(new Color(139, 69, 19));
        chart.getTitle().setFont(new Font("Arial", Font.BOLD, 16));
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(600, 400));
        return chartPanel;
    }
    private static JPanel createTimelineChart(Map<String, List<Session>> participantSessions) {
        XYSeriesCollection dataset = new XYSeriesCollection();
        for (Map.Entry<String, List<Session>> entry : participantSessions.entrySet()) {
            XYSeries series = new XYSeries(entry.getKey());
            for (Session session : entry.getValue()) {
                double startHour = session.join.getHour() + session.join.getMinute() / 60.0;
                double duration = session.durationSeconds / 3600.0;
                series.add(startHour, duration);
            }
            dataset.addSeries(series);
        }
        JFreeChart chart = ChartFactory.createXYLineChart(
            "Meeting Timeline Analysis",
            "Time of Day (hours)",
            "Session Duration (hours)",
            dataset,
            PlotOrientation.VERTICAL,
            true, true, false
        );
        chart.setBackgroundPaint(new Color(240, 255, 240));
        chart.getTitle().setPaint(new Color(34, 139, 34));
        chart.getTitle().setFont(new Font("Arial", Font.BOLD, 16));
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(600, 400));
        return chartPanel;
    }
    private static JPanel createHeatmapChart(Map<String, List<Session>> participantSessions) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (int hour = 8; hour <= 16; hour++) {
            for (Map.Entry<String, List<Session>> entry : participantSessions.entrySet()) {
                double participationInHour = 0;
                for (Session session : entry.getValue()) {
                    int sessionStartHour = session.join.getHour();
                    int sessionEndHour = session.leave.getHour();
                    if (hour >= sessionStartHour && hour <= sessionEndHour) {
                        participationInHour += 1.0;
                    }
                }
                if (participationInHour > 0) {
                    dataset.addValue(participationInHour, entry.getKey(), hour + ":00");
                }
            }
        }
        JFreeChart chart = ChartFactory.createBarChart(
            "Meeting Activity Heatmap",
            "Time Slots",
            "Participants",
            dataset,
            PlotOrientation.HORIZONTAL,
            true, true, false
        );
        chart.setBackgroundPaint(new Color(255, 240, 245));
        chart.getTitle().setPaint(new Color(220, 20, 60));
        chart.getTitle().setFont(new Font("Arial", Font.BOLD, 16));
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(600, 400));
        return chartPanel;
    }

    // --- New Chart for Chat Activity ---
    private static JPanel createChatChart(Map<String, Long> participantChatCounts) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Map.Entry<String, Long> entry : participantChatCounts.entrySet()) {
            dataset.addValue(entry.getValue(), "Chat Messages", entry.getKey());
        }
        JFreeChart chart = ChartFactory.createBarChart(
            "Chat Messages by Participant",
            "Participants",
            "Number of Messages",
            dataset,
            PlotOrientation.VERTICAL,
            true, true, false
        );
        chart.setBackgroundPaint(new Color(230, 240, 250));
        chart.getTitle().setPaint(new Color(0, 102, 204));
        chart.getTitle().setFont(new Font("Arial", Font.BOLD, 16));
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(600, 400));
        return chartPanel;
    }

    // --- Data classes ---
    static class Meeting {
        String meetingId, title, date;
        List<Participant> participants;
        List<Chat> chats; // New field for chats

        Meeting(String meetingId, String title, String date, List<Participant> participants, List<Chat> chats) {
            this.meetingId = meetingId;
            this.title = title;
            this.date = date;
            this.participants = participants;
            this.chats = chats;
        }
    }
    static class Participant {
        String name;
        List<Session> sessions;
        Participant(String name, List<Session> sessions) {
            this.name = name;
            this.sessions = sessions;
        }
    }
    static class Session {
        LocalDateTime join;
        LocalDateTime leave;
        long durationSeconds;
        Session(LocalDateTime join, LocalDateTime leave, long durationSeconds) {
            this.join = join;
            this.leave = leave;
            this.durationSeconds = durationSeconds;
        }
    }

    // --- New Chat Data Class ---
    static class Chat {
        LocalDateTime timestamp;
        String sender;
        String message;

        Chat(LocalDateTime timestamp, String sender, String message) {
            this.timestamp = timestamp;
            this.sender = sender;
            this.message = message;
        }
    }
}
