******

### Historial de versiones

******

# v0.6.1

###### 2026/09/12

* `Mejora` Verificación de compilación de la alineación de páginas de 16 KB en bibliotecas nativas de 64 bits, con controles del contrato manifest e informes JSON
* `Mejora` Usar el plugin compartido de plataforma Gradle para seleccionar las versiones de compilación, eliminar los datos locales de compatibilidad y alinear las comprobaciones de mantenimiento

# v0.6.0

###### 2026/08/29

* `Aviso` La compilacion de proyectos ya esta habilitada para AutoJs6 6.8.0 Alpha7 (build 5253) o posterior; los perfiles Node tambien requieren el plugin Node.js Runtime, mientras que los perfiles Rhino no
* `Aviso` La compilacion y la ejecucion usan un unico snapshot exacto del inicio; los paquetes npm bare/scoped se resuelven al compilar y se cargan en runtime mediante Rhino CommonJS o el plugin Node.js Runtime
* `Aviso` Android 7.0 / API 24 requiere Chrome/WebView 119 o posterior; los proveedores antiguos incluidos con API 24/25 no son compatibles y fallan de forma cerrada
* `Función` Los proyectos Node TypeScript respaldados por el compilador ahora pueden compilar bajo demanda archivos `.ts/.mts/.cts` creados durante la ejecución: Node transfiere la fuente mediante un PFD acotado por bytes y SHA-256, el host reutiliza la sesión residente del proyecto, se conservan los diagnósticos de tipos y el mapeo de pila con Source Map, y siguen rechazados el escape de ruta, los enlaces simbólicos y los destinos ambiguos
* `Función` Se añadieron la revisión 2 de la política de capa de dependencias y la revisión 4 de la política del resolver: los archivos del host y el Worker de producción rechazan de forma determinista `.node`, `binding.gyp`, `gypfile` y hooks del ciclo de instalación con errores native/install-script estables y orientación hacia JavaScript puro/WASM
* `Función` Se añadió una matriz de aceptación sin conexión con paquetes reales para dayjs 1.11.13, lodash 4.17.21, zod 4.1.5, uuid 13.0.0 y nanoid 5.1.5 en los perfiles Rhino y Node; la revisión 3 de la política del resolver ahora recurre del JavaScript de ejecución a los `@types` instalados sin reemplazar las declaraciones incluidas
* `Función` Sincronizados los tipos de paquetes entre el compilador y Ace: `types`/`typings`, `typesVersions` de TypeScript 6, imports de declaraciones anidadas y `@types` instalados usan ahora una misma capa congelada; el completado de dayjs, hover y el diagnostico strict TS2322 coinciden en los perfiles Rhino y Node
* `Función` Se habilitaron conjuntamente `compilerAvailable`, `projectCompilation`, `semanticDiagnostics` y `sourceMapV3` (capability mask 15), con compilacion TypeScript Program completa, diagnosticos estructurados y salidas externas Source Map v3 emparejadas
* `Función` Se agrego compilacion publica de proyectos Rhino `.ts/.tsx` con rutas UTF-8 y lowering de UI TSX nativo de AutoJs6; controlled tsconfig revision 3 se aplica a Rhino y NodeNext con 20 opciones booleanas y `target`/`lib` limitados por profile, manteniendo compatibles las solicitudes Rhino revision 2
* `Función` Se agrego compilacion publica NodeNext revision 2 para `.mts/.cts` y `.ts` dentro del proyecto, emitiendo `.mjs/.cjs/.js` segun el tipo de fuente y el package type
* `Función` El compilador se integro en la ejecucion de AutoJs6 con diagnosticos clicables, mapeo de stacks runtime, proyectos de varios archivos, mapeo dynamic import, entradas de Tasker/planificadas y el flujo en memoria de proyectos cifrados de APK Builder; los paquetes contienen JS/maps compilados y no incluyen fuentes TS
* `Función` Se alinearon las declaraciones del editor Ace y el comportamiento de TypeScript 6.0.3 con la compilacion previa a la ejecucion
* `Función` Controlled tsconfig revision 4 agrega aliases `baseUrl`/`paths` locales al proyecto para Rhino y NodeNext; la resolucion queda dentro del snapshot congelado, los specifiers emitidos se reescriben como modulos relativos de runtime y las identidades revision 2/3 conservan compatibilidad exacta
* `Función` Se habilito top-level `await` para proyectos Node ESM `.mts` y `.ts` con `type: module` en la raiz; la configuracion controlled ES2022 emite `.mjs/.js` con Source Maps emparejados para ejecucion directa mediante el plugin Node.js Runtime
* `Función` Se agregaron limites package controlados de workspace Node en `packages/<name>` y `packages/@scope/<name>`; los `.ts` de un mismo proyecto pueden mezclar ESM y CommonJS segun el `package.json.type` mas cercano, mientras `scripts` sigue prohibido
* `Función` Las compilaciones compatibles de Node Runtime ahora ejecutan entradas ESM y dynamic `import()` mediante el linker nativo de V8, conservando dependencias ciclicas, exports mutables y live bindings reexportados; un grafo `.mts` de cuatro modulos pasa de `initial=1` a `updated=42`
* `Función` Se anadio una capa de dependencias `node_modules` de solo lectura con cuota independiente y resolucion bare/scoped, incluidos subpath `exports`, condiciones `types`/`import`/`require`, fallbacks `types`/`typings`/`module`/`main`/`index` y aceptacion Program completa con dayjs + zod; la misma capa exacta llega ahora al runtime, donde Rhino elige entradas CommonJS/JSON `require` con errores estables para ESM-only y Node ejecuta paquetes ESM/CJS/dual mediante un workspace overlay privado que nunca escribe cambios en las dependencias
* `Mejora` Se añadió la revision 1 opt-in de sesión de proyecto: identidades derivadas por el servicio para cada caller/project conservan un Worker realm acotado y reutilizan SourceFiles versionados por resumen, el builder incremental y salidas sin cambios; las solicitudes legacy siguen totalmente frías y la presión de memoria descarta todas las sesiones
* `Mejora` Se agrego reutilizacion determinista de cache entre ejecuciones; los hits medidos tardan 18-63 ms y el batching limitado de entradas redujo el costo de ingress entre 40% y 57% en los dispositivos observados
* `Mejora` Los limites ordinarios de salida aumentaron a 512 artifacts, 16 MiB por JavaScript o map y 64 MiB en total, y la entrada caller ordinaria subio a revision 3 con 4096 files, 8 MiB por file y 64 MiB en total; las declaraciones integradas quedan fuera de la cuota caller y los hosts revision 2 conservan la ruta compatible de 512 files / 8 MiB
* `Mejora` Se redujo aun mas el ingress en frio agrupando hasta seis frames intermedios y manteniendo un maximo de cuatro payload chunks; el fixture fijo de 109 files bajo de 97 a 68 envelopes y mejoraron las medianas de compiler e ingress en cuatro combinaciones Android/WebView

# v0.5.0

###### 2026/07/27

* `Aviso` Todas las puertas de salida de R1 ya están completas, incluida la matriz de runtime del modern-provider Chrome 119 en API 24/25; `compilerAvailable=false` sigue siendo el estado autoritativo porque la compilación de proyectos en producción pertenece a R2
* `Corrección` La destruccion del servicio libera WebView, Worker, MessagePort, el cliente ServiceWorker, las solicitudes pending y los callbacks de Handler antes de publicar el close report y despertar a los waiters; tambien elimina la carrera entre deadline y terminal publication
* `Corrección` La observacion de `/proc` y FD queda ligada a `(pid,starttime)`, falla de forma cerrada ante una salida shell no disponible o parcial y evita la carrera de UiAutomation finish durante la salida del proceso
* `Mejora` Validacion de 10 cold compiler processes y 100 hot prewarms en cada dispositivo Android 12 y Android 13; los FD del compiler service vuelven exactamente al baseline sin staging archives retenidos
* `Mejora` Superacion de los estados none, Node-only, Ace-only y Node+Ace en API 33 con WebView 109; una puerta estatica impide que production source, manifest y direct dependencies descubran o dependan de Node/Ace

# v0.4.0

###### 2026/07/27

* `Aviso` `compilerAvailable=false` sigue siendo el estado autoritativo en v0.4.0: API 24 solo supera las puertas Bundle/FD transport porque su WebView antiguo no puede analizar el JavaScript original del compiler TypeScript 6.0.3; la matriz modern-provider de API 24/25 sigue pendiente
* `Corrección` Rechazo de FD en el raw Bundle transport antes de la deserializacion y liberacion de sus copias de transporte en las rutas de rechazo de prewarm, compilation y cancellation
* `Corrección` Restriccion de los arrays de strings en Bundle metadata al runtime type real `String[]` y rechazo de top-level null values, Parcelable arrays, Binder, Serializable y nested Bundle
* `Corrección` Combinacion de la ejecucion timeout recovery por epoch para que un stale recovery no destruya una replacement session; la cobertura determinista de dos timeout consecutivos de 1 ms verifica la recuperacion del probe en espera
* `Mejora` Superacion de las puertas de salida real del renderer WebView en Android 12 y Android 13: la primera salida recibe exactamente un retry, la segunda falla de forma determinista y un probe ordinario posterior se recupera
* `Mejora` Estabilidad de los distribution/declarations fingerprints durante 10 reinicios del proceso `:typescript_compiler` en cada dispositivo Android 12 y Android 13; API 24 supero por separado seis puertas Bundle/FD transport

# v0.3.0

###### 2026/07/27

* `Aviso` `compilerAvailable=false` sigue siendo el estado autoritativo en v0.3.0: el prewarm de R1 solo ejecuta el golden probe del compiler host restringido; la compilacion de proyectos no estara disponible hasta R2
* `Función` Se establecio un compiler host WebView/Worker en el proceso dedicado `:typescript_compiler` con origen HTTPS privado para assets, allowlist exacta, WebMessagePort y capacidades restringidas de network, file, content, storage y window
* `Función` El prewarm de R1 carga el runtime real de TypeScript 6.0.3 y verifica su runtime version, enum emit, diagnostico de sintaxis estructurado, Source Map v3, fingerprints de salida fijos y ausencia de inline source content
* `Mejora` Se cubren 100 hot probes por dispositivo Android 12 y Android 13 junto con la recuperacion mediante reconstruccion del Worker tras un timeout, y se verifica el minified release con un helper independiente en Android 13, manteniendo en false todas las capacidades de compilacion de proyectos
* `Dependencia` Se agrega AndroidX WebKit version 1.14.0

# v0.2.0

###### 2026/07/27

* `Aviso` `compilerAvailable=false` sigue siendo el estado autoritativo en v0.2.0: R1 solo prepara y valida la distribucion del compilador y el transporte IPC/FD; la compilacion de proyectos no estara disponible hasta R2
* `Función` Se establecio una canalizacion reproducible para la distribucion oficial de TypeScript 6.0.3 que verifica SHA-1, SHA-256 y SRI SHA-512, extrae de forma segura una allowlist estricta de entradas tar y genera un `distribution.json` canonico junto con los fingerprints del compiler, las standard libraries, el generator y la distribucion
* `Función` Se reforzaron los controles de contenido de los APK debug/release al recalcular los bytes empaquetados segun `distribution.json`, exigir exactamente un `lib/typescript.js` permitido y verificar las declarations, la license y el third-party notice
* `Función` La instrumentación cubre el descubrimiento y las capacidades Binder del servicio remoto real `:typescript_compiler`, junto con el rechazo síncrono R0; verifica que los FD propiedad del llamador sigan siendo válidos después del rechazo y que los descriptores no válidos o compartidos no activen ningún callback durante una ventana de observación breve y acotada
* `Dependencia` Se agrega AndroidX Test Runner version 1.7.0
* `Dependencia` Se agrega AndroidX Test JUnit Extension version 1.3.0

# v0.1.0

###### 2026/07/27

* `Aviso` `compilerAvailable=false` indica que R0 solo expone el descubrimiento del plugin y un contrato de compilación provisional; todavía no puede compilar ni ejecutar TypeScript
* `Función` Se estableció el proyecto independiente del plugin frontal de compilación TypeScript para AutoJs6, se fijó TypeScript 6.0.3 y se mantuvo sin dependencias del plugin Node.js Runtime
* `Función` Se proporcionó el descubrimiento del plugin y la negociación de capacidades del compilador mediante los servicios `org.autojs.plugin.INFO` y `org.autojs.plugin.typescript.COMPILER` protegidos por `org.autojs.permission.PLUGIN`
* `Función` El servicio de compilación se ejecuta en el proceso dedicado `:typescript_compiler`, con un sobre Binder provisional que transporta metadatos `Bundle` pequeños y archivos de entrada/salida mediante `ParcelFileDescriptor`
* `Función` Se añadieron controles automatizados R0 para el contrato API, la admisión/el rechazo, el bloqueo de distribución del compilador, la independencia de Node Runtime, Lint y el contenido del APK universal
