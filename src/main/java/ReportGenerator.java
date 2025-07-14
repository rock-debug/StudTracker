import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReportGenerator {
    
    public static void generateComprehensiveReport(List<App.Meeting> meetings, String filename) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("STUDTRACK - COMPREHENSIVE ATTENDANCE REPORT");
            writer.println("=============================================");
            writer.println("Generated on: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writer.println();
            
            // Executive Summary
            generateExecutiveSummary(writer, meetings);
            
            // Online Meetings Analysis
            generateOnlineMeetingsReport(writer, meetings);
            
            // Offline Meetings Analysis
            generateOfflineMeetingsReport(writer, meetings);
            
            // Participant Performance Analysis
            generateParticipantAnalysis(writer, meetings);
            
            // Recommendations
            generateRecommendations(writer, meetings);
            
            System.out.println("✅ Comprehensive report generated: " + filename);
            
        } catch (Exception e) {
            System.err.println("❌ Error generating report: " + e.getMessage());
        }
    }
    
    private static void generateExecutiveSummary(PrintWriter writer, List<App.Meeting> meetings) {
        writer.println("EXECUTIVE SUMMARY");
        writer.println("=================");
        
        long totalMeetings = meetings.size();
        long onlineMeetings = meetings.stream().filter(m -> "online".equals(m.type)).count();
        long offlineMeetings = meetings.stream().filter(m -> "offline".equals(m.type)).count();
        
        writer.println("Total Meetings: " + totalMeetings);
        writer.println("Online Meetings: " + onlineMeetings);
        writer.println("Offline Meetings: " + offlineMeetings);
        writer.println();
        
        // Overall attendance statistics
        Map<String, Long> participantTotalTime = new HashMap<>();
        Map<String, Integer> participantMeetingCount = new HashMap<>();
        
        for (App.Meeting meeting : meetings) {
            for (App.Participant participant : meeting.participants) {
                participantMeetingCount.merge(participant.name, 1, Integer::sum);
                
                if ("online".equals(meeting.type)) {
                    long totalSeconds = participant.sessions.stream()
                        .mapToLong(s -> s.durationSeconds).sum();
                    participantTotalTime.merge(participant.name, totalSeconds, Long::sum);
                } else {
                    if (participant.attendance != null && participant.attendance.checkIn != null && 
                        participant.attendance.checkOut != null) {
                        long totalSeconds = Duration.between(participant.attendance.checkIn, 
                            participant.attendance.checkOut).getSeconds();
                        participantTotalTime.merge(participant.name, totalSeconds, Long::sum);
                    }
                }
            }
        }
        
        writer.println("TOP PARTICIPANTS BY TOTAL TIME:");
        participantTotalTime.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .forEach(entry -> {
                long hours = entry.getValue() / 3600;
                long minutes = (entry.getValue() % 3600) / 60;
                writer.printf("  %s: %d hours %d minutes%n", entry.getKey(), hours, minutes);
            });
        writer.println();
    }
    
    private static void generateOnlineMeetingsReport(PrintWriter writer, List<App.Meeting> meetings) {
        List<App.Meeting> onlineMeetings = meetings.stream()
            .filter(m -> "online".equals(m.type))
            .collect(Collectors.toList());
        
        if (onlineMeetings.isEmpty()) {
            writer.println("ONLINE MEETINGS ANALYSIS");
            writer.println("========================");
            writer.println("No online meetings found.");
            writer.println();
            return;
        }
        
        writer.println("ONLINE MEETINGS ANALYSIS");
        writer.println("========================");
        
        for (App.Meeting meeting : onlineMeetings) {
            writer.println("\nMeeting: " + meeting.title + " (" + meeting.date + ")");
            writer.println("-".repeat(50));
            
            // Participant session analysis
            for (App.Participant participant : meeting.participants) {
                long totalSeconds = participant.sessions.stream()
                    .mapToLong(s -> s.durationSeconds).sum();
                long hours = totalSeconds / 3600;
                long minutes = (totalSeconds % 3600) / 60;
                
                writer.printf("  %s: %d hours %d minutes (%d sessions)%n", 
                    participant.name, hours, minutes, participant.sessions.size());
                
                // Session details
                for (int i = 0; i < participant.sessions.size(); i++) {
                    App.Session session = participant.sessions.get(i);
                    long sessionMinutes = session.durationSeconds / 60;
                    writer.printf("    Session %d: %s - %s (%d minutes)%n",
                        i + 1,
                        session.join.format(DateTimeFormatter.ofPattern("HH:mm")),
                        session.leave.format(DateTimeFormatter.ofPattern("HH:mm")),
                        sessionMinutes);
                }
            }
            
            // Chat analysis
            if (!meeting.chats.isEmpty()) {
                writer.println("\n  Chat Activity:");
                Map<String, Long> chatCounts = meeting.chats.stream()
                    .collect(Collectors.groupingBy(c -> c.sender, Collectors.counting()));
                
                chatCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(entry -> {
                        writer.printf("    %s: %d messages%n", entry.getKey(), entry.getValue());
                    });
            }
        }
        writer.println();
    }
    
    private static void generateOfflineMeetingsReport(PrintWriter writer, List<App.Meeting> meetings) {
        List<App.Meeting> offlineMeetings = meetings.stream()
            .filter(m -> "offline".equals(m.type))
            .collect(Collectors.toList());
        
        if (offlineMeetings.isEmpty()) {
            writer.println("OFFLINE MEETINGS ANALYSIS");
            writer.println("=========================");
            writer.println("No offline meetings found.");
            writer.println();
            return;
        }
        
        writer.println("OFFLINE MEETINGS ANALYSIS");
        writer.println("=========================");
        
        for (App.Meeting meeting : offlineMeetings) {
            writer.println("\nMeeting: " + meeting.title + " (" + meeting.date + ") at " + meeting.location);
            writer.println("-".repeat(60));
            
            // Attendance summary
            long presentCount = meeting.participants.stream()
                .filter(p -> p.attendance != null && "present".equals(p.attendance.status))
                .count();
            long lateCount = meeting.participants.stream()
                .filter(p -> p.attendance != null && "late".equals(p.attendance.status))
                .count();
            long absentCount = meeting.participants.stream()
                .filter(p -> p.attendance != null && "absent".equals(p.attendance.status))
                .count();
            
            double attendanceRate = (double)(presentCount + lateCount) / meeting.participants.size() * 100;
            
            writer.printf("Attendance Rate: %.1f%%%n", attendanceRate);
            writer.printf("Present: %d, Late: %d, Absent: %d%n", presentCount, lateCount, absentCount);
            writer.println();
            
            // Participant details
            for (App.Participant participant : meeting.participants) {
                if (participant.attendance != null) {
                    writer.printf("  %s: %s", participant.name, participant.attendance.status);
                    
                    if ("present".equals(participant.attendance.status) || "late".equals(participant.attendance.status)) {
                        if (participant.attendance.checkIn != null && participant.attendance.checkOut != null) {
                            long totalMinutes = Duration.between(participant.attendance.checkIn, 
                                participant.attendance.checkOut).toMinutes();
                            writer.printf(" (%d minutes)", totalMinutes);
                        }
                        
                        if (participant.attendance.lateByMinutes > 0) {
                            writer.printf(" - Late by %d minutes", participant.attendance.lateByMinutes);
                        }
                        if (participant.attendance.earlyLeaveMinutes > 0) {
                            writer.printf(" - Left %d minutes early", participant.attendance.earlyLeaveMinutes);
                        }
                    }
                    writer.println();
                }
            }
            
            // Activity analysis
            if (!meeting.activities.isEmpty()) {
                writer.println("\n  Activity Summary:");
                Map<String, Long> activityCounts = meeting.activities.stream()
                    .collect(Collectors.groupingBy(a -> a.activity, Collectors.counting()));
                
                activityCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(entry -> {
                        writer.printf("    %s: %d times%n", entry.getKey(), entry.getValue());
                    });
                
                // Most active participants
                Map<String, Long> participantActivityCounts = meeting.activities.stream()
                    .collect(Collectors.groupingBy(a -> a.participant, Collectors.counting()));
                
                writer.println("\n  Most Active Participants:");
                participantActivityCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(3)
                    .forEach(entry -> {
                        writer.printf("    %s: %d activities%n", entry.getKey(), entry.getValue());
                    });
            }
        }
        writer.println();
    }
    
    private static void generateParticipantAnalysis(PrintWriter writer, List<App.Meeting> meetings) {
        writer.println("PARTICIPANT PERFORMANCE ANALYSIS");
        writer.println("===============================");
        
        Map<String, ParticipantStats> participantStats = new HashMap<>();
        
        // Initialize stats for all participants
        for (App.Meeting meeting : meetings) {
            for (App.Participant participant : meeting.participants) {
                participantStats.computeIfAbsent(participant.name, k -> new ParticipantStats());
            }
        }
        
        // Collect statistics
        for (App.Meeting meeting : meetings) {
            for (App.Participant participant : meeting.participants) {
                ParticipantStats stats = participantStats.get(participant.name);
                stats.totalMeetings++;
                
                if ("online".equals(meeting.type)) {
                    stats.onlineMeetings++;
                    long totalSeconds = participant.sessions.stream()
                        .mapToLong(s -> s.durationSeconds).sum();
                    stats.totalOnlineTime += totalSeconds;
                    stats.totalSessions += participant.sessions.size();
                    
                    // Chat activity
                    long chatCount = meeting.chats.stream()
                        .filter(c -> c.sender.equals(participant.name))
                        .count();
                    stats.totalChatMessages += chatCount;
                    
                } else {
                    stats.offlineMeetings++;
                    if (participant.attendance != null) {
                        if ("present".equals(participant.attendance.status)) {
                            stats.presentCount++;
                        } else if ("late".equals(participant.attendance.status)) {
                            stats.lateCount++;
                        } else if ("absent".equals(participant.attendance.status)) {
                            stats.absentCount++;
                        }
                        
                        if (participant.attendance.checkIn != null && participant.attendance.checkOut != null) {
                            long totalMinutes = Duration.between(participant.attendance.checkIn, 
                                participant.attendance.checkOut).toMinutes();
                            stats.totalOfflineTime += totalMinutes * 60; // Convert to seconds
                        }
                    }
                    
                    // Activity count
                    long activityCount = meeting.activities.stream()
                        .filter(a -> a.participant.equals(participant.name))
                        .count();
                    stats.totalActivities += activityCount;
                }
            }
        }
        
        // Generate participant rankings
        writer.println("\nTOP PARTICIPANTS BY ENGAGEMENT:");
        participantStats.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().getTotalTime(), a.getValue().getTotalTime()))
            .limit(5)
            .forEach(entry -> {
                ParticipantStats stats = entry.getValue();
                long totalHours = stats.getTotalTime() / 3600;
                long totalMinutes = (stats.getTotalTime() % 3600) / 60;
                writer.printf("  %s: %d hours %d minutes (%d meetings)%n", 
                    entry.getKey(), totalHours, totalMinutes, stats.totalMeetings);
            });
        
        writer.println("\nATTENDANCE RELIABILITY:");
        participantStats.entrySet().stream()
            .filter(e -> e.getValue().offlineMeetings > 0)
            .sorted((a, b) -> {
                double aRate = (double)(a.getValue().presentCount + a.getValue().lateCount) / a.getValue().offlineMeetings;
                double bRate = (double)(b.getValue().presentCount + b.getValue().lateCount) / b.getValue().offlineMeetings;
                return Double.compare(bRate, aRate);
            })
            .limit(5)
            .forEach(entry -> {
                ParticipantStats stats = entry.getValue();
                double attendanceRate = (double)(stats.presentCount + stats.lateCount) / stats.offlineMeetings * 100;
                writer.printf("  %s: %.1f%% (%d/%d meetings)%n", 
                    entry.getKey(), attendanceRate, stats.presentCount + stats.lateCount, stats.offlineMeetings);
            });
        
        writer.println();
    }
    
    private static void generateRecommendations(PrintWriter writer, List<App.Meeting> meetings) {
        writer.println("RECOMMENDATIONS");
        writer.println("===============");
        
        // Analyze patterns and provide recommendations
        long totalMeetings = meetings.size();
        long onlineMeetings = meetings.stream().filter(m -> "online".equals(m.type)).count();
        long offlineMeetings = meetings.stream().filter(m -> "offline".equals(m.type)).count();
        
        writer.println("1. MEETING DISTRIBUTION:");
        writer.printf("   - Online meetings: %d (%.1f%%)%n", onlineMeetings, (double)onlineMeetings/totalMeetings*100);
        writer.printf("   - Offline meetings: %d (%.1f%%)%n", offlineMeetings, (double)offlineMeetings/totalMeetings*100);
        
        if (onlineMeetings > offlineMeetings) {
            writer.println("   Recommendation: Consider increasing offline meetings for better engagement");
        } else if (offlineMeetings > onlineMeetings) {
            writer.println("   Recommendation: Consider online meetings for flexibility and accessibility");
        }
        writer.println();
        
        // Attendance analysis
        Map<String, Integer> participantMeetingCount = new HashMap<>();
        Map<String, Integer> participantAbsenceCount = new HashMap<>();
        
        for (App.Meeting meeting : meetings) {
            for (App.Participant participant : meeting.participants) {
                participantMeetingCount.merge(participant.name, 1, Integer::sum);
                
                if ("offline".equals(meeting.type) && participant.attendance != null && 
                    "absent".equals(participant.attendance.status)) {
                    participantAbsenceCount.merge(participant.name, 1, Integer::sum);
                }
            }
        }
        
        writer.println("2. ATTENDANCE ISSUES:");
        participantAbsenceCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .forEach(entry -> {
                int totalMeetingsForParticipant = participantMeetingCount.get(entry.getKey());
                double absenceRate = (double)entry.getValue() / totalMeetingsForParticipant * 100;
                writer.printf("   - %s: %.1f%% absence rate (%d absences in %d meetings)%n", 
                    entry.getKey(), absenceRate, entry.getValue(), totalMeetingsForParticipant);
            });
        
        if (!participantAbsenceCount.isEmpty()) {
            writer.println("   Recommendation: Implement attendance tracking and follow-up for frequent absentees");
        }
        writer.println();
        
        // Engagement analysis
        writer.println("3. ENGAGEMENT OPPORTUNITIES:");
        Map<String, Long> participantTotalTime = new HashMap<>();
        
        for (App.Meeting meeting : meetings) {
            for (App.Participant participant : meeting.participants) {
                if ("online".equals(meeting.type)) {
                    long totalSeconds = participant.sessions.stream()
                        .mapToLong(s -> s.durationSeconds).sum();
                    participantTotalTime.merge(participant.name, totalSeconds, Long::sum);
                } else {
                    if (participant.attendance != null && participant.attendance.checkIn != null && 
                        participant.attendance.checkOut != null) {
                        long totalSeconds = Duration.between(participant.attendance.checkIn, 
                            participant.attendance.checkOut).getSeconds();
                        participantTotalTime.merge(participant.name, totalSeconds, Long::sum);
                    }
                }
            }
        }
        
        // Find participants with low engagement
        long averageTime = participantTotalTime.values().stream()
            .mapToLong(Long::longValue)
            .sum() / participantTotalTime.size();
        
        participantTotalTime.entrySet().stream()
            .filter(entry -> entry.getValue() < averageTime * 0.7) // 70% of average
            .forEach(entry -> {
                long hours = entry.getValue() / 3600;
                long minutes = (entry.getValue() % 3600) / 60;
                writer.printf("   - %s: Low engagement (%d hours %d minutes total)%n", 
                    entry.getKey(), hours, minutes);
            });
        
        if (participantTotalTime.values().stream().anyMatch(time -> time < averageTime * 0.7)) {
            writer.println("   Recommendation: Implement engagement strategies for low-participation members");
        }
        writer.println();
        
        writer.println("4. GENERAL RECOMMENDATIONS:");
        writer.println("   - Regular attendance tracking and reporting");
        writer.println("   - Mix of online and offline meetings for optimal engagement");
        writer.println("   - Activity-based learning for offline sessions");
        writer.println("   - Follow-up with participants showing declining engagement");
        writer.println("   - Regular feedback collection to improve meeting effectiveness");
    }
    
    private static class ParticipantStats {
        int totalMeetings = 0;
        int onlineMeetings = 0;
        int offlineMeetings = 0;
        long totalOnlineTime = 0;
        long totalOfflineTime = 0;
        int totalSessions = 0;
        long totalChatMessages = 0;
        int presentCount = 0;
        int lateCount = 0;
        int absentCount = 0;
        long totalActivities = 0;
        
        long getTotalTime() {
            return totalOnlineTime + totalOfflineTime;
        }
    }
} 