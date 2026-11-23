package fr.firewatch;

import java.util.List;
import java.util.Locale;

/** FRONT END : génère la page HTML (CSS + graphique SVG) entièrement en Java. */
public class Page {

    static final String CSS =
        ":root{--encre:#10243e;--papier:#fff;--fond:#e8edf1;--ligne:#cfd8e0;--gris:#55657a}\n" +
        "*{box-sizing:border-box}\n" +
        "body{margin:0;background:var(--fond);color:var(--encre);font-family:'Segoe UI',Tahoma,sans-serif;line-height:1.5}\n" +
        ".haut{background:var(--encre);color:#fff;padding:18px 20px}\n" +
        ".haut div{max-width:820px;margin:0 auto;display:flex;justify-content:space-between;align-items:baseline;gap:12px;flex-wrap:wrap}\n" +
        ".haut h1{margin:0;font-size:1.5rem;letter-spacing:.01em}\n" +
        ".haut p{margin:0;color:#aebbd0;font-size:.9rem}\n" +
        "main{max-width:820px;margin:0 auto;padding:0 20px 56px}\n" +
        ".etat{margin-top:24px;padding:34px 30px;border-radius:10px;color:#fff;background:#667085}\n" +
        ".etat h2{margin:0;font-size:2.6rem;line-height:1.1;letter-spacing:-.02em}\n" +
        ".etat p{margin:8px 0 0;font-size:1.1rem;opacity:.92}\n" +
        ".etat.ok{background:#0e7a4f}\n" +
        ".etat.fumee{background:#c27803}\n" +
        ".etat.flamme{background:#c9302c;animation:pouls 1.4s ease-out infinite}\n" +
        "@keyframes pouls{0%{box-shadow:0 0 0 0 rgba(201,48,44,.55)}100%{box-shadow:0 0 0 22px rgba(201,48,44,0)}}\n" +
        "@media (prefers-reduced-motion:reduce){.etat.flamme{animation:none}}\n" +
        ".valeurs{display:flex;background:var(--papier);border-radius:10px;margin-top:14px}\n" +
        ".valeurs div{flex:1;padding:18px 22px}\n" +
        ".valeurs div+div{border-left:1px solid var(--ligne)}\n" +
        ".valeurs span{display:block;font-size:1.8rem;font-weight:600;font-variant-numeric:tabular-nums}\n" +
        ".valeurs small{color:var(--gris)}\n" +
        "h3{font-size:1rem;margin:34px 0 10px;color:var(--gris);font-weight:600}\n" +
        "svg{width:100%;height:auto;background:var(--papier);border-radius:10px;display:block}\n" +
        "ul{list-style:none;margin:0;padding:0;background:var(--papier);border-radius:10px;overflow:hidden}\n" +
        "li{display:flex;gap:16px;padding:12px 18px;border-bottom:1px solid var(--ligne);border-left:5px solid var(--ligne)}\n" +
        "li:last-child{border-bottom:0}\n" +
        "li time{color:var(--gris);min-width:76px;font-variant-numeric:tabular-nums}\n" +
        "li.flamme{border-left-color:#c9302c}li.fumee{border-left-color:#c27803}li.pompe{border-left-color:#1d6fb8}\n" +
        "li.vide{color:var(--gris);border-left-color:transparent}\n" +
        ".controle{background:var(--papier);border-radius:10px;padding:16px 18px}\n" +
        ".etatcmd{margin:0 0 12px;color:var(--gris)}\n" +
        ".boutons{display:flex;flex-wrap:wrap;gap:8px}\n" +
        ".controle form{margin:0}.controle .seuil{margin-top:12px;display:flex;gap:8px;flex-wrap:wrap}\n" +
        ".controle button,.bouton{font:inherit;font-weight:600;background:#10243e;color:#fff;border:0;border-radius:8px;padding:10px 16px;cursor:pointer;text-decoration:none;display:inline-block}\n" +
        ".controle button.sec{background:#55657a;margin-top:12px}\n" +
        ".controle input{font:inherit;padding:9px 10px;border:2px solid var(--ligne);border-radius:8px;background:var(--papier);color:inherit}\n" +
        "li.commande{border-left-color:#6b4fbb}\n" +
        "@media (max-width:520px){.etat{padding:26px 20px}.etat h2{font-size:2rem}.valeurs div{padding:14px}.valeurs span{font-size:1.4rem}}\n";

    /** Texte anglais d'une alerte (selon son type, même pour les anciennes lignes en français). */
    static String texteEvenement(Db.Evenement e) {
        if ("flamme".equals(e.type)) return "Flame detected";
        if ("pompe".equals(e.type)) return "Pump activated";
        if ("fumee".equals(e.type)) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(e.message);
            return "Smoke detected" + (m.find() ? " (MQ-2: " + m.group(1) + ")" : "");
        }
        return e.message;
    }

    /** Panneau de commandes : visible par tous (état), boutons réservés à l'administrateur. */
    static String panneau(Db.Commande c, boolean admin) {
        StringBuilder s = new StringBuilder("<h3>Control panel</h3><section class=\"controle\"><p class=\"etatcmd\">Automatic mode: <strong>"
            + (c.auto ? "ARMED" : "DISARMED") + "</strong> &middot; Buzzer: <strong>"
            + (c.muteRestant > 0 ? "silenced (" + c.muteRestant + " s)" : "active") + "</strong> &middot; Manual pump: <strong>"
            + (c.pompeRestant > 0 ? "ON (" + c.pompeRestant + " s)" : "OFF") + "</strong> &middot; Smoke threshold: <strong>"
            + c.seuil + "</strong></p>");
        if (!admin) return s.append("<a class=\"bouton\" href=\"/login\">Admin login to send commands</a></section>").toString();
        s.append("<div class=\"boutons\">")
         .append(bouton("pompe_on", 0, "Pump ON (30 s)", "Start the pump manually for 30 seconds?"))
         .append(bouton("pompe_off", 0, "Pump OFF", null))
         .append(bouton("mute_on", 0, "Silence buzzer (5 min)", null))
         .append(bouton("mute_off", 0, "Restore buzzer", null))
         .append(c.auto ? bouton("auto", 0, "Disarm automatic mode", "Disarm automatic extinguishing? The pump will no longer start by itself.")
                        : bouton("auto", 1, "Arm automatic mode", null))
         .append(bouton("reset", 0, "Reset commands", null)).append("</div>")
         .append("<form method=\"post\" action=\"/commande\" class=\"seuil\"><input type=\"hidden\" name=\"action\" value=\"seuil\">"
               + "<input type=\"number\" name=\"valeur\" min=\"10\" max=\"900\" value=\"" + c.seuil + "\" aria-label=\"Smoke threshold\">"
               + "<button type=\"submit\">Set smoke threshold</button></form>")
         .append("<form method=\"post\" action=\"/logout\"><button type=\"submit\" class=\"sec\">Log out</button></form></section>");
        return s.toString();
    }

    static String bouton(String action, int valeur, String texte, String confirmation) {
        return "<form method=\"post\" action=\"/commande\"><input type=\"hidden\" name=\"action\" value=\"" + action
            + "\"><input type=\"hidden\" name=\"valeur\" value=\"" + valeur + "\"><button type=\"submit\""
            + (confirmation == null ? "" : " onclick=\"return confirm('" + confirmation + "')\"") + ">" + texte + "</button></form>";
    }

    public static String connexion(String erreur) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            + "<title>Login | FireWatch</title><style>" + CSS + "</style></head><body><header class=\"haut\"><div><h1>FireWatch</h1><p>Admin login</p></div></header>"
            + "<main><section class=\"controle\">" + (erreur == null ? "" : "<p role=\"alert\">" + esc(erreur) + "</p>")
            + "<form method=\"post\" action=\"/login\" class=\"seuil\"><input type=\"password\" name=\"password\" placeholder=\"Password\" required aria-label=\"Password\">"
            + "<button type=\"submit\">Log in</button></form><p><a href=\"/\">Back to dashboard</a></p></section></main></body></html>";
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String heure(String creeLe) { return creeLe.substring(11); } // heure UTC

    public static String rendre(Db.Mesure m, Long age, List<Db.Mesure> historique, List<Db.Evenement> evenements,
                                Db.Commande cmd, boolean admin) {
        String classe, titre, detail;
        if (m == null) { classe = ""; titre = "No data"; detail = "No readings received yet."; }
        else if (age != null && age > 15) { classe = ""; titre = "Sensors offline"; detail = "Last reading " + age + " s ago."; }
        else if (m.flamme) { classe = "flamme"; titre = "Flame detected"; detail = m.pompe ? "Extinguishing in progress." : "Alarm active."; }
        else if (m.fumee) { classe = "fumee"; titre = "Smoke detected"; detail = "Alarm active, no sprinkling without flame."; }
        else { classe = "ok"; titre = "All clear"; detail = "No smoke or flame."; }

        String info = m == null ? "Waiting for readings" : "Last reading at " + heure(m.creeLe) + " UTC";
        String vMq2 = m == null ? "–" : String.valueOf(m.mq2);
        String vFlamme = m == null ? "–" : (m.flamme ? "Detected" : "None");
        String vPompe = m == null ? "–" : (m.pompe ? "Running" : "Stopped");

        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
         .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
         .append("").append(admin ? "" : "<meta http-equiv=\"refresh\" content=\"2\">").append("<title>FireWatch</title><style>").append(CSS).append("</style></head><body>")
         .append("<header class=\"haut\"><div><h1>FireWatch</h1><p>").append(esc(info)).append("</p></div></header><main>")
         .append("<section class=\"etat ").append(classe).append("\"><h2>").append(esc(titre)).append("</h2><p>")
         .append(esc(detail)).append("</p></section>")
         .append("<section class=\"valeurs\"><div><span>").append(vMq2).append("</span><small>Smoke (MQ-2)</small></div>")
         .append("<div><span>").append(vFlamme).append("</span><small>Flame</small></div>")
         .append("<div><span>").append(vPompe).append("</span><small>Pump</small></div></section>")
         .append(panneau(cmd, admin)).append("<h3>Smoke level, last 60 readings</h3>").append(graphique(historique, cmd.seuil))
         .append("<h3>Alert history</h3><ul>");

        if (evenements.isEmpty()) h.append("<li class=\"vide\">No alerts recorded.</li>");
        for (Db.Evenement e : evenements) {
            h.append("<li class=\"").append(esc(e.type)).append("\"><time>").append(esc(heure(e.creeLe)))
             .append("</time><strong>").append(esc(texteEvenement(e))).append("</strong></li>");
        }
        return h.append("</ul></main>")
            .append(admin ? "<script>setInterval(function(){if(document.activeElement.tagName!=='INPUT')location.reload();},2000);</script>" : "")
            .append("</body></html>").toString();
    }

    /** Courbe du MQ-2 en SVG : grille, zone sous la courbe, ligne du seuil, points au-dessus du seuil. */
    static String graphique(List<Db.Mesure> ms, int seuil) {
        int w = 760, h = 250, x0 = 40, x1 = w - 14, yHaut = 16, yBas = h - 24;
        StringBuilder s = new StringBuilder("<svg viewBox=\"0 0 " + w + " " + h + "\" role=\"img\" aria-label=\"Smoke sensor curve\">");

        int[] reperes = {0, 512, 1024};
        for (int v : reperes) {
            double y = yBas - (v / 1024.0) * (yBas - yHaut);
            s.append(String.format(Locale.ROOT,
                "<line x1=\"%d\" y1=\"%.1f\" x2=\"%d\" y2=\"%.1f\" stroke=\"#e3e9ee\"/>"
                + "<text x=\"%d\" y=\"%.1f\" font-size=\"11\" fill=\"#55657a\" text-anchor=\"end\">%d</text>",
                x0, y, x1, y, x0 - 6, y + 4, v));
        }

        double ySeuil = yBas - (seuil / 1024.0) * (yBas - yHaut);
        s.append(String.format(Locale.ROOT,
            "<line x1=\"%d\" y1=\"%.1f\" x2=\"%d\" y2=\"%.1f\" stroke=\"#c27803\" stroke-width=\"1.5\" stroke-dasharray=\"6 5\"/>"
            + "<text x=\"%d\" y=\"%.1f\" font-size=\"12\" fill=\"#c27803\" text-anchor=\"end\">threshold %d</text>",
            x0, ySeuil, x1, ySeuil, x1, ySeuil - 6, seuil));

        if (!ms.isEmpty()) {
            double pas = ms.size() > 1 ? (double) (x1 - x0) / (ms.size() - 1) : 0;
            StringBuilder pts = new StringBuilder();
            StringBuilder points = new StringBuilder();
            for (int i = 0; i < ms.size(); i++) {
                double x = x0 + i * pas;
                double y = yBas - (ms.get(i).mq2 / 1024.0) * (yBas - yHaut);
                pts.append(String.format(Locale.ROOT, "%.1f,%.1f ", x, y));
                if (ms.get(i).mq2 > seuil) {
                    points.append(String.format(Locale.ROOT, "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"3.5\" fill=\"#c9302c\"/>", x, y));
                }
            }
            double xFin = x0 + (ms.size() - 1) * pas;
            s.append(String.format(Locale.ROOT, "<polygon fill=\"rgba(16,36,62,.10)\" points=\"%d,%d %s%.1f,%d\"/>", x0, yBas, pts, xFin, yBas));
            s.append("<polyline fill=\"none\" stroke=\"#10243e\" stroke-width=\"2\" stroke-linejoin=\"round\" points=\"").append(pts).append("\"/>");
            s.append(points);
            double yFin = yBas - (ms.get(ms.size() - 1).mq2 / 1024.0) * (yBas - yHaut);
            s.append(String.format(Locale.ROOT, "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"4.5\" fill=\"#10243e\"/>", xFin, yFin));
        }
        return s.append("</svg>").toString();
    }
}
