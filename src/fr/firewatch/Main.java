package fr.firewatch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** BACK END : serveur HTTP du JDK (aucun serveur externe à installer). */
public class Main {
    static final String CLE_API = System.getenv().getOrDefault("API_KEY", "maCleSecrete42");
    static Db db;

    public static void main(String[] args) throws Exception {
        db = new Db();
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "3000"));
        HttpServer serveur = HttpServer.create(new InetSocketAddress(port), 0);
        serveur.createContext("/", Main::accueil);                // le dashboard (front end)
        serveur.createContext("/login", Main::connexion);
        serveur.createContext("/logout", Main::deconnexion);
        serveur.createContext("/commande", Main::commande);          // ordres envoyés depuis le site
        serveur.createContext("/api/commande", Main::apiCommande);   // l'ESP8266 vient chercher les ordres
        serveur.createContext("/api/mesures", Main::recevoir);    // réception des mesures de l'ESP8266
        serveur.setExecutor(Executors.newFixedThreadPool(4));
        serveur.start();
        System.out.println("FireWatch disponible sur http://localhost:" + port);
    }

    static void accueil(HttpExchange ex) throws IOException {
        if (!ex.getRequestURI().getPath().equals("/")) { envoyer(ex, 404, "Page introuvable", "text/plain"); return; }
        try {
            String html = Page.rendre(db.derniere(), db.age(), db.historique(60), db.evenements(15), db.commande(), connecte(ex));
            envoyer(ex, 200, html, "text/html; charset=utf-8");
        } catch (Exception e) {
            envoyer(ex, 500, "Erreur serveur : " + e.getMessage(), "text/plain");
        }
    }

    static void recevoir(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { envoyer(ex, 405, "POST uniquement", "text/plain"); return; }
        if (!CLE_API.equals(ex.getRequestHeaders().getFirst("x-api-key"))) {
            envoyer(ex, 401, "Cle API invalide", "text/plain"); return;
        }
        String json = new String(lireTout(ex.getRequestBody()), StandardCharsets.UTF_8);
        int mq2;
        try { mq2 = (int) Math.round(Double.parseDouble(champ(json, "mq2"))); }
        catch (Exception e) { envoyer(ex, 400, "mq2 manquant ou invalide", "text/plain"); return; }
        if (mq2 < 0 || mq2 > 1024) { envoyer(ex, 400, "mq2 doit etre entre 0 et 1024", "text/plain"); return; }
        try {
            db.ajouter(mq2, "true".equals(champ(json, "flamme")), "true".equals(champ(json, "fumee")),
                    "true".equals(champ(json, "pompe")));
            envoyer(ex, 201, "{\"ok\":true}", "application/json");
        } catch (Exception e) {
            envoyer(ex, 500, "Erreur base de donnees", "text/plain");
        }
    }

    /** Lit la valeur d'un champ dans un JSON simple (sans bibliothèque). */
    static String champ(String json, String nom) {
        Matcher m = Pattern.compile("\"" + nom + "\"\\s*:\\s*([\\w.-]+)").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    // ---------- Connexion administrateur et commandes ----------
    static final java.util.Set<String> SESSIONS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static final String MOT_DE_PASSE = System.getenv().getOrDefault("ADMIN_PASSWORD", "admin123");
    static final String HTML = "text/html; charset=utf-8";

    static boolean connecte(HttpExchange ex) {
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) return false;
        for (String part : cookie.split(";")) {
            part = part.trim();
            if (part.startsWith("sid=") && SESSIONS.contains(part.substring(4))) return true;
        }
        return false;
    }

    static java.util.Map<String, String> formulaire(HttpExchange ex) throws IOException {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        String corps = new String(lireTout(ex.getRequestBody()), StandardCharsets.UTF_8);
        for (String paire : corps.split("&")) {
            int i = paire.indexOf('=');
            if (i > 0) m.put(java.net.URLDecoder.decode(paire.substring(0, i), "UTF-8"),
                             java.net.URLDecoder.decode(paire.substring(i + 1), "UTF-8"));
        }
        return m;
    }

    static void redirige(HttpExchange ex, String url) throws IOException {
        ex.getResponseHeaders().set("Location", url);
        ex.sendResponseHeaders(303, -1);
        ex.close();
    }

    static void connexion(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equals("GET")) { envoyer(ex, 200, Page.connexion(null), HTML); return; }
        String mdp = formulaire(ex).getOrDefault("password", "");
        if (!java.security.MessageDigest.isEqual(mdp.getBytes(StandardCharsets.UTF_8), MOT_DE_PASSE.getBytes(StandardCharsets.UTF_8))) {
            envoyer(ex, 401, Page.connexion("Wrong password."), HTML); return;
        }
        String sid = java.util.UUID.randomUUID().toString();
        SESSIONS.add(sid);
        ex.getResponseHeaders().add("Set-Cookie", "sid=" + sid + "; HttpOnly; SameSite=Strict; Path=/");
        redirige(ex, "/");
    }

    static void deconnexion(HttpExchange ex) throws IOException {
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        if (cookie != null) for (String part : cookie.split(";")) if (part.trim().startsWith("sid=")) SESSIONS.remove(part.trim().substring(4));
        ex.getResponseHeaders().add("Set-Cookie", "sid=; Max-Age=0; Path=/");
        redirige(ex, "/");
    }

    static void commande(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) { envoyer(ex, 405, "POST uniquement", "text/plain"); return; }
        if (!connecte(ex)) { redirige(ex, "/login"); return; }
        try {
            java.util.Map<String, String> f = formulaire(ex);
            int valeur = 0;
            try { valeur = Integer.parseInt(f.getOrDefault("valeur", "0").trim()); } catch (NumberFormatException e) { /* 0 */ }
            db.commander(f.getOrDefault("action", ""), valeur);
            redirige(ex, "/");
        } catch (IllegalArgumentException e) {
            envoyer(ex, 400, e.getMessage(), "text/plain; charset=utf-8");
        } catch (Exception e) {
            envoyer(ex, 500, "Erreur serveur", "text/plain");
        }
    }

    /** L'ESP8266 lit "pompe mute seuil auto", par exemple "0 0 300 1". */
    static void apiCommande(HttpExchange ex) throws IOException {
        if (!CLE_API.equals(ex.getRequestHeaders().getFirst("x-api-key"))) { envoyer(ex, 401, "Cle API invalide", "text/plain"); return; }
        try {
            Db.Commande c = db.commande();
            envoyer(ex, 200, (c.pompeRestant > 0 ? 1 : 0) + " " + (c.muteRestant > 0 ? 1 : 0) + " " + c.seuil + " " + (c.auto ? 1 : 0), "text/plain");
        } catch (Exception e) {
            envoyer(ex, 500, "Erreur base de donnees", "text/plain");
        }
    }

    static byte[] lireTout(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[1024];
        int n;
        while ((n = in.read(b)) != -1) o.write(b, 0, n);
        return o.toByteArray();
    }

    static void envoyer(HttpExchange ex, int code, String corps, String type) throws IOException {
        byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, octets.length);
        ex.getResponseBody().write(octets);
        ex.close();
    }
}
