# DearMe — Privacy Policy

*Last updated: 6 November 2026*

DearMe is an Android app for letters to your future self: text, photos and voice notes sealed until a
day you choose. It is published as open source at <https://github.com/bigdevwhale/dearme>.

## What we collect

**Nothing.** DearMe has no accounts, no analytics, no advertising and no crash reporting.
We run no server, so nothing you write, photograph or record ever reaches us.

## What stays on your device

The app keeps the following only in its private storage on your phone:

- your letters and moments: their text, the photos you take or pick, your voice notes, replies and
  "then & now" shots, and the dates they open;
- your rituals: when DearMe should call you and what goes into the envelope;
- your settings (name, language, theme, notifications) and the last trusted clock reading.

## The network clock

Before a seal is broken, DearMe checks that the phone's date hasn't been moved forward. If the phone is
online, it sends one empty request to `https://www.gstatic.com/generate_204` (a Google server) and reads
the time from the reply's date header. The request carries nothing of yours: no letter, no account, no
device identifier. Like any request on the internet, it reveals your IP address to that server, whose
own privacy policy applies. This is the app's only network traffic.

## Sharing

- **Share cards** and opened letters are shared only when you tap Share, through the Android share sheet
  to an app you choose.
- **Backup files** are created only when you ask for one in settings and saved where you choose.
  Inside, the file is scrambled so that unzipping it doesn't reveal unopened letters.
- **Community links** in settings open Telegram or your browser, only when you tap them; what you do
  there is governed by those services' own policies.
- **Device backup.** Android may include the app's data in your own device backup to your Google account,
  if you have that turned on in system settings.

## Permissions

- **Camera** — to take photos for a letter or a moment, only when you open the camera.
- **Microphone** — to record a voice note, only when you press record.
- **Notifications** — for ritual calls and to tell you when a letter is ready to open.
- **Alarms & reminders** (exact alarms) — so a ritual calls at its time and a letter opens on its day.
- **Run at startup** — to set those alarms again after the phone restarts.
- **Vibration** — for the call and the breaking seal.
- **Internet** — only for the network clock described above.

Photos from your gallery are picked with the system photo picker: the app sees only the photos you choose.

## Children

DearMe is not directed at children under 13.

## Changes

Any change to this policy will be published on this page with a new date.

## Contact

Questions: open an issue at <https://github.com/bigdevwhale/dearme/issues>.

---

# DearMe — Политика конфиденциальности

*Обновлено: 6 ноября 2026*

DearMe — приложение под Android для писем себе в будущее: текст, снимки и голос, запечатанные до
выбранного дня. Код открыт: <https://github.com/bigdevwhale/dearme>.

## Что мы собираем

**Ничего.** В приложении нет аккаунтов, аналитики, рекламы и отчётов о сбоях.
У нас нет сервера, поэтому ничего из того, что вы пишете, снимаете или записываете, к нам не попадает.

## Что хранится на устройстве

Только в собственном хранилище приложения на телефоне:

- письма и моменты: текст, снимки, которые вы сделали или выбрали, голосовые записи, ответы и снимки
  «тогда и сейчас», а также даты, когда они откроются;
- ритуалы: когда DearMe звать вас и что класть в конверт;
- настройки (имя, язык, тема, уведомления) и последнее проверенное показание часов.

## Сетевые часы

Перед тем как сломать печать, DearMe проверяет, что дату на телефоне не перевели вперёд. Если телефон
в сети, приложение отправляет один пустой запрос на `https://www.gstatic.com/generate_204` (сервер Google)
и берёт время из заголовка ответа. В запросе нет ничего вашего: ни письма, ни аккаунта, ни идентификатора
устройства. Как любой запрос в интернете, он сообщает этому серверу ваш IP-адрес; там действует политика
Google. Другого сетевого трафика у приложения нет.

## Передача

- **Открытки** и открытые письма отправляются, только когда вы нажимаете «Поделиться», через системное
  меню Android в выбранное вами приложение.
- **Файл резервной копии** создаётся, только когда вы просите об этом в настройках, и сохраняется туда,
  куда вы укажете. Внутри он зашифрован, чтобы распаковка не открыла запечатанные письма.
- **Ссылки на сообщество** в настройках открывают Telegram или браузер, только когда вы на них нажимаете;
  там действуют правила этих сервисов.
- **Резервная копия устройства.** Android может включить данные приложения в резервную копию устройства
  в вашем аккаунте Google, если она включена в настройках.

## Разрешения

- **Камера** — чтобы снимать для письма или момента, только когда вы открываете камеру.
- **Микрофон** — чтобы записать голос, только когда вы нажимаете запись.
- **Уведомления** — зов ритуала и сообщение, что письмо можно открыть.
- **Будильники и напоминания** (точные будильники) — чтобы ритуал звал вовремя, а письмо открывалось в свой день.
- **Запуск при включении** — чтобы заново поставить эти будильники после перезагрузки.
- **Вибрация** — для зова и ломающейся печати.
- **Интернет** — только для сетевых часов, описанных выше.

Снимки из галереи выбираются через системный выбор фото: приложение видит только выбранные вами снимки.

## Дети

Приложение не предназначено для детей младше 13 лет.

## Изменения

Изменения этой политики публикуются на этой странице с новой датой.

## Контакты

Вопросы — через issue: <https://github.com/bigdevwhale/dearme/issues>.
