import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.*;
import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;

public class App {
    public static void main(String[] args) {
        try {
            // Load JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(new File("src/main/resources/meet_data.json"));
            JsonNode participants = root.get("participants");

            // Dataset for chart
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            for (JsonNode participant : participants) {
                String name = participant.get("name").asText();
                int totalMinutes = 0;

                for (JsonNode session : participant.get("sessions")) {
                    LocalDateTime join = LocalDateTime.parse(session.get("join").asText(), formatter);
                    LocalDateTime leave = LocalDateTime.parse(session.get("leave").asText(), formatter);
                    totalMinutes += Duration.between(join, leave).toMinutes();
                }

                dataset.addValue(totalMinutes, "Minutes", name);
            }

            // Create chart
            JFreeChart barChart = ChartFactory.createBarChart(
                "Time Spent in GMeet",
                "Participant",
                "Minutes",
                dataset
            );

            // Show chart in window
            JFrame frame = new JFrame("GMeet Minutes");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new ChartPanel(barChart));
            frame.pack();
            frame.setVisible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
