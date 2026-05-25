package Game;

import java.io.*;
import java.util.*;

/**
 * Reads and writes the top-5 player scores to a local "scores.txt" file.
 * Format per line: NAME|SCORE
 */
public class ScoreManager {
    private static final String FILE_PATH = "scores.txt";
    private static final int MAX_ENTRIES = 5;

    /** Saves a new score. If it makes the top 5, it is persisted. */
    public static void saveScore(String name, int score) {
        List<String[]> all = loadScores();
        all.add(new String[]{ name.trim().toUpperCase(), String.valueOf(score) });
        all.sort((a, b) -> Integer.parseInt(b[1]) - Integer.parseInt(a[1]));
        if (all.size() > MAX_ENTRIES) all = all.subList(0, MAX_ENTRIES);

        try (PrintWriter pw = new PrintWriter(new FileWriter(FILE_PATH))) {
            for (String[] entry : all) {
                pw.println(entry[0] + "|" + entry[1]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns a list of [name, score] pairs, sorted descending by score.
     * Returns an empty list if the file does not exist or is unreadable.
     */
    public static List<String[]> loadScores() {
        List<String[]> list = new ArrayList<>();
        File f = new File(FILE_PATH);
        if (!f.exists()) return list;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length == 2) list.add(parts);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        list.sort((a, b) -> Integer.parseInt(b[1]) - Integer.parseInt(a[1]));
        return list;
    }

    public static boolean hasScores() {
        return !loadScores().isEmpty();
    }
}
