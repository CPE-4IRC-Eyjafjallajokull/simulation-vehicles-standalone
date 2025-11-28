# **Spécifications Fonctionnelles Détaillées (SFD)**

## Système intégré de gestion et suivi des incidents – SDMIS Lyon/Villeurbanne

---

## **1. Objectifs généraux du système**

Le système doit permettre au SDMIS de Lyon :

* de **centraliser, suivre et gérer en temps quasi-réel** l’ensemble des incidents, interventions et ressources ;
* d’**assister les opérateurs** dans l’analyse et la prise de décision ;
* de **coordonner efficacement les équipes terrain** via un dispositif IoT embarqué ;
* d’assurer une **communication fiable, robuste et sécurisée** entre les différents sous-systèmes (QG, IoT, Simulation, Base de données).

---

## **2. Acteurs**

### **2.1. Opérateur QG**

Responsable de la supervision des incidents, du déclenchement des interventions, de l’affectation des ressources et du suivi de leur évolution.

### **2.2. Équipe terrain (Pompiers / Véhicule équipé Micro:Bit)**

Réalise les interventions, transmet la position en continu et envoie des messages d’état terrain.

### **2.3. Usager**

Personne signalant un incident ou impliquée dans l’incident.

### **2.4. Système de Simulation**

Génère des incidents, véhicules, états et événements permettant de tester l’application.

### **2.5. Système d’Information (Backend QG)**

Moteur de traitement, décision, filtrage, agrégation et stockage.

---

## **3. User Stories**

### **3.1. Équipe Terrain**

* **En tant que pompier**, je souhaite que **l’évolution de l’incident auquel je participe soit remontée automatiquement** afin d’éviter les transmissions manuelles imprécises.
* **En tant que pompier**, je souhaite que **l’assignation aux interventions soit intelligente**, basée sur ma position et mon statut, afin d’éviter les erreurs d’affectation.

### **3.2. Opérateur QG**

* **En tant qu’opérateur**, je souhaite **assister la victime** et disposer des informations nécessaires à sa prise en charge rapide.
* **En tant qu’opérateur**, je souhaite **avoir une visibilité globale sur les ressources disponibles**.
* **En tant qu’opérateur**, je souhaite pouvoir **déclencher, suivre et conclure les interventions**.

### **3.3. Usager**

* **En tant qu’usager**, je souhaite pouvoir **signaler un problème**.
* **En tant qu’usager**, je souhaite que **les secours interviennent rapidement**.
* **En tant qu’usager**, je veux **recevoir des conseils immédiats** de l’opérateur.
* **En tant que victime**, je veux **être transportée à l’hôpital si nécessaire**.

---

## **4. Description fonctionnelle par composant**

---

### **4.1. App QG – Interface Opérateur**

#### **4.1.1. Fonctionnalités principales**

1. **Affichage en temps réel**

   * Carte interactive présentant :

     * incidents en cours (type, localisation, gravité, timestamp)
     * interventions en cours (état, ressources affectées)
     * véhicules et équipes (position GPS, statut, disponibilité)
   * Mise à jour via **Server-Sent Events (SSE)**.

2. **Gestion des incidents**

   * Création manuelle d’un incident.
   * Réception automatique depuis la simulation.
   * Consultation de la fiche incident (historique, évolution, messages terrain).

3. **Gestion des interventions**

   * Création manuelle d’une intervention.
   * Proposition d’intervention automatique :

     * choix des véhicules optimaux (distance, disponibilité, capacités)
     * pré-affectation automatique
   * Validation ou annulation par un opérateur.

4. **Gestion des ressources**

   * Consultation des véhicules :

     * localisation
     * statut (disponible / en trajet / sur intervention / indisponible technique)
     * jauges (carburant simulé, équipements)

5. **Suivi des communications IoT**

   * Liste des messages reçus par radio :

     * coordonnées GPS
     * déclaration arrivée
     * signalement de fin d’intervention
     * demande de renfort
     * message d’information terrain

6. **Authentification**

   * Auth via Keycloak
   * Rôles : Opérateur, Administrateur, Simulation

---

### **4.2. Backend QG (REST API + Decision Engine)**

#### **4.2.1. Rôles**

Le backend centralise, structure et décide :

* traite les flux IoT entrants
* expose une API unique pour le Front et la Simulation
* orchestre le moteur décisionnel Java
* assure la cohérence des données (états, transitions)
* publie/consomme des messages RabbitMQ

#### **4.2.2. Fonctionnalités**

##### **Traitement des incidents**

* Validation des données entrantes (type, position, gravité)
* Affectation automatique (moteur décisionnel)
* Mise à jour en temps réel de l’état

##### **Traitement des interventions**

* Suivi des statuts :

  * créé
  * affecté
  * en route
  * sur place
  * terminé
* Transmission aux équipes terrain via RabbitMQ (si besoin)

##### **Gestion des véhicules**

* Mise à jour périodique des coordonnées
* Mise à jour des états terrain (radio)
* Transition automatique entre statuts

##### **Diffusion temps réel**

* SSE vers le front
* Webhooks internes si nécessaire

---

### **4.3. App Terrain (Micro:Bit)**

#### **4.3.1. Fonctionnalités côté véhicule**

##### **Envois automatiques**

* Transmission périodique GPS (toutes les X secondes)
* Transmission du statut (en déplacement / sur intervention)

##### **Envois manuels**

* Fin d’intervention
* Demande de renfort
* Déclaration d’arrivée sur site
* Message d’information terrain

##### **Retour QG (optionnel)**

* Notifications brèves (instructions de l’opérateur)

#### **4.3.2. Propriétés fonctionnelles**

* Tolérance à la perte de paquets
* Intégrité + identifiant unique du véhicule
* Non-répudiation simulation vs réel

---

### **4.4. Passerelle RF (Micro:Bit Centrale)**

#### **Rôle**

* Réception de toutes les trames radio
* Transmission via UART à l’API Python
* Validation minimale + horodatage
* Tolérance à la latence et pertes

---

### **4.5. Module de Simulation**

#### **4.5.1. Fonctionnalités**

##### **Simulation d’incidents**

* Génération pseudo-aléatoire :

  * type
  * localisation
  * gravité
  * timestamp
* Évolution dans le temps (diminution, aggravation, propagation)

##### **Simulation des véhicules**

* Déplacement sur carte (interpolation GPS)
* État (disponible, en mission, retour)
* Envoi vers la passerelle RF via UART

#### **4.5.2. Intégration avec l’API QG**

* Droits spécifiques (API Key Simulation)
* Génération d’incidents automatisée

---

## **5. Spécifications fonctionnelles détaillées**

### **5.1. Gestion des incidents**

| Action                  | Description                      | Acteur         |
| ----------------------- | -------------------------------- | -------------- |
| Création manuelle       | Formulaire dédié                 | Opérateur      |
| Création automatique    | Simulation ou IA                 | Système        |
| Modification            | Évolution, changement de gravité | Opérateur      |
| Affectation automatique | Calcul des ressources adaptées   | Backend + Java |
| Clôture                 | Incident terminé                 | Terrain → QG   |

#### **Règles**

* Un incident doit disposer :

  * ID, localisation, type, gravité, timestamp
  * 0..n interventions associées
* La gravité doit évoluer selon événements remontés

---

### **5.2. Gestion des interventions**

| État      | Déclencheur            |
| --------- | ---------------------- |
| Créée     | opérateur / système    |
| Affectée  | moteur décisionnel     |
| En route  | GPS → distance diminue |
| Sur place | déclarée par véhicule  |
| Clôturée  | terrain ou opérateur   |

#### **Règles métiers**

* Une intervention doit avoir **au moins un véhicule** affecté.
* Les véhicules doivent être **compatibles avec le type d’incident**.
* Le QG peut **valider ou refuser** la pré-assignation automatique.

---

### **5.3. Gestion des ressources (véhicules, équipes)**

* Chaque véhicule possède :

  * position GPS
  * vitesse (optionnelle)
  * statut opérationnel
  * historique des positions
* Le système doit pouvoir filtrer :

  * disponibles seulement
  * proches de l’incident
  * adaptés au type d’incident

---

### **5.4. Communication IoT**

#### **Messages obligatoires**

* `POS_UPDATE`
* `ARRIVED_ON_SITE`
* `END_OF_INTERVENTION`
* `REQUEST_BACKUP`
* `INFO`

#### **Validations**

* longueur OK
* signature ou ID valide
* timestamp

#### **Règles**

* Une trame invalide est rejetée et loggée.
* La passerelle ne modifie jamais le contenu.

---

## **6. Contraintes non fonctionnelles**

### **6.1. Disponibilité**

* Front + Backend doivent supporter un rafraîchissement en continu (SSE).
* L’IoT doit fonctionner même en cas de perte de 30% des paquets.

### **6.2. Sécurité**

* Keycloak obligatoire pour toute connexion.
* Chiffrement API REST (HTTPS).
* Règles OWASP appliquées.

### **6.3. Performance**

* Temps de propagation IoT → QG < **1 seconde** en conditions normales.
* Agrégation et décision < **500 ms**.

---

## **7. Hypothèses et limites**

* Le réseau radio est simulé (pas de réseau réel SIG).
* La simulation sert uniquement au test et ne doit pas modifier l’architecture QG.
* Pas de contraintes de production (scalabilité non exigée).
