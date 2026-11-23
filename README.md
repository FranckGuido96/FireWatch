**FireWatch** est un système de protection incendie connecté. Une carte NodeMCU ESP8266
surveille la fumée (MQ-2) et la flamme, déclenche localement l'alarme et la pompe, puis
envoie ses mesures à un serveur Java. Le serveur les stocke dans SQLite et les affiche
sur un tableau de bord web (courbe en temps réel, historique des alertes).

## Fonctionnalités
- Détection de fumée (seuil réglable) et de flamme, avec alarme sonore et lumineuse
- Extinction automatique par pompe, avec délai de confirmation et temps de repos
- Sécurité locale : le mode automatique fonctionne même sans Wi-Fi
- Tableau de bord web : mesures, courbe, historique des événements
- Espace administrateur protégé par mot de passe : pompe manuelle, coupure du buzzer,
  réglage du seuil, armement du mode automatique
- Simulateur inclus pour tester sans matériel

## Technologies
ESP8266 / Arduino C++ · Java 8 (serveur HTTP du JDK) · SQLite (JDBC) · HTML/CSS/SVG

# FireWatch

Système connecté de détection et d'extinction d'incendie (ESP8266 + serveur Java + tableau de bord web).

## Contenu
- `src/fr/firewatch/` : serveur Java (Main, Db, Page, Simulateur)
- `arduino/FireWatch_ESP8266/` : code de la carte NodeMCU ESP8266

## Lancer (NetBeans 8.2, JDK 8)
1. Télécharger `sqlite-jdbc-3.36.0.3.jar` et l'ajouter dans Libraries.
2. Définir la classe principale : `fr.firewatch.Main`.
3. Lancer, puis ouvrir http://localhost:3000.
4. Sans matériel : lancer `Simulateur.java`.

## Configuration
- `API_KEY` (défaut : `maCleSecrete42`) et `ADMIN_PASSWORD` (défaut : `admin123`) : à changer avant tout usage réel.
- Dans le code Arduino : renseigner le Wi-Fi, l'IP du PC et la même clé API.