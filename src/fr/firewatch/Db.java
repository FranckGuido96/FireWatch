package fr.firewatch;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** BASE DE DONNÉES : accès JDBC à SQLite (fichier firewatch.db). */
public class Db {
    private static final String URL = "jdbc:sqlite:firewatch.db";

    public static class Mesure {
        public final int mq2; public final boolean flamme, fumee, pompe; public final String creeLe;
        public Mesure(int mq2, boolean flamme, boolean fumee, boolean pompe, String creeLe) {
            this.mq2 = mq2; this.flamme = flamme; this.fumee = fumee; this.pompe = pompe; this.creeLe = creeLe;
        }
    }
    public static class Evenement {
        public final String type, message, creeLe;
        public Evenement(String type, String message, String creeLe) {
            this.type = type; this.message = message; this.creeLe = creeLe;
        }
    }

    public Db() throws SQLException {
        initCommande();
        try (Connection c = DriverManager.getConnection(URL); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS mesures ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, mq2 INTEGER NOT NULL, flamme INTEGER NOT NULL, "
                    + "fumee INTEGER NOT NULL, pompe INTEGER NOT NULL, cree_le TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE TABLE IF NOT EXISTS evenements ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, message TEXT NOT NULL, "
                    + "cree_le TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    /** Enregistre une mesure et crée les alertes quand un état passe à "actif". */
    public synchronized void ajouter(int mq2, boolean flamme, boolean fumee, boolean pompe) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL)) {
            Mesure prec = derniere(c);
            if (flamme && (prec == null || !prec.flamme)) evenement(c, "flamme", "Flamme détectée");
            if (fumee && (prec == null || !prec.fumee)) evenement(c, "fumee", "Fumée détectée (MQ-2 : " + mq2 + ")");
            if (pompe && (prec == null || !prec.pompe)) evenement(c, "pompe", "Pompe activée");
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO mesures (mq2, flamme, fumee, pompe) VALUES (?,?,?,?)")) {
                p.setInt(1, mq2);
                p.setInt(2, flamme ? 1 : 0);
                p.setInt(3, fumee ? 1 : 0);
                p.setInt(4, pompe ? 1 : 0);
                p.executeUpdate();
            }
        }
    }

    private void evenement(Connection c, String type, String message) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO evenements (type, message) VALUES (?,?)")) {
            p.setString(1, type);
            p.setString(2, message);
            p.executeUpdate();
        }
    }

    private Mesure lire(ResultSet r) throws SQLException {
        return new Mesure(r.getInt("mq2"), r.getInt("flamme") == 1, r.getInt("fumee") == 1,
                r.getInt("pompe") == 1, r.getString("cree_le"));
    }

    private Mesure derniere(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT * FROM mesures ORDER BY id DESC LIMIT 1")) {
            return r.next() ? lire(r) : null;
        }
    }

    public Mesure derniere() throws SQLException {
        try (Connection c = DriverManager.getConnection(URL)) { return derniere(c); }
    }

    /** Ancienneté de la dernière mesure en secondes (null s'il n'y en a pas). */
    public Long age() throws SQLException {
        try (Connection c = DriverManager.getConnection(URL); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT strftime('%s','now') - strftime('%s', cree_le) "
                     + "FROM mesures ORDER BY id DESC LIMIT 1")) {
            return r.next() ? r.getLong(1) : null;
        }
    }

    /** Dernières mesures, de la plus ancienne à la plus récente. */
    public List<Mesure> historique(int n) throws SQLException {
        List<Mesure> liste = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(URL);
             PreparedStatement p = c.prepareStatement("SELECT * FROM mesures ORDER BY id DESC LIMIT ?")) {
            p.setInt(1, n);
            try (ResultSet r = p.executeQuery()) { while (r.next()) liste.add(0, lire(r)); }
        }
        return liste;
    }

    public List<Evenement> evenements(int n) throws SQLException {
        List<Evenement> liste = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(URL);
             PreparedStatement p = c.prepareStatement("SELECT * FROM evenements ORDER BY id DESC LIMIT ?")) {
            p.setInt(1, n);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) liste.add(new Evenement(r.getString("type"), r.getString("message"), r.getString("cree_le")));
            }
        }
        return liste;
    }

    /** Commandes envoyées depuis le site (une seule ligne, id = 1). */
    public static class Commande {
        public final int pompeRestant, muteRestant, seuil; public final boolean auto;
        public Commande(int pompeRestant, int muteRestant, int seuil, boolean auto) {
            this.pompeRestant = pompeRestant; this.muteRestant = muteRestant; this.seuil = seuil; this.auto = auto;
        }
    }

    private void initCommande() throws SQLException {
        try (Connection c = DriverManager.getConnection(URL); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS commande (id INTEGER PRIMARY KEY, pompe_jusqua INTEGER NOT NULL DEFAULT 0, "
                + "mute_jusqua INTEGER NOT NULL DEFAULT 0, seuil INTEGER NOT NULL DEFAULT 300, auto_mode INTEGER NOT NULL DEFAULT 1)");
            s.execute("INSERT OR IGNORE INTO commande (id) VALUES (1)");
        }
    }

    public Commande commande() throws SQLException {
        try (Connection c = DriverManager.getConnection(URL); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT pompe_jusqua, mute_jusqua, seuil, auto_mode, "
                + "CAST(strftime('%s','now') AS INTEGER) AS t FROM commande WHERE id = 1")) {
            r.next();
            long t = r.getLong("t");
            return new Commande((int) Math.max(0, r.getLong("pompe_jusqua") - t), (int) Math.max(0, r.getLong("mute_jusqua") - t),
                r.getInt("seuil"), r.getInt("auto_mode") == 1);
        }
    }

    /** Enregistre une commande du site et la note dans l'historique. */
    public synchronized void commander(String action, int valeur) throws SQLException {
        String sql, texte;
        String maintenant = "CAST(strftime('%s','now') AS INTEGER)";
        switch (action) {
            case "pompe_on": sql = "UPDATE commande SET pompe_jusqua = " + maintenant + " + 30 WHERE id = 1"; texte = "Manual pump started (30 s)"; break;
            case "pompe_off": sql = "UPDATE commande SET pompe_jusqua = 0 WHERE id = 1"; texte = "Manual pump stopped"; break;
            case "mute_on": sql = "UPDATE commande SET mute_jusqua = " + maintenant + " + 300 WHERE id = 1"; texte = "Buzzer silenced (5 min)"; break;
            case "mute_off": sql = "UPDATE commande SET mute_jusqua = 0 WHERE id = 1"; texte = "Buzzer sound restored"; break;
            case "reset": sql = "UPDATE commande SET pompe_jusqua = 0, mute_jusqua = 0 WHERE id = 1"; texte = "Commands reset"; break;
            case "auto": sql = "UPDATE commande SET auto_mode = " + (valeur == 1 ? 1 : 0) + " WHERE id = 1";
                texte = valeur == 1 ? "Automatic mode armed" : "Automatic mode disarmed"; break;
            case "seuil":
                if (valeur < 100 || valeur > 900) throw new IllegalArgumentException("Threshold must be between 100 and 900.");
                sql = "UPDATE commande SET seuil = " + valeur + " WHERE id = 1"; texte = "Smoke threshold set to " + valeur; break;
            default: throw new IllegalArgumentException("Unknown action.");
        }
        try (Connection c = DriverManager.getConnection(URL); Statement s = c.createStatement()) {
            s.executeUpdate(sql);
            evenement(c, "commande", texte);
        }
    }
}
