<div align="center">

<img src="docs/readme/hero.svg" width="100%" alt="Исходники ИТД — Android-клиент итд.com на Kotlin и Jetpack Compose">

<br>

<a href="https://github.com/D1ma-coder/ITD-APK-Source/releases"><img src="https://img.shields.io/github/v/release/D1ma-coder/ITD-APK-Source?label=%D1%80%D0%B5%D0%BB%D0%B8%D0%B7&color=2979FF&style=for-the-badge" alt="Релиз"></a>
<img src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin 2.2">
<img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
<img src="https://img.shields.io/badge/Android-12%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 12+">
<a href="https://github.com/D1ma-coder/ITD-APK-Source/fork"><img src="https://img.shields.io/github/forks/D1ma-coder/ITD-APK-Source?label=%D1%84%D0%BE%D1%80%D0%BA%D0%B8&color=8B5CF6&style=for-the-badge" alt="Форки"></a>

<br><br>

<b>Исходный код Android-приложения <a href="https://итд.com">ИТД</a>, максимально близкий к официальному.</b><br>
<sub>Форкните, допишите немного кода — и у вас свой клиент ИТД.</sub>

<br><br>

<a href="#-зачем-это-нужно">Зачем</a> &nbsp;•&nbsp;
<a href="#-как-сделано">Как сделано</a> &nbsp;•&nbsp;
<a href="#-что-внутри">Что внутри</a> &nbsp;•&nbsp;
<a href="#-как-сделать-свой-мод">Свой мод</a> &nbsp;•&nbsp;
<a href="#-сборка">Сборка</a> &nbsp;•&nbsp;
<a href="#-что-дальше">Что дальше</a>

</div>

<br>

## 📌 О проекте

> [!NOTE]
> Сделал репозиторий, где постарался максимально точно повторить исходники официального приложения ИТД. Проект собирается в Android Studio и работает с тем же сервером, что и оригинал.

<table>
<tr>
<td width="33%" align="center">
<h3>🧬</h3>
<b>Функционально идентичен</b><br>
<sub>те же экраны, запросы к API и поведение, что у версии 1.1.0</sub>
</td>
<td width="33%" align="center">
<h3>🧩</h3>
<b>Готов к модам</b><br>
<sub>понятная структура пакетов, обычный Gradle-проект без магии</sub>
</td>
<td width="33%" align="center">
<h3>🔄</h3>
<b>Следует за обновлениями</b><br>
<sub>каждая версия ИТД — отдельный релиз с исходниками</sub>
</td>
</tr>
</table>

## 🎯 Зачем это нужно

Чтобы вы могли легко **сделать форк** проекта и, добавив немного кода, **сделать мод** на приложение — по сути, свой клиент ИТД на основе официального.

<table>
<tr>
<td>🎨 Своя тема и оформление</td>
<td>🧰 Новые функции и твики</td>
<td>🔕 Свои фильтры ленты</td>
</tr>
<tr>
<td>📊 Расширенная статистика</td>
<td>🌐 Эксперименты с API</td>
<td>📚 Изучение устройства приложения</td>
</tr>
</table>

## 🛠 Как сделано

<img src="docs/readme/pipeline.svg" width="100%" alt="APK → декомпиляция → перенос → восстановление с ИИ → проект">

1. Взял **последнюю версию** ИТД.
2. **Декомпилировал** её.
3. Большую часть **необфусцированных файлов перенёс напрямую** — модели данных, `MainActivity`, `CameraActivity`, push-сервис, ресурсы, иконки, ассеты.
4. **Обфусцированные файлы восстановлены и приведены в порядок с помощью ИИ** — с сохранением функциональной идентичности: тот же API, те же тексты, та же логика.

<details>
<summary><b>🔬 Как проверялась идентичность</b></summary>
<br>

- Все запросы к API (пути, параметры, заголовки, формат JSON) сверены с оригиналом.
- Модели данных проверяются тестом контрактов: имена полей, типы и значения по умолчанию совпадают с оригиналом.
- Тексты интерфейса сравнивались построчно: в проекте есть подавляющее большинство строк оригинала.
- Release-сборка с R8 запускается так же, как оригинальное приложение.

</details>

## 📦 Что внутри

<table>
<tr>
<td valign="top" width="50%">

**📰 Лента и посты**
- Вкладки «Для вас», «Подписки», «Лента кланов», поиск
- Посты с фото, видео, опросами, репостами
- Комментарии, ответы, голосовые сообщения
- Редактор с форматированием и ссылками
- Живое обновление лайков и просмотров

**👤 Профиль и люди**
- Профиль, стена, «Понравилось», подписчики
- Редактирование профиля, пины, баннер-рисовалка
- Блокировки и жалобы

</td>
<td valign="top" width="50%">

**🔔 Уведомления**
- Push через Firebase и поток в реальном времени (SSE)
- Подписка в ответ прямо из уведомлений

**⚙️ Аккаунт и безопасность**
- Вход, регистрация, восстановление пароля, капча
- Вход на компьютере по QR-коду
- Активные сессии, приватность, подписка «ИТД НУКСТА»

**🎃 Сезонный ивент «Хеллоуин»**
- Призраки в постах, посты-оборотни, ночные конфеты
- Портал события, звуки и уведомления

</td>
</tr>
</table>

### 🧱 Стек

<p>
<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin">
<img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Compose">
<img src="https://img.shields.io/badge/Material%203-757575?style=flat-square&logo=materialdesign&logoColor=white" alt="Material 3">
<img src="https://img.shields.io/badge/Ktor%20%2B%20OkHttp-087CFA?style=flat-square&logo=ktor&logoColor=white" alt="Ktor">
<img src="https://img.shields.io/badge/kotlinx.serialization-7F52FF?style=flat-square" alt="kotlinx.serialization">
<img src="https://img.shields.io/badge/Decompose-0095D5?style=flat-square" alt="Decompose">
<img src="https://img.shields.io/badge/Coil-FF6F00?style=flat-square" alt="Coil">
<img src="https://img.shields.io/badge/Media3-34A853?style=flat-square" alt="Media3">
<img src="https://img.shields.io/badge/CameraX%20%2B%20ML%20Kit-4285F4?style=flat-square&logo=google&logoColor=white" alt="CameraX">
<img src="https://img.shields.io/badge/MapLibre-396CB2?style=flat-square" alt="MapLibre">
<img src="https://img.shields.io/badge/Firebase%20Messaging-FFCA28?style=flat-square&logo=firebase&logoColor=black" alt="Firebase">
<img src="https://img.shields.io/badge/Sentry-362D59?style=flat-square&logo=sentry&logoColor=white" alt="Sentry">
</p>

### 🗂 Структура

```text
app/src/main/java/com/itd/app/
├── data/
│   ├── analytics/     аналитика просмотров и взаимодействий
│   ├── local/         хранение сессии
│   ├── model/         модели API
│   ├── remote/        HTTP-клиент, API, push, поток уведомлений
│   └── repository/    репозитории и общие обновления постов
├── decompose/         стек оверлеев (пост, профиль, портал, баннер)
├── di/                контейнер зависимостей
├── halloween/         сезонный ивент
├── ui/
│   ├── components/    карточка поста, WebView, общие элементы
│   ├── navigation/    оболочка приложения и нижнее меню
│   ├── screens/       экраны
│   └── theme/         цвета и типографика
└── util/              логирование, время, диагностика
```

## 🧪 Как сделать свой мод

```bash
# 1. Сделайте форк кнопкой Fork вверху страницы и склонируйте его
git clone https://github.com/<ваш-логин>/ITD-APK-Source.git
cd ITD-APK-Source

# 2. Поменяйте applicationId, чтобы мод ставился рядом с официальным приложением
#    app/build.gradle.kts → defaultConfig { applicationId = "com.example.itdmod" }

# 3. Пишите код и собирайте
./gradlew :app:assembleDebug
```

> [!TIP]
> С отдельным `applicationId` мод не конфликтует с официальным ИТД из магазина и ставится рядом с ним.

<details>
<summary><b>💡 С чего начать</b></summary>
<br>

| Хочу изменить | Где искать |
|---|---|
| Цвета и тему | `ui/theme/Theme.kt` |
| Карточку поста | `ui/components/PostCard.kt` |
| Нижнее меню | `ui/navigation/ItdBottomBar.kt` |
| Ленту | `ui/screens/feed/` |
| Запросы к серверу | `data/remote/` |
| Обработку уведомлений | `data/remote/PushNotificationService.kt` |

</details>

## 🔨 Сборка

**Нужно:** актуальная Android Studio, JDK 17–21, Android SDK 36.

```bash
./gradlew :app:assembleDebug        # отладочная сборка
./gradlew :app:testDebugUnitTest    # юнит-тесты
```

Отладочная сборка устанавливается рядом с релизной под идентификатором `com.itd.app.debug`.

## 🗺 Что дальше

Постараюсь успевать **переносить каждое обновление** ИТД вовремя. Исходники определённого обновления можно будет скачать на странице **[Релизы](https://github.com/D1ma-coder/ITD-APK-Source/releases)**.

| Версия ИТД | Статус |
|:---:|:---|
| **1.1.0** | ✅ перенесена |
| следующая | ⏳ скоро |

## ⚠️ Дисклеймер

Это неофициальный проект, не связанный с командой ИТД. Название и логотип принадлежат их владельцам. Проект создан в образовательных целях и для разработки сторонних клиентов. Пользуясь модами, соблюдайте правила сервиса.

<div align="center">
<br>

Если проект пригодился — поставьте ⭐ и сделайте форк!

<sub>Сделано с ❤️ для сообщества ИТД</sub>

</div>
