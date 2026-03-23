package strafeland.club.strafescheduler;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class Main extends JavaPlugin implements CommandExecutor {

    private FileConfiguration schedulerConfig;
    private int taskId = -1;
    private int lastExecutedMinute = -1;
    private String prefix = "";

    @Override
    public void onEnable() {
        loadConfigs();
        startScheduler();
        getCommand("scheduler").setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void loadConfigs() {
        File schedulerFile = new File(getDataFolder(), "scheduler.yml");
        if (!schedulerFile.exists()) {
            schedulerFile.getParentFile().mkdirs();
            saveResource("scheduler.yml", false);
        }
        schedulerConfig = YamlConfiguration.loadConfiguration(schedulerFile);

        prefix = ChatColor.translateAlternateColorCodes('&', "&8[&cScheduler&8] &7");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("strafescheduler.admin")) {
            sender.sendMessage(prefix + ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(prefix + ChatColor.YELLOW + "Usage: /scheduler <run|reload|tasks|next> [task]");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            loadConfigs();
            sender.sendMessage(prefix + ChatColor.GREEN + "Configuration reloaded successfully.");
            return true;
        }

        if (args[0].equalsIgnoreCase("tasks")) {
            sender.sendMessage(prefix + ChatColor.AQUA + "Scheduled Tasks:");
            if (schedulerConfig.getConfigurationSection("schedules") != null) {
                for (String key : schedulerConfig.getConfigurationSection("schedules").getKeys(false)) {
                    String path = "schedules." + key + ".";
                    if (!schedulerConfig.getBoolean(path + "enabled", true)) {
                        sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.RED + key + ChatColor.DARK_GRAY + " [DISABLED]");
                        continue;
                    }
                    String type = schedulerConfig.getString(path + "type", "UNKNOWN");
                    sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + key + ChatColor.YELLOW + " [" + type + "]");
                }
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("next")) {
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Madrid"));
            LocalDateTime closestTime = null;
            String closestTask = null;

            if (schedulerConfig.getConfigurationSection("schedules") != null) {
                for (String key : schedulerConfig.getConfigurationSection("schedules").getKeys(false)) {
                    String path = "schedules." + key + ".";
                    if (!schedulerConfig.getBoolean(path + "enabled", true)) continue;

                    String type = schedulerConfig.getString(path + "type");
                    if (type == null) continue;

                    int month = schedulerConfig.getInt(path + "month", 1);
                    int dayOfMonth = schedulerConfig.getInt(path + "day-of-month", 1);
                    int dayOfWeek = schedulerConfig.getInt(path + "day-of-week", 1);
                    int hour = schedulerConfig.getInt(path + "hour", 0);
                    int minute = schedulerConfig.getInt(path + "minute", 0);

                    LocalDateTime nextRun = getNextExecution(type, month, dayOfMonth, dayOfWeek, hour, minute, now);

                    if (nextRun != null) {
                        if (closestTime == null || nextRun.isBefore(closestTime)) {
                            closestTime = nextRun;
                            closestTask = key;
                        }
                    }
                }
            }

            if (closestTask == null) {
                sender.sendMessage(prefix + ChatColor.RED + "There are no scheduled tasks available.");
            } else {
                Duration duration = Duration.between(now, closestTime);
                long days = duration.toDays();
                long hours = duration.toHours() % 24;
                long minutes = duration.toMinutes() % 60;

                String timeLeft = "";
                if (days > 0) timeLeft += days + "d ";
                if (hours > 0) timeLeft += hours + "h ";
                timeLeft += minutes + "m";

                if (timeLeft.equals("0m")) {
                    timeLeft = "less than a minute";
                }

                sender.sendMessage(prefix + ChatColor.AQUA + "Next task: " + ChatColor.WHITE + closestTask);
                sender.sendMessage(prefix + ChatColor.AQUA + "Time left: " + ChatColor.YELLOW + timeLeft.trim());
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("run")) {
            if (args.length < 2) {
                sender.sendMessage(prefix + ChatColor.RED + "Please specify a task name to run.");
                return true;
            }

            String taskName = args[1];
            String path = "schedules." + taskName;

            if (schedulerConfig.getConfigurationSection("schedules") == null || schedulerConfig.getConfigurationSection(path) == null) {
                sender.sendMessage(prefix + ChatColor.RED + "Task '" + taskName + "' not found in scheduler.yml.");
                return true;
            }

            List<String> commands = schedulerConfig.getStringList(path + ".commands");
            for (String cmd : commands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }

            sender.sendMessage(prefix + ChatColor.GREEN + "Task '" + taskName + "' has been executed manually.");
            return true;
        }

        sender.sendMessage(prefix + ChatColor.YELLOW + "Usage: /scheduler <run|reload|tasks|next> [task]");
        return true;
    }

    private LocalDateTime getNextExecution(String type, int month, int dayOfMonth, int dayOfWeek, int hour, int minute, LocalDateTime now) {
        LocalDateTime next = now.withSecond(0).withNano(0);
        try {
            switch (type.toUpperCase()) {
                case "MINUTELY":
                    return next.plusMinutes(1);
                case "HOURLY":
                    next = next.withMinute(minute);
                    if (!next.isAfter(now)) next = next.plusHours(1);
                    return next;
                case "DAILY":
                    next = next.withHour(hour).withMinute(minute);
                    if (!next.isAfter(now)) next = next.plusDays(1);
                    return next;
                case "WEEKLY":
                    next = next.with(java.time.DayOfWeek.of(dayOfWeek)).withHour(hour).withMinute(minute);
                    if (!next.isAfter(now)) next = next.plusWeeks(1);
                    return next;
                case "MONTHLY":
                    int maxDays = java.time.YearMonth.from(next).lengthOfMonth();
                    next = next.withDayOfMonth(Math.min(dayOfMonth, maxDays)).withHour(hour).withMinute(minute);
                    if (!next.isAfter(now)) {
                        next = next.plusMonths(1);
                        maxDays = java.time.YearMonth.from(next).lengthOfMonth();
                        next = next.withDayOfMonth(Math.min(dayOfMonth, maxDays));
                    }
                    return next;
                case "YEARLY":
                    next = next.withMonth(month).withDayOfMonth(dayOfMonth).withHour(hour).withMinute(minute);
                    if (!next.isAfter(now)) {
                        next = next.plusYears(1).withMonth(month).withDayOfMonth(dayOfMonth);
                    }
                    return next;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private void startScheduler() {
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Madrid"));

            if (now.getMinute() == lastExecutedMinute) {
                return;
            }
            lastExecutedMinute = now.getMinute();

            if (schedulerConfig.getConfigurationSection("schedules") == null) {
                return;
            }

            for (String key : schedulerConfig.getConfigurationSection("schedules").getKeys(false)) {
                String path = "schedules." + key + ".";

                boolean enabled = schedulerConfig.getBoolean(path + "enabled", true);
                if (!enabled) {
                    continue;
                }

                String type = schedulerConfig.getString(path + "type");
                if (type == null) {
                    continue;
                }

                boolean shouldRun = false;
                int currentMinute = now.getMinute();
                int currentHour = now.getHour();
                int currentDayOfWeek = now.getDayOfWeek().getValue();
                int currentDayOfMonth = now.getDayOfMonth();
                int currentMonth = now.getMonthValue();

                switch (type.toUpperCase()) {
                    case "MINUTELY":
                        shouldRun = true;
                        break;
                    case "HOURLY":
                        int hMin = schedulerConfig.getInt(path + "minute", 0);
                        shouldRun = (currentMinute == hMin);
                        break;
                    case "DAILY":
                        int dHour = schedulerConfig.getInt(path + "hour", 0);
                        int dMin = schedulerConfig.getInt(path + "minute", 0);
                        shouldRun = (currentHour == dHour && currentMinute == dMin);
                        break;
                    case "WEEKLY":
                        int wDay = schedulerConfig.getInt(path + "day-of-week", 1);
                        int wHour = schedulerConfig.getInt(path + "hour", 0);
                        int wMin = schedulerConfig.getInt(path + "minute", 0);
                        shouldRun = (currentDayOfWeek == wDay && currentHour == wHour && currentMinute == wMin);
                        break;
                    case "MONTHLY":
                        int mDay = schedulerConfig.getInt(path + "day-of-month", 1);
                        int mHour = schedulerConfig.getInt(path + "hour", 0);
                        int mMin = schedulerConfig.getInt(path + "minute", 0);
                        shouldRun = (currentDayOfMonth == mDay && currentHour == mHour && currentMinute == mMin);
                        break;
                    case "YEARLY":
                        int yMonth = schedulerConfig.getInt(path + "month", 1);
                        int yDay = schedulerConfig.getInt(path + "day-of-month", 1);
                        int yHour = schedulerConfig.getInt(path + "hour", 0);
                        int yMin = schedulerConfig.getInt(path + "minute", 0);
                        shouldRun = (currentMonth == yMonth && currentDayOfMonth == yDay && currentHour == yHour && currentMinute == yMin);
                        break;
                }

                if (shouldRun) {
                    List<String> commands = schedulerConfig.getStringList(path + "commands");
                    for (String cmd : commands) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    }
                }
            }
        }, 0L, 20L);
    }
}