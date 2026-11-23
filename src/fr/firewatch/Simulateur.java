package fr.firewatch;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/** Simule l'ESP8266 pour tester le site sans matériel. Lancer Main d'abord. */
public class Simulateur {
    public static void main(String[] args) throws Exception {
        String url = "http://localhost:3000/api/mesures";
        String cle = "maCleSecrete42";
        Random hasard = new Random();
        int t = 0;
        while (true) {
            int c = t++ % 40; // cycle de 80 s, une mesure toutes les 2 s
            int mq2; boolean flamme = false, fumee = false, pompe = false;
            if (c < 15) mq2 = 55 + hasard.nextInt(16);
            else if (c < 22) { mq2 = 320 + hasard.nextInt(200); fumee = true; }
            else if (c < 27) { mq2 = 400 + hasard.nextInt(300); fumee = true; flamme = true; pompe = c >= 24; }
            else if (c < 30) mq2 = 250 + hasard.nextInt(100);
            else mq2 = 55 + hasard.nextInt(16);

            String json = "{\"mq2\":" + mq2 + ",\"flamme\":" + flamme + ",\"fumee\":" + fumee + ",\"pompe\":" + pompe + "}";
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setRequestProperty("x-api-key", cle);
                con.setConnectTimeout(2000);
                con.setDoOutput(true);
                try (OutputStream o = con.getOutputStream()) { o.write(json.getBytes(StandardCharsets.UTF_8)); }
                System.out.println(con.getResponseCode() + " " + json);
            } catch (Exception e) {
                System.out.println("Serveur injoignable : " + e.getMessage());
            }
            Thread.sleep(2000);
        }
    }
}
