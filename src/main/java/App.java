
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class App {
    static List<Meeting> meetings = new ArrayList<>();
    static Map<String, Meeting> meetingMap = new LinkedHashMap<>();
    static final String OVERALL = "All Meetings (Overall)";

    public static void main(String[] args) throws Exception {
        System.out.println("StudTrack - Meeting Analytics Dashboard (Online & Offline)");
        System.out.println("==========================================================");

        // Load JSON data with offline support
        InputStream inputStream = App.class.getResourceAsStream("/meet_data_with_offline.json");
        if (inputStream == null) {
            System.out.println("❌ JSON file not found!");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(inputStream);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Parse meetings with support for both online and offline
        for (JsonNode meetingNode : root.get("meetings")) {
            String meetingId = meetingNode.get("meeting_id").asText();
            String title = meetingNode.get("title").asText();
            String date = meetingNode.get("date").asText();
            String type = meetingNode.has("type") ? meetingNode.get("type").asText() : "online";
            String location = meetingNode.has("location") ? meetingNode.get("location").asText() : "";
            
            List<Participant> participants = new ArrayList<>();
            
            if ("online".equals(type)) {
                // Parse online meeting participants
                for (JsonNode person : meetingNode.get("participants")) {
                    String name = person.get("name").asText();
                    List<Session> sessions = new ArrayList<>();
                    for (JsonNode session : person.get("sessions")) {
                        LocalDateTime join = LocalDateTime.parse(session.get("join").asText(), formatter);
                        LocalDateTime leave = LocalDateTime.parse(session.get("leave").asText(), formatter);
                        sessions.add(new Session(join, leave, Duration.between(join, leave).getSeconds()));
                    }
                    participants.add(new Participant(name, sessions, null));
                }
            } else {
                // Parse offline meeting participants
                for (JsonNode person : meetingNode.get("participants")) {
                    String name = person.get("name").asText();
                    JsonNode attendanceNode = person.get("attendance");
                    Attendance attendance = null;
                    
                    if (attendanceNode != null) {
                        String status = attendanceNode.get("status").asText();
                        LocalDateTime checkIn = null;
                        LocalDateTime checkOut = null;
                        
                        if (attendanceNode.has("check_in") && !attendanceNode.get("check_in").isNull()) {
                            checkIn = LocalDateTime.parse(attendanceNode.get("check_in").asText(), formatter);
                        }
                        if (attendanceNode.has("check_out") && !attendanceNode.get("check_out").isNull()) {
                            checkOut = LocalDateTime.parse(attendanceNode.get("check_out").asText(), formatter);
                        }
                        
                        int lateByMinutes = attendanceNode.has("late_by_minutes") ? attendanceNode.get("late_by_minutes").asInt() : 0;
                        int earlyLeaveMinutes = attendanceNode.has("early_leave_minutes") ? attendanceNode.get("early_leave_minutes").asInt() : 0;
                        
                        attendance = new Attendance(status, checkIn, checkOut, lateByMinutes, earlyLeaveMinutes);
                    }
                    
                    participants.add(new Participant(name, new ArrayList<>(), attendance));
                }
            }

            List<Chat> chats = new ArrayList<>();
            if (meetingNode.has("chats")) {
                for (JsonNode chatNode : meetingNode.get("chats")) {
                    chats.add(new Chat(
                        LocalDateTime.parse(chatNode.get("timestamp").asText(), formatter),
                        chatNode.get("sender").asText(),
                        chatNode.get("message").asText()
                    ));
                }
            }
            
            List<Activity> activities = new ArrayList<>();
            if (meetingNode.has("activities")) {
                for (JsonNode activityNode : meetingNode.get("activities")) {
                    activities.add(new Activity(
                        LocalDateTime.parse(activityNode.get("timestamp").asText(), formatter),
                        activityNode.get("participant").asText(),
                        activityNode.get("activity").asText()
                    ));
                }
            }
            
            Meeting meeting = new Meeting(meetingId, title, date, type, location, participants, chats, activities);
            meetings.add(meeting);
            meetingMap.put(meetingId + " - " + title + " (" + date + ") [" + type + "]", meeting);
        }

        // Print meeting summaries
        for (Meeting m : meetings) {
            System.out.println(m.title + " (" + m.date + ") - " + m.type.toUpperCase() + 
                             (m.location.isEmpty() ? "" : " at " + m.location));
            
            if ("online".equals(m.type)) {
                for (Participant p : m.participants) {
                    long totalSeconds = p.sessions.stream().mapToLong(s -> s.durationSeconds).sum();
                    System.out.printf("  %s was in the meet for: %d minutes and %d seconds.%n",
                        p.name, totalSeconds / 60, totalSeconds % 60);
                }
                System.out.println("  Chat messages: " + m.chats.size());
            } else {
                for (Participant p : m.participants) {
                    if (p.attendance != null) {
                        System.out.printf("  %s: %s", p.name, p.attendance.status);
                        if ("present".equals(p.attendance.status) || "late".equals(p.attendance.status)) {
                            if (p.attendance.lateByMinutes > 0) {
                                System.out.printf(" (late by %d minutes)", p.attendance.lateByMinutes);
                            }
                            if (p.attendance.earlyLeaveMinutes > 0) {
                                System.out.printf(" (left %d minutes early)", p.attendance.earlyLeaveMinutes);
                            }
                        }
                        System.out.println();
                    }
                }
                System.out.println("  Activities recorded: " + m.activities.size());
            }
        }

        // Enhanced Chat Pattern Analysis for online meetings
        System.out.println("\n=== Online Meeting Chat Pattern Analysis ===");
        for (Meeting m : meetings) {
            if ("online".equals(m.type) && !m.chats.isEmpty()) {
                System.out.println("\n" + m.title + " (" + m.date + ")");
                
                Map<String, List<Chat>> chatsByParticipant = m.chats.stream()
                    .collect(Collectors.groupingBy(c -> c.sender));
                
                List<ParticipantScore> scores = new ArrayList<>();
                
                chatsByParticipant.forEach((participant, chats) -> {
                    chats.sort(Comparator.comparing(c -> c.timestamp));
                    
                    boolean isSpam = detectSpamPattern(chats, 2, 1);
                    double spamScore = calculateSpamScore(chats);
                    
                    scores.add(new ParticipantScore(participant, chats.size(), isSpam, spamScore));
                });
                
                System.out.println("--------------------------------------------------");
                System.out.printf("%-15s %-15s %-20s %-15s%n",
                    "Participant", "Messages", "Pattern", "Spam Score");
                System.out.println("--------------------------------------------------");
                
                for (ParticipantScore ps : scores) {
                    System.out.printf("%-15s %-15d %-20s %-15.1f%n",
                        ps.participant,
                        ps.messageCount,
                        ps.isSpam ? "SPAM DETECTED" : "Normal",
                        ps.score
                    );
                }
                
                System.out.println("\nMeeting Highlights:");
                System.out.println("- Most active: " + 
                    scores.stream()
                        .max(Comparator.comparingInt(ps -> ps.messageCount))
                        .map(ps -> ps.participant)
                        .orElse("None"));
                System.out.println("- Total messages: " + m.chats.size());
            }
        }

        // Offline Meeting Activity Analysis
        System.out.println("\n=== Offline Meeting Activity Analysis ===");
        for (Meeting m : meetings) {
            if ("offline".equals(m.type) && !m.activities.isEmpty()) {
                System.out.println("\n" + m.title + " (" + m.date + ") at " + m.location);
                
                Map<String, List<Activity>> activitiesByParticipant = m.activities.stream()
                    .collect(Collectors.groupingBy(a -> a.participant));
                
                System.out.println("--------------------------------------------------");
                System.out.printf("%-15s %-15s %-20s%n",
                    "Participant", "Activities", "Most Common Activity");
                System.out.println("--------------------------------------------------");
                
                for (Map.Entry<String, List<Activity>> entry : activitiesByParticipant.entrySet()) {
                    String participant = entry.getKey();
                    List<Activity> activities = entry.getValue();
                    
                    String mostCommonActivity = activities.stream()
                        .collect(Collectors.groupingBy(a -> a.activity, Collectors.counting()))
                        .entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("None");
                    
                    System.out.printf("%-15s %-15d %-20s%n",
                        participant, activities.size(), mostCommonActivity);
                }
                
                System.out.println("\nAttendance Summary:");
                long presentCount = m.participants.stream()
                    .filter(p -> p.attendance != null && "present".equals(p.attendance.status))
                    .count();
                long lateCount = m.participants.stream()
                    .filter(p -> p.attendance != null && "late".equals(p.attendance.status))
                    .count();
                long absentCount = m.participants.stream()
                    .filter(p -> p.attendance != null && "absent".equals(p.attendance.status))
                    .count();
                
                System.out.printf("- Present: %d, Late: %d, Absent: %d%n", presentCount, lateCount, absentCount);
                System.out.printf("- Attendance Rate: %.1f%%%n", 
                    (double)(presentCount + lateCount) / m.participants.size() * 100);
            }
        }

        // Generate comprehensive report
        ReportGenerator.generateComprehensiveReport(meetings, "StudTrack_Report.txt");
        
        // Create dashboard with slight delay
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {}
        SwingUtilities.invokeLater(() -> createDashboard());
    }

    // Helper class to store results before printing
    static class ParticipantScore {
        String participant;
        int messageCount;
        boolean isSpam;
        double score;

        public ParticipantScore(String participant, int messageCount, boolean isSpam, double score) {
            this.participant = participant;
            this.messageCount = messageCount;
            this.isSpam = isSpam;
            this.score = score;
        }
    }

    private static boolean detectSpamPattern(List<Chat> chats, int threshold, int minutes) {
        if (chats.size() < 2) return false;
        chats.sort(Comparator.comparing(c -> c.timestamp));

        double spamScore = calculateSpamScore(chats);
        
        if (spamScore >= 15) {
            return true;
        }
        if (spamScore < 7) return false; 
        
        if (chats.size() >= threshold) {
            for (int i = 0; i <= chats.size() - threshold; i++) {
                Duration delta = Duration.between(
                    chats.get(i).timestamp,
                    chats.get(i + threshold - 1).timestamp
                );
                if (delta.toSeconds() <= minutes * 60) {
                    return true;
                }
            }
        }

        int consecutiveDuplicates = 0;
        for (int i = 0; i < chats.size() - 1; i++) {
            if (chats.get(i).message.equals(chats.get(i + 1).message)) {
                consecutiveDuplicates++;
                if (consecutiveDuplicates >= 2) {
                    return true;
                }
            } else {
                consecutiveDuplicates = 0;
            }
        }
        
        return false;
    }

    private static double calculateSpamScore(List<Chat> chats) {
        if (chats.size() < 2) return 10.0;
        
        double minutes = Duration.between(
            chats.get(0).timestamp,
            chats.get(chats.size()-1).timestamp
        ).toMinutes();
        minutes = Math.max(1, minutes);
        double density = chats.size() / minutes;
        
        double spamPoints = 0;
        for (int i = 0; i < chats.size() - 1; i++) {
            Duration delta = Duration.between(chats.get(i).timestamp, chats.get(i + 1).timestamp);
            
            if (delta.toSeconds() <= 30) {
                spamPoints += 5;
                if (chats.get(i).message.equals(chats.get(i + 1).message)) {
                    spamPoints += 10;
                }
            }
        }
        
        return Math.min(100, spamPoints + (density * 2));
    }

    private static String getMostActiveParticipant(Map<String, List<Chat>> chatsByParticipant) {
        return chatsByParticipant.entrySet().stream()
            .max(Comparator.comparingInt(entry -> entry.getValue().size()))
            .map(Map.Entry::getKey)
            .orElse("None");
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

        // Chart panels
        JPanel barChartPanel = new JPanel();
        JPanel pieChartPanel = new JPanel();
        JPanel timelineChartPanel = new JPanel();
        JPanel heatmapPanel = new JPanel();
        JPanel chatChartPanel = new JPanel();
        frame.add(barChartPanel);
        frame.add(pieChartPanel);
        frame.add(timelineChartPanel);
        frame.add(heatmapPanel);
        frame.add(chatChartPanel);

        // Chart update logic
        ActionListener updateCharts = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selected = (String) meetingSelector.getSelectedItem();
                Map<String, Long> participantTotalTime;
                Map<String, List<Session>> participantSessions;
                Map<String, Long> participantChatCounts;
                Map<String, List<Chat>> participantChats;

                if (selected.equals(OVERALL)) {
                    participantTotalTime = getOverallTotalTime();
                    participantSessions = getOverallSessions();
                    participantChatCounts = getOverallChatCounts();
                    participantChats = getOverallChats();
                } else {
                    Meeting m = meetingMap.get(selected);
                    participantTotalTime = getMeetingTotalTime(m);
                    participantSessions = getMeetingSessions(m);
                    participantChatCounts = getMeetingChatCounts(m);
                    participantChats = getMeetingChats(m);
                }

                frame.getContentPane().removeAll();
                frame.add(selectorPanel);
                frame.add(createBarChart(participantTotalTime));
                frame.add(createPieChart(participantTotalTime));
                frame.add(createTimelineChart(participantSessions));
                frame.add(createHeatmapChart(participantSessions));
                
                // Only show chat-related charts for online meetings or overall view with chat data
                if (selected.equals(OVERALL)) {
                    // For overall view, only show chat charts if there are online meetings with chat data
                    if (!participantChatCounts.isEmpty()) {
                        frame.add(createChatChart(participantChatCounts));
                        frame.add(createChatIntervalChart(participantChats));
                    } else {
                        // If no chat data, show empty panels or alternative charts
                        frame.add(new JPanel());
                        frame.add(new JPanel());
                    }
                } else if (meetingMap.containsKey(selected) && "online".equals(meetingMap.get(selected).type)) {
                    frame.add(createChatChart(participantChatCounts));
                    frame.add(createChatIntervalChart(participantChats));
                } else {
                    // For offline meetings, show attendance and activity charts instead
                    Meeting m = meetingMap.get(selected);
                    if ("offline".equals(m.type)) {
                        frame.add(createAttendanceChart(m));
                        frame.add(createActivityChart(m));
                    }
                }
                
                frame.revalidate();
                frame.repaint();
            }
        };
        meetingSelector.addActionListener(updateCharts);
        meetingSelector.setSelectedIndex(0);
        updateCharts.actionPerformed(null);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        System.out.println("Dashboard opened! Use the dropdown to select a meeting or view overall trends.");
    }

    private static Map<String, List<Chat>> getMeetingChats(Meeting m) {
        return m.chats.stream().collect(Collectors.groupingBy(c -> c.sender));
    }

    private static Map<String, Long> getMeetingTotalTime(Meeting m) {
        Map<String, Long> result = new HashMap<>();
        for (Participant p : m.participants) {
            if ("online".equals(m.type)) {
                long totalSeconds = p.sessions.stream().mapToLong(s -> s.durationSeconds).sum();
                result.put(p.name, totalSeconds);
            } else {
                if (p.attendance != null && p.attendance.checkIn != null && p.attendance.checkOut != null) {
                    long totalSeconds = Duration.between(p.attendance.checkIn, p.attendance.checkOut).getSeconds();
                    result.put(p.name, totalSeconds);
                }
            }
        }
        return result;
    }

    private static Map<String, List<Session>> getMeetingSessions(Meeting m) {
        Map<String, List<Session>> result = new HashMap<>();
        for (Participant p : m.participants) {
            if ("online".equals(m.type)) {
                result.put(p.name, p.sessions);
            } else {
                // Convert offline attendance to session format for visualization
                if (p.attendance != null && p.attendance.checkIn != null && p.attendance.checkOut != null) {
                    List<Session> sessions = new ArrayList<>();
                    sessions.add(new Session(p.attendance.checkIn, p.attendance.checkOut, 
                        Duration.between(p.attendance.checkIn, p.attendance.checkOut).getSeconds()));
                    result.put(p.name, sessions);
                }
            }
        }
        return result;
    }

    private static Map<String, Long> getMeetingChatCounts(Meeting m) {
        return m.chats.stream().collect(Collectors.groupingBy(c -> c.sender, Collectors.counting()));
    }

    private static Map<String, Long> getOverallTotalTime() {
        Map<String, Long> result = new HashMap<>();
        for (Meeting m : meetings) {
            Map<String, Long> meetingTimes = getMeetingTotalTime(m);
            for (Map.Entry<String, Long> entry : meetingTimes.entrySet()) {
                result.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
        }
        return result;
    }

    private static Map<String, List<Session>> getOverallSessions() {
        Map<String, List<Session>> result = new HashMap<>();
        for (Meeting m : meetings) {
            Map<String, List<Session>> meetingSessions = getMeetingSessions(m);
            for (Map.Entry<String, List<Session>> entry : meetingSessions.entrySet()) {
                result.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
            }
        }
        return result;
    }

    private static Map<String, Long> getOverallChatCounts() {
        Map<String, Long> result = new HashMap<>();
        for (Meeting m : meetings) {
            if ("online".equals(m.type)) {
                Map<String, Long> meetingChats = getMeetingChatCounts(m);
                for (Map.Entry<String, Long> entry : meetingChats.entrySet()) {
                    result.merge(entry.getKey(), entry.getValue(), Long::sum);
                }
            }
        }
        return result;
    }

    private static Map<String, List<Chat>> getOverallChats() {
        Map<String, List<Chat>> result = new HashMap<>();
        for (Meeting m : meetings) {
            if ("online".equals(m.type)) {
                Map<String, List<Chat>> meetingChats = getMeetingChats(m);
                for (Map.Entry<String, List<Chat>> entry : meetingChats.entrySet()) {
                    result.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
                }
            }
        }
        return result;
    }

    private static JPanel createPieChart(Map<String, Long> participantTotalTime) {
        org.jfree.data.general.DefaultPieDataset dataset = new org.jfree.data.general.DefaultPieDataset();
        participantTotalTime.forEach((name, seconds) ->
            dataset.setValue(name, seconds / 60.0));
        JFreeChart chart = ChartFactory.createPieChart(
            "Meeting Time Distribution", dataset, true, true, false);
        chart.setBackgroundPaint(new Color(255, 250, 240));
        return new ChartPanel(chart) {{
            setPreferredSize(new Dimension(600, 400));
        }};
    }

    private static JPanel createHeatmapChart(Map<String, List<Session>> participantSessions) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        for (Map.Entry<String, List<Session>> entry : participantSessions.entrySet()) {
            for (Session session : entry.getValue()) {
                String timeSlot = session.join.getHour() + ":00";
                dataset.addValue(1, entry.getKey(), timeSlot);
            }
        }
        JFreeChart chart = ChartFactory.createStackedBarChart(
            "Attendance Heatmap (by Hour)", "Hour", "Sessions",
            dataset, PlotOrientation.VERTICAL, true, true, false);
        chart.setBackgroundPaint(new Color(255, 240, 245));
        return new ChartPanel(chart) {{
            setPreferredSize(new Dimension(600, 400));
        }};
    }

    private static JPanel createChatChart(Map<String, Long> participantChatCounts) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        participantChatCounts.forEach((name, count) ->
            dataset.addValue(count, "Chat Messages", name));
        JFreeChart chart = ChartFactory.createBarChart(
            "Chat Messages by Participant", "Participants", "Messages",
            dataset, PlotOrientation.VERTICAL, true, true, false);
        chart.setBackgroundPaint(new Color(240, 255, 255));
        return new ChartPanel(chart) {{
            setPreferredSize(new Dimension(600, 400));
        }};
    }

    private static JPanel createBarChart(Map<String, Long> participantTotalTime) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        participantTotalTime.forEach((name, seconds) -> 
            dataset.addValue(seconds / 60.0, "Meeting Time (minutes)", name));
        JFreeChart chart = ChartFactory.createBarChart(
            "Total Meeting Time by Participant", "Participants", "Time (minutes)",
            dataset, PlotOrientation.VERTICAL, true, true, false);
        chart.setBackgroundPaint(new Color(240, 248, 255));
        return new ChartPanel(chart) {{
            setPreferredSize(new Dimension(600, 400));
        }};
    }



    private static JPanel createAttendanceChart(Meeting m) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        Map<String, Long> statusCounts = new HashMap<>();
        for (Participant p : m.participants) {
            if (p.attendance != null) {
                statusCounts.merge(p.attendance.status, 1L, Long::sum);
            }
        }
        
        for (Map.Entry<String, Long> entry : statusCounts.entrySet()) {
            dataset.addValue(entry.getValue(), "Count", entry.getKey().toUpperCase());
        }

        JFreeChart chart = ChartFactory.createBarChart(
            "Attendance Status - " + m.title, "Status", "Count",
            dataset, PlotOrientation.VERTICAL, true, true, false);
        chart.setBackgroundPaint(new Color(255, 250, 240));
        return new ChartPanel(chart) {{
            setPreferredSize(new Dimension(600, 400));
        }};
    }

    private static JPanel createActivityChart(Meeting m) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        Map<String, Long> activityCounts = m.activities.stream()
            .collect(Collectors.groupingBy(a -> a.activity, Collectors.counting()));
        
        for (Map.Entry<String, Long> entry : activityCounts.entrySet()) {
            dataset.addValue(entry.getValue(), "Count", entry.getKey());
        }

        JFreeChart chart = ChartFactory.createBarChart(
            "Activity Distribution - " + m.title, "Activity", "Count",
            dataset, PlotOrientation.VERTICAL, true, true, false);
        chart.setBackgroundPaint(new Color(240, 255, 255));
        return new ChartPanel(chart) {{
            setPreferredSize(new Dimension(600, 400));
        }};
    }

    private static JPanel createTimelineChart(Map<String, List<Session>> participantSessions) {
        XYSeriesCollection dataset = new XYSeriesCollection();
        int idx = 1;
        for (Map.Entry<String, List<Session>> entry : participantSessions.entrySet()) {
            XYSeries series = new XYSeries(entry.getKey());
            for (Session session : entry.getValue()) {
                long start = session.join.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
                long end = session.leave.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
                series.add(start, idx);
                series.add(end, idx);
            }
            dataset.addSeries(series);
            idx++;
        }
        JFreeChart chart = ChartFactory.createXYLineChart(
            "Participant Timeline", "Time (epoch seconds)", "Participant Index",
            dataset, PlotOrientation.HORIZONTAL, true, true, false);
        chart.setBackgroundPaint(new Color(245, 255, 250));
        return new ChartPanel(chart) {{
            setPreferredSize(new Dimension(600, 400));
        }};
    }

    private static JPanel createChatIntervalChart(Map<String, List<Chat>> participantChats) {
    DefaultCategoryDataset dataset = new DefaultCategoryDataset();
    
    participantChats.forEach((participant, chats) -> {
        if (!chats.isEmpty()) {
            chats.sort(Comparator.comparing(c -> c.timestamp));
            
            // Calculate raw metrics
            double duration = Math.max(1, 
                Duration.between(
                    chats.get(0).timestamp,
                    chats.get(chats.size()-1).timestamp
                ).toMinutes());
            
            double totalChats = chats.size();
            double ratePer10Min = (chats.size() / duration) * 10;
            double spamScore = calculateSpamScore(chats);
            
            // Add raw values to dataset
            dataset.addValue(totalChats, "Total Messages", participant);
            dataset.addValue(ratePer10Min, "Rate (per 10min)", participant);
            dataset.addValue(spamScore, "Spam Score (0-100)", participant);
        }
    });

    JFreeChart chart = ChartFactory.createBarChart(
        "Chat Participation Metrics", 
        "Participants", 
        "Values", 
        dataset, 
        PlotOrientation.VERTICAL, 
        true, 
        true, 
        false
    );

    // Basic styling
    chart.setBackgroundPaint(Color.WHITE);
    CategoryPlot plot = chart.getCategoryPlot();
    plot.setBackgroundPaint(Color.WHITE);
    
    return new ChartPanel(chart) {{
        setPreferredSize(new Dimension(600, 400));
    }};
}

    static class Meeting {
        String meetingId, title, date, type, location;
        List<Participant> participants;
        List<Chat> chats;
        List<Activity> activities;
        
        Meeting(String meetingId, String title, String date, String type, String location,
               List<Participant> participants, List<Chat> chats, List<Activity> activities) {
            this.meetingId = meetingId;
            this.title = title;
            this.date = date;
            this.type = type;
            this.location = location;
            this.participants = participants;
            this.chats = chats;
            this.activities = activities;
        }
    }

    static class Participant {
        String name;
        List<Session> sessions;
        Attendance attendance;
        
        Participant(String name, List<Session> sessions, Attendance attendance) {
            this.name = name;
            this.sessions = sessions;
            this.attendance = attendance;
        }
    }

    static class Session {
        LocalDateTime join, leave;
        long durationSeconds;
        
        Session(LocalDateTime join, LocalDateTime leave, long durationSeconds) {
            this.join = join;
            this.leave = leave;
            this.durationSeconds = durationSeconds;
        }
    }

    static class Attendance {
        String status;
        LocalDateTime checkIn, checkOut;
        int lateByMinutes, earlyLeaveMinutes;
        
        Attendance(String status, LocalDateTime checkIn, LocalDateTime checkOut, 
                  int lateByMinutes, int earlyLeaveMinutes) {
            this.status = status;
            this.checkIn = checkIn;
            this.checkOut = checkOut;
            this.lateByMinutes = lateByMinutes;
            this.earlyLeaveMinutes = earlyLeaveMinutes;
        }
    }

    static class Chat {
        LocalDateTime timestamp;
        String sender, message;
        
        Chat(LocalDateTime timestamp, String sender, String message) {
            this.timestamp = timestamp;
            this.sender = sender;
            this.message = message;
        }
    }

    static class Activity {
        LocalDateTime timestamp;
        String participant, activity;
        
        Activity(LocalDateTime timestamp, String participant, String activity) {
            this.timestamp = timestamp;
            this.participant = participant;
            this.activity = activity;
        }
    }
}