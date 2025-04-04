![Author: AlexFirst404](https://img.shields.io/badge/author-AlexFirst404-blueviolet)
![License: CC BY-NC 4.0](https://img.shields.io/badge/License-CC%20BY--NC%204.0-lightgrey.svg)
![Status: Beta](https://img.shields.io/badge/status-beta-yellow.svg)
![Java](https://img.shields.io/badge/language-Java-blue.svg)


# DiscordStatPlugin

**DiscordStatPlugin** – это плагин для [Mohist 1.20.1](https://mohistmc.com/), который поднимает Discord-бота и выводит статистику о Minecraft-сервере в **одном** сообщении в канале Discord. Также плагин умеет выполнять команды по расписанию.

## Основные функции

1. **Запуск Discord-бота** с помощью библиотеки [JDA](https://github.com/discord-jda/JDA).
2. **Автоматическое создание и редактирование** одного и того же сообщения в канале Discord каждые 5 секунд:
   - **TPS** (обрезается до 20.00, чтобы не показывало 23).
   - **Онлайн/оффлайн** статус (условно онлайн).
   - **Количество игроков**.
   - **Список игроков** с их пингом (через reflection).
3. **Выполнение команд по расписанию** (пример: `say …` в 12:00), настраиваемое в `config.yml`.

## Установка

1. Возьмите или соберите `.jar`-файл плагина.
2. Положите `.jar` в папку `plugins/` на вашем Mohist/Spigot-сервере (1.20.1).
3. Запустите (или перезапустите) сервер.
4. Впервые запущенный плагин создаст `config.yml` и запишет туда `discord-message-id` после того, как впервые отправит сообщение в Discord.

## Настройка

Откройте `config.yml`, чтобы задать:

```yaml
bot-token: "ВАШ_ТОКЕН_ЗДЕСЬ"
channel-id: 123456789012345678

server-domen: "f1.rustix.me"
server-port: 28146

# Плагин автоматически заполнит это поле после первого запуска
discord-message-id: ""

scheduled-commands:
  - time: "12:00"
    command: "say Наступил полдень!"
  - time: "18:30"
    command: "say 18:30, готовим рестарт через 5 минут..."
```
## License

This project is licensed under the [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/) License

You are free to use, modify, and share this code **for non-commercial purposes only**, as long as you give proper credit to **AlexFirst404**.
