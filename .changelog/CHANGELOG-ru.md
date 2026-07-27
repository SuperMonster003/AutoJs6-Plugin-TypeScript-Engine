******

### История выпусков

******

# v0.5.0

###### 2026/07/27

* `Примечание` `compilerAvailable=false` остается авторитетным состоянием в v0.5.0, поскольку R1 не завершен; матрица modern-provider для API 24/25, PSS compiler service и renderer, а также обработка accepted/cancel остаются в работе
* `Исправление` При service destruction WebView, Worker, MessagePort, ServiceWorker client, pending requests и Handler callbacks освобождаются до публикации close report и пробуждения waiters; также устранена гонка между deadline и terminal publication
* `Исправление` Наблюдение за `/proc` и FD привязано к `(pid,starttime)`, недоступный или частичный вывод shell приводит к fail closed, а при process exit polling исключена гонка UiAutomation finish
* `Улучшение` На каждом устройстве Android 12 и Android 13 проверены 10 cold compiler processes и 100 hot prewarms; FD compiler service точно возвращаются к baseline без сохраненных staging archives
* `Улучшение` На API 33 с WebView 109 пройдены состояния none, Node-only, Ace-only и Node+Ace; статическая проверка запрещает production source, manifest и direct dependencies обнаруживать Node/Ace или зависеть от них

# v0.4.0

###### 2026/07/27

* `Примечание` `compilerAvailable=false` остается авторитетным состоянием в v0.4.0: API 24 проходит только проверки Bundle/FD transport, поскольку старый WebView не может разобрать исходный JavaScript compiler TypeScript 6.0.3; матрица modern-provider для API 24/25 остается незавершенной
* `Исправление` FD в raw Bundle transport отклоняются до deserialization, а их transport copies освобождаются в путях отказа prewarm, compilation и cancellation
* `Исправление` Строковые массивы в Bundle metadata ограничены фактическим runtime type `String[]`, а top-level null values, Parcelable arrays, Binder, Serializable и nested Bundle отклоняются
* `Исправление` Выполнение timeout recovery объединяется по epoch, чтобы stale recovery не мог уничтожить replacement session; детерминированная проверка двух последовательных timeout по 1 ms подтверждает восстановление queued probe
* `Улучшение` На Android 12 и Android 13 пройдены проверки реального завершения WebView renderer: первое завершение получает ровно один retry, второе завершается предсказуемой ошибкой, а следующий обычный probe восстанавливается
* `Улучшение` Distribution/declarations fingerprints стабильны после 10 перезапусков процесса `:typescript_compiler` на каждом устройстве Android 12 и Android 13; API 24 отдельно прошел шесть проверок Bundle/FD transport

# v0.3.0

###### 2026/07/27

* `Примечание` `compilerAvailable=false` остается авторитетным состоянием в v0.3.0: prewarm R1 выполняет только golden probe ограниченного compiler host; компиляция проектов недоступна до R2
* `Новое` Реализован WebView/Worker compiler host в выделенном процессе `:typescript_compiler` с приватным HTTPS asset origin, точным allowlist ресурсов, WebMessagePort и ограниченными возможностями network, file, content, storage и window
* `Новое` Prewarm R1 загружает реальный runtime TypeScript 6.0.3 и проверяет runtime version, enum emit, структурированный синтаксический diagnostic, Source Map v3, фиксированные fingerprints вывода и отсутствие inline source content
* `Улучшение` На каждом устройстве Android 12 и Android 13 выполнено 100 hot probes и проверено восстановление через перестроение Worker после timeout, а отдельный helper на Android 13 проверил minified release при сохранении всех возможностей компиляции проектов в состоянии false
* `Зависимость` Добавлен AndroidX WebKit версии 1.14.0

# v0.2.0

###### 2026/07/27

* `Примечание` `compilerAvailable=false` остается авторитетным состоянием в v0.2.0: R1 только подготавливает и проверяет compiler distribution и IPC/FD transport; компиляция проектов недоступна до R2
* `Новое` Реализован воспроизводимый pipeline официального дистрибутива TypeScript 6.0.3 с проверкой SHA-1, SHA-256 и SRI SHA-512, безопасным извлечением строгого allowlist для tar entries и генерацией canonical `distribution.json`, а также fingerprints для compiler, standard libraries, generator и всего дистрибутива
* `Новое` Усилены проверки содержимого debug/release APK: фактически упакованные bytes повторно проверяются по `distribution.json`, допускается ровно один allowlisted `lib/typescript.js`, а declarations, license и third-party notice проверяются на полноту
* `Новое` Инструментальные тесты охватывают обнаружение и возможности реальной удаленной Binder-службы `:typescript_compiler`, а также синхронный отказ R0; подтверждено, что принадлежащие вызывающей стороне FD остаются действительными после отказа, а недопустимые или общие дескрипторы не вызывают callback в пределах короткого ограниченного окна наблюдения
* `Зависимость` Добавлен AndroidX Test Runner версии 1.7.0
* `Зависимость` Добавлен AndroidX Test JUnit Extension версии 1.3.0

# v0.1.0

###### 2026/07/27

* `Примечание` `compilerAvailable=false` означает, что R0 предоставляет только обнаружение плагина и предварительный контракт компиляции; компиляция и выполнение TypeScript пока недоступны
* `Новое` Создан независимый проект плагина фронтенда компилятора TypeScript для AutoJs6, точно закреплена версия TypeScript 6.0.3 и сохранено отсутствие зависимостей от плагина Node.js Runtime
* `Новое` Добавлены обнаружение плагина и согласование возможностей компилятора через службы `org.autojs.plugin.INFO` и `org.autojs.plugin.typescript.COMPILER`, защищенные разрешением `org.autojs.permission.PLUGIN`
* `Новое` Служба компилятора работает в выделенном процессе `:typescript_compiler`, а предварительный Binder envelope передает небольшие метаданные `Bundle` и входные/выходные архивы через `ParcelFileDescriptor`
* `Новое` Добавлены автоматические проверки R0 для контракта API, допуска/отклонения, блокировки дистрибутива компилятора, независимости от Node Runtime, Lint и содержимого универсального APK
