******

### Historique des versions

******

# v0.1.0

###### 2026/07/27

* `Indication` `compilerAvailable=false` indique que R0 expose uniquement la découverte du plugin et un contrat de compilation provisoire; TypeScript ne peut pas encore être compilé ni exécuté
* `Fonctionnalité` Création du projet de plugin frontal de compilation TypeScript autonome pour AutoJs6, verrouillage exact de TypeScript 6.0.3 et absence de dépendance au plugin Node.js Runtime
* `Fonctionnalité` Découverte du plugin et négociation des capacités du compilateur via les services `org.autojs.plugin.INFO` et `org.autojs.plugin.typescript.COMPILER` protégés par `org.autojs.permission.PLUGIN`
* `Fonctionnalité` Exécution du service de compilation dans le processus dédié `:typescript_compiler`, avec une enveloppe Binder provisoire transportant de petites métadonnées `Bundle` et les archives d'entrée/sortie via `ParcelFileDescriptor`
* `Fonctionnalité` Ajout de contrôles automatisés R0 pour le contrat API, l'admission/le rejet, le verrou de distribution du compilateur, l'indépendance vis-à-vis de Node Runtime, Lint et le contenu de l'APK universel
