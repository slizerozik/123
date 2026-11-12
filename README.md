# Кланы и капты для Roblox

Система кланов (создание, приглашения, ранги, очки, сохранение в DataStore) и захвата зон («каптов») между кланами.

## Установка

### Через Rojo

```
rojo serve
```

Подключитесь из плагина Rojo в Studio — скрипты разложатся сами.

### Вручную

Создайте в Studio объекты и вставьте в них код из файлов:

| Где в Studio | Тип | Файл |
| --- | --- | --- |
| `ReplicatedStorage/ClanShared/Config` | ModuleScript в папке `ClanShared` | `src/shared/Config.lua` |
| `ServerScriptService/ClanSystem/Main` | Script в папке `ClanSystem` | `src/server/Main.server.lua` |
| `ServerScriptService/ClanSystem/ClanService` | ModuleScript | `src/server/ClanService.lua` |
| `ServerScriptService/ClanSystem/CaptureService` | ModuleScript | `src/server/CaptureService.lua` |
| `StarterPlayer/StarterPlayerScripts/ClanClient` | LocalScript | `src/client/ClanClient.client.lua` |

Имена папок и скриптов должны совпадать с таблицей.

### Настройка места

1. **Зоны.** Создайте в `Workspace` папку `CaptureZones` и положите в неё парты (Block, `Anchored = true`, `CanCollide = false`, можно полупрозрачные). Каждый парт — одна зона, имена партов должны быть уникальными. Игрок считается в зоне, если стоит над партом (до 30 studs выше). Чтобы показать другое название, добавьте парту строковый атрибут `DisplayName`.
2. **Сохранение.** Опубликуйте место и включите *Game Settings → Security → Enable Studio Access to API Services*. Без этого в Studio всё работает, но кланы живут только до конца сессии.

## Как это работает

**Кланы.** Кнопка «Кланы» слева открывает окно. Игрок без клана может создать свой (название, тег, цвет) или принять приглашение. Ранги: лидер, офицер, участник. Офицеры приглашают и кикают участников; лидер ещё повышает/понижает, передаёт клан и распускает его. Тег клана показывается над головой. Название и тег проходят через фильтр текста Roblox.

**Капты.** Пока в зоне стоят игроки только одного клана, шкала заполняется (30 секунд для одного игрока, быстрее с соклановцами). Чужую зону сначала нужно сбить до нуля, потом захватывать. Если в зоне несколько кланов — захват заморожен. Игроки без клана захватывать не могут. За захват клан получает 100 очков, за удержание — 5 очков каждые 30 секунд. Владение зонами действует в пределах сервера и сбрасывается при его перезапуске; очки клана сохраняются.

Все числа меняются в `Config`.

## Для своих скриптов

На сервере:

```lua
local ClanService = require(game.ServerScriptService.ClanSystem.ClanService)

local clan = ClanService.GetPlayerClan(player) -- { id, name, tag, color, points, members } или nil
ClanService.AddPoints(clan.id, 50)
```

Также у игрока есть атрибуты `ClanId`, `ClanName`, `ClanTag` и `CurrentZone`, а у парта зоны — `OwnerTag`, `HolderTag`, `Control` (0..1), `Contested`.
