package me.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class DiscordStatPlugin extends JavaPlugin {

    private JDA jda;
    private long channelId;
    private String messageId; // ID того самого Discord-сообщения
    private String serverDomain;
    private int serverPort;

    @Override
    public void onEnable() {
        // Создаёт config.yml, если его нет
        saveDefaultConfig();

        // Читаем настройки
        String botToken = getConfig().getString("bot-token", "");
        channelId = getConfig().getLong("channel-id", 0L);
        serverDomain = getConfig().getString("server-domen", "f1.rustix.me");
        serverPort = getConfig().getInt("server-port", 28146);

        if (botToken.isEmpty() || channelId == 0) {
            getLogger().severe("Не указан bot-token или channel-id в config.yml! Отключаюсь...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            // Запускаем Discord-бота
            jda = JDABuilder.createDefault(botToken)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES)
                    .build();
            jda.awaitReady();

            getLogger().info("Discord бот успешно запущен!");

            // Получаем канал по ID
            MessageChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                getLogger().severe("Не удалось найти Discord-канал по ID " + channelId);
                return;
            }

            // Ищем в конфиге, нет ли старого messageId
            messageId = getConfig().getString("discord-message-id", "");
            Message msg = null;
            // Если messageId непустой, попытаемся загрузить сообщение
            if (messageId != null && !messageId.isEmpty()) {
                try {
                    msg = channel.retrieveMessageById(messageId).complete();
                } catch (ErrorResponseException ex) {
                    getLogger().warning("Не удалось получить сообщение " + messageId + ". Будет создано новое.");
                }
            }

            // Если не получилось загрузить старое - создаём новое
            if (msg == null) {
                msg = channel.sendMessage("Инициализация...").complete();
                messageId = msg.getId();
                getConfig().set("discord-message-id", messageId);
                saveConfig();
            }

            // Каждые 5 секунд обновляем Discord-сообщение (асинхронно)
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                try {
                    String content = buildStatusMessage();
                    channel.editMessageById(messageId, content).queue();
                } catch (Exception e) {
                    getLogger().warning("Ошибка при обновлении сообщения: " + e.getMessage());
                }
            }, 0L, 5 * 20L);

            // Раз в минуту проверяем расписание команд
            Bukkit.getScheduler().runTaskTimer(this, this::checkScheduledCommands, 0L, 60 * 20L);

        } catch (Exception e) {
            e.printStackTrace();
            getLogger().severe("Ошибка при запуске Discord-бота: " + e.getMessage());
            // Если бот не поднялся, отключаем плагин
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Останавливаем бота при выгрузке плагина
        if (jda != null) {
            jda.shutdownNow();
        }
        getLogger().info("DiscordStatPlugin отключён.");
    }

    /**
     * Формируем текст для Discord-сообщения.
     * - TPS (обрезаем макс до 20.0)
     * - Онлайн/оффлайн (условно онлайн)
     * - Кол-во игроков
     * - Список игроков (пинг)
     */
    private String buildStatusMessage() {
        double rawTps = getServerTPS();   // получим TPS через reflection
        double tps = Math.min(rawTps, 20.0); // чтобы не показывало 23
        String tpsStr = String.format("%.2f", tps);

        boolean online = true; // Раз плагин работает - сервер онлайн
        int onlineCount = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();

        StringBuilder sb = new StringBuilder();
        sb.append("**Статистика сервера**\n");
        sb.append("**TPS**: ").append(tpsStr).append("\n");
        sb.append("**Статус**: ").append(online ? "🟢 Онлайн" : "🔴 Оффлайн").append("\n");
        sb.append("**Игроков онлайн**: ").append(onlineCount).append("/").append(maxPlayers).append("\n");

        for (Player p : Bukkit.getOnlinePlayers()) {
            int ping = getPlayerPing(p);
            sb.append(p.getName()).append(" (пинг: ").append(ping).append("ms)\n");
        }
        sb.append("\nДомен: ").append(serverDomain).append(":").append(serverPort);

        return sb.toString();
    }

    /**
     * Пробуем достать TPS через reflection из MinecraftServer (Mohist / Spigot).
     * MinecraftServer.recentTps[0].
     */
    private double getServerTPS() {
        try {
            // Bukkit.getServer() -> CraftServer
            Object craftServer = Bukkit.getServer();
            // craftServer.getServer() -> MinecraftServer
            Method getServerMethod = craftServer.getClass().getMethod("getServer");
            Object nmsMinecraftServer = getServerMethod.invoke(craftServer);

            // nmsMinecraftServer.recentTps -> double[]
            Field tpsField = nmsMinecraftServer.getClass().getField("recentTps");
            double[] tpsArr = (double[]) tpsField.get(nmsMinecraftServer);
            return tpsArr[0]; // обычно [0] - 1-минутный TPS
        } catch (Exception e) {
            // Если не вышло, вернём заглушку
            return 20.0;
        }
    }

    /**
     * Пробуем получить пинг игрока несколькими путями:
     * 1) Если у Player есть метод getPing().
     * 2) Иначе берём поле ping у EntityPlayer.
     * Если всё неудачно - вернём -1.
     */
    private int getPlayerPing(Player player) {
        try {
            // Сначала пробуем вызвать метод getPing() у player
            Method getPingMethod = player.getClass().getMethod("getPing");
            return (int) getPingMethod.invoke(player);
        } catch (Exception e) {
            // Если такого метода нет или ошибка - идём дальше
        }

        // Пробуем reflection: player.getHandle().ping
        try {
            Method getHandle = player.getClass().getMethod("getHandle");
            Object entityPlayer = getHandle.invoke(player);

            Field pingField = entityPlayer.getClass().getField("ping");
            return pingField.getInt(entityPlayer);
        } catch (Exception e) {
            // Если не нашли поле ping - вернём -1
            return -1;
        }
    }

    /**
     * Проверяет расписание команд (каждую минуту).
     * В config.yml:
     * scheduled-commands:
     *   - time: "12:00"
     *     command: "say Полдень!"
     */
    private void checkScheduledCommands() {
        List<?> scheduled = getConfig().getList("scheduled-commands");
        if (scheduled == null) return;

        // Текущее локальное время
        LocalTime now = LocalTime.now(TimeZone.getDefault().toZoneId());

        for (Object entry : scheduled) {
            if (!(entry instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) entry;
            String time = (String) map.get("time");
            String command = (String) map.get("command");
            if (time == null || command == null) {
                continue;
            }

            LocalTime scheduledTime;
            try {
                scheduledTime = LocalTime.parse(time); // формат "HH:mm"
            } catch (Exception e) {
                continue;
            }

            // Если часы/минуты совпадают
            if (now.getHour() == scheduledTime.getHour() && now.getMinute() == scheduledTime.getMinute()) {
                // Выполняем команду от имени консоли
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                getLogger().info("Выполнил команду по расписанию: " + command);
            }
        }
    }
}
