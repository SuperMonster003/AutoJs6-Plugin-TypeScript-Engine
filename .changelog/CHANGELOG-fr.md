******

### Historique des versions

******

# v0.5.0

###### 2026/07/27

* `Indication` `compilerAvailable=false` reste l'etat faisant autorite dans v0.5.0 car R1 est incomplet; la matrice modern-provider API 24/25, le PSS du compiler service et du renderer, ainsi que la gestion accepted/cancel restent en attente
* `Correction` La destruction du service libere WebView, Worker, MessagePort, le client ServiceWorker, les requetes pending et les callbacks Handler avant de publier le close report et de reveiller les attentes; la course entre deadline et terminal publication est aussi supprimee
* `Correction` L'observation de `/proc` et des FD est liee a `(pid,starttime)`, echoue de maniere fermee sur une sortie shell indisponible ou partielle et evite la course UiAutomation finish pendant la sortie du process
* `Amélioration` Validation de 10 cold compiler processes et 100 hot prewarms sur chaque appareil Android 12 et Android 13; les FD du compiler service reviennent exactement au baseline sans staging archive conservee
* `Amélioration` Validation des etats none, Node-only, Ace-only et Node+Ace sous API 33 avec WebView 109; une porte statique interdit aux production source, manifest et direct dependencies de decouvrir ou dependre de Node/Ace

# v0.4.0

###### 2026/07/27

* `Indication` `compilerAvailable=false` reste l'etat faisant autorite dans v0.4.0: API 24 valide uniquement les portes Bundle/FD transport car son ancien WebView ne peut pas analyser le JavaScript original du compiler TypeScript 6.0.3; la matrice modern-provider API 24/25 reste en attente
* `Correction` Rejet des FD dans le raw Bundle transport avant deserialization et liberation de leurs copies de transport dans les chemins de rejet prewarm, compilation et cancellation
* `Correction` Restriction des tableaux de chaines dans les Bundle metadata au runtime type reel `String[]` et rejet des top-level null values, Parcelable arrays, Binder, Serializable et nested Bundle
* `Correction` Fusion de l'execution timeout recovery par epoch afin qu'un stale recovery ne puisse pas detruire une replacement session; la couverture deterministe de deux timeout consecutifs de 1 ms verifie la recuperation du probe en attente
* `Amélioration` Validation des portes de sortie reelle du renderer WebView sur Android 12 et Android 13: la premiere sortie recoit exactement un retry, la seconde echoue de maniere deterministe et un probe ordinaire ulterieur recupere
* `Amélioration` Stabilite des distribution/declarations fingerprints sur 10 redemarrages du process `:typescript_compiler` pour chaque appareil Android 12 et Android 13; API 24 a valide separement six portes Bundle/FD transport

# v0.3.0

###### 2026/07/27

* `Indication` `compilerAvailable=false` reste l'etat faisant autorite dans v0.3.0: le prewarm R1 execute uniquement le golden probe du compiler host restreint; la compilation de projet reste indisponible jusqu'a R2
* `Fonctionnalité` Mise en place d'un compiler host WebView/Worker dans le processus dedie `:typescript_compiler` avec une origine HTTPS privee pour les assets, une allowlist exacte, WebMessagePort et des capacites network, file, content, storage et window restreintes
* `Fonctionnalité` Le prewarm R1 charge le runtime TypeScript 6.0.3 reel et verifie sa runtime version, l'enum emit, un diagnostic syntaxique structure, une Source Map v3, les fingerprints de sortie fixes et l'absence de inline source content
* `Amélioration` Couverture de 100 hot probes par appareil Android 12 et Android 13 ainsi que de la reconstruction du Worker apres timeout, avec verification du minified release par un helper Android 13 autonome, tout en conservant toutes les capacites de compilation de projet a false
* `Dépendance` Ajout de AndroidX WebKit version 1.14.0

# v0.2.0

###### 2026/07/27

* `Indication` `compilerAvailable=false` reste l'etat faisant autorite dans v0.2.0: R1 prepare et valide uniquement la distribution du compilateur et le transport IPC/FD; la compilation de projet reste indisponible jusqu'a R2
* `Fonctionnalité` Mise en place d'un pipeline reproductible pour la distribution officielle TypeScript 6.0.3 qui verifie SHA-1, SHA-256 et SRI SHA-512, extrait de maniere sure une allowlist stricte des entrees tar et produit un `distribution.json` canonique ainsi que les fingerprints du compiler, des standard libraries, du generator et de la distribution
* `Fonctionnalité` Renforcement des controles de contenu des APK debug/release par le recalcul des octets empaquetes selon `distribution.json`, avec exactement un `lib/typescript.js` autorise et la verification des declarations, de la license et de la third-party notice
* `Fonctionnalité` L'instrumentation couvre la découverte et les capacités Binder du véritable service distant `:typescript_compiler`, ainsi que le rejet synchrone R0; elle vérifie que les FD détenus par l'appelant restent valides après le rejet et que les descripteurs non valides ou partagés ne déclenchent aucun callback pendant une courte fenêtre d'observation bornée
* `Dépendance` Ajout de AndroidX Test Runner version 1.7.0
* `Dépendance` Ajout de AndroidX Test JUnit Extension version 1.3.0

# v0.1.0

###### 2026/07/27

* `Indication` `compilerAvailable=false` indique que R0 expose uniquement la découverte du plugin et un contrat de compilation provisoire; TypeScript ne peut pas encore être compilé ni exécuté
* `Fonctionnalité` Création du projet de plugin frontal de compilation TypeScript autonome pour AutoJs6, verrouillage exact de TypeScript 6.0.3 et absence de dépendance au plugin Node.js Runtime
* `Fonctionnalité` Découverte du plugin et négociation des capacités du compilateur via les services `org.autojs.plugin.INFO` et `org.autojs.plugin.typescript.COMPILER` protégés par `org.autojs.permission.PLUGIN`
* `Fonctionnalité` Exécution du service de compilation dans le processus dédié `:typescript_compiler`, avec une enveloppe Binder provisoire transportant de petites métadonnées `Bundle` et les archives d'entrée/sortie via `ParcelFileDescriptor`
* `Fonctionnalité` Ajout de contrôles automatisés R0 pour le contrat API, l'admission/le rejet, le verrou de distribution du compilateur, l'indépendance vis-à-vis de Node Runtime, Lint et le contenu de l'APK universel
