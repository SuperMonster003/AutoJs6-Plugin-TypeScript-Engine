******

### Historial de versiones

******

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
