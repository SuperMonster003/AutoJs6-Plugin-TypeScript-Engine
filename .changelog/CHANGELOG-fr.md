******

### Historique des versions

******

# v0.6.1

###### 2026/09/12

* `Amélioration` Vérification à la compilation de l'alignement des pages de 16 KB des bibliothèques natives 64 bits, avec contrôle du contrat manifest et rapports JSON
* `Amélioration` Utiliser le plugin de plateforme Gradle commun pour choisir les versions des outils de compilation, supprimer les données de compatibilité locales et aligner les contrôles de maintenance

# v0.6.0

###### 2026/08/29

* `Indication` La compilation de projets est maintenant activee pour AutoJs6 6.8.0 Alpha7 (build 5253) ou version ulterieure; les profils Node exigent aussi le plugin Node.js Runtime, contrairement aux profils Rhino
* `Indication` La compilation et l'execution utilisent un unique snapshot exact du demarrage; les packages npm bare/scoped sont resolus a la compilation puis charges au runtime par Rhino CommonJS ou le plugin Node.js Runtime
* `Indication` Android 7.0 / API 24 exige Chrome/WebView 119 ou une version ulterieure; les anciens providers livres avec API 24/25 ne sont pas pris en charge et echouent de maniere fermee
* `Fonctionnalité` Les projets Node TypeScript adossés au compilateur peuvent désormais compiler à la demande les fichiers `.ts/.mts/.cts` créés pendant l'exécution: Node transfère la source via un PFD borné par le nombre d'octets et SHA-256, l'hôte réutilise la session de projet résidente, les diagnostics de types et la remise en correspondance des piles par Source Map sont conservés, et les sorties de chemin, liens symboliques et cibles ambiguës restent refusés
* `Fonctionnalité` Ajout de la révision 2 de la politique de couche de dépendances et de la révision 4 de la politique du resolver: les archives de l'hôte et le Worker de production rejettent de façon déterministe `.node`, `binding.gyp`, `gypfile` et les hooks du cycle d'installation avec des erreurs native/install-script stables et des conseils JavaScript pur/WASM
* `Fonctionnalité` Ajout d'une matrice d'acceptation hors ligne avec les paquets réels dayjs 1.11.13, lodash 4.17.21, zod 4.1.5, uuid 13.0.0 et nanoid 5.1.5 dans les profils Rhino et Node ; la révision 3 de la politique du resolver bascule désormais du JavaScript d'exécution vers les `@types` installés sans remplacer les déclarations fournies
* `Fonctionnalité` Synchronisation des types de packages entre le compilateur et Ace: `types`/`typings`, `typesVersions` de TypeScript 6, imports de declarations imbriques et `@types` installes utilisent maintenant une meme couche gelee; completion dayjs, hover et diagnostic strict TS2322 sont identiques dans les profils Rhino et Node
* `Fonctionnalité` Activation conjointe de `compilerAvailable`, `projectCompilation`, `semanticDiagnostics` et `sourceMapV3` (capability mask 15), avec compilation TypeScript Program complete, diagnostics structures et sorties Source Map v3 externes associees
* `Fonctionnalité` Ajout de la compilation publique de projets Rhino `.ts/.tsx` avec chemins UTF-8 et lowering UI TSX natif AutoJs6; controlled tsconfig revision 3 s'applique a Rhino et NodeNext avec 20 options booleennes et `target`/`lib` limites par profile, tout en conservant la compatibilite des requetes Rhino revision 2
* `Fonctionnalité` Ajout de la compilation publique NodeNext revision 2 pour `.mts/.cts` et `.ts` limite au projet, avec emission `.mjs/.cjs/.js` selon le type de source et le package type
* `Fonctionnalité` Integration du compilateur dans l'execution AutoJs6, avec diagnostics cliquables, mapping des piles runtime, projets multifichiers, mapping dynamic import, entrees Tasker/planifiees et flux en memoire des projets chiffres d'APK Builder; les packages contiennent le JS et les maps compiles sans sources TS
* `Fonctionnalité` Alignement des declarations de l'editeur Ace et du comportement TypeScript 6.0.3 sur la compilation avant execution
* `Fonctionnalité` Controlled tsconfig revision 4 ajoute des alias `baseUrl`/`paths` internes au projet pour Rhino et NodeNext; la resolution reste dans le snapshot fige, les specifiers emis deviennent des modules relatifs runtime explicites et les identites revision 2/3 restent exactement compatibles
* `Fonctionnalité` Activation du top-level `await` pour les projets Node ESM `.mts` et `.ts` avec `type: module` a la racine; la configuration controlled ES2022 emet `.mjs/.js` avec des Source Maps associees pour execution directe par le plugin Node.js Runtime
* `Fonctionnalité` Ajout de limites package controlees de workspace Node sous `packages/<name>` et `packages/@scope/<name>`; les `.ts` d'un meme projet peuvent melanger ESM et CommonJS selon le `package.json.type` le plus proche, tandis que `scripts` reste interdit
* `Fonctionnalité` Les builds Node Runtime compatibles executent maintenant les entrees ESM et dynamic `import()` via le linker natif V8, en preservant les dependances cycliques, les exports mutables et les live bindings reexportes; un graphe `.mts` de quatre modules passe de `initial=1` a `updated=42`
* `Fonctionnalité` Ajout d'une couche de dependances `node_modules` read-only a quota separe et de la resolution bare/scoped, avec subpath `exports`, conditions `types`/`import`/`require`, fallbacks `types`/`typings`/`module`/`main`/`index` et validation Program complete de dayjs + zod; cette meme couche exacte atteint maintenant le runtime, ou Rhino choisit les entrees CommonJS/JSON `require` avec des erreurs ESM-only stables et Node execute les packages ESM/CJS/dual via un workspace overlay prive qui ne reecrit jamais les dependances
* `Amélioration` Ajout de la revision 1 opt-in de session projet : des identites derivees par le service pour chaque caller/project conservent un Worker realm borne et reutilisent les SourceFiles versionnes par empreinte, le builder incremental et les sorties inchangees ; les requetes legacy restent entierement froides et la pression memoire supprime toutes les sessions
* `Amélioration` Ajout d'un cache deterministe reutilisable entre executions; les hits mesures prennent 18-63 ms et le batching borne des entrees reduit le cout d'ingress de 40%-57% sur les appareils observes
* `Amélioration` Relevement des limites de sortie ordinaires a 512 artifacts, 16 MiB par JavaScript ou map et 64 MiB au total, ainsi que de l'entree caller ordinaire en revision 3 a 4096 files, 8 MiB par file et 64 MiB au total; les declarations integrees restent hors quota caller et les hosts revision 2 conservent le chemin compatible 512 files / 8 MiB
* `Amélioration` Nouvelle reduction de l'ingress a froid en regroupant jusqu'a six frames intermediaires tout en limitant les payload chunks a quatre; le fixture fixe de 109 files passe de 97 a 68 envelopes et les medianes compiler/ingress s'ameliorent sur quatre combinaisons Android/WebView

# v0.5.0

###### 2026/07/27

* `Indication` Toutes les portes de sortie de R1 sont désormais validées, y compris la matrice d'exécution du modern-provider Chrome 119 sur API 24/25 ; `compilerAvailable=false` reste l'état faisant autorité, car la compilation de projets en production relève de R2
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
