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
import java.time.LocalDateTime;
import java.util.List;

public class Main extends JavaPlugin implements CommandExecutor {

    private FileConfiguration schedulerConfig;
    private FileConfiguration messagesConfig;
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
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("strafescheduler.admin")) {
            sender.sendMessage(prefix + ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(prefix + ChatColor.YELLOW + "Usage: /scheduler <run|reload> [task]");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            loadConfigs();
            sender.sendMessage(prefix + ChatColor.GREEN + "Configuration reloaded successfully.");
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

        sender.sendMessage(prefix + ChatColor.YELLOW + "Usage: /scheduler <run|reload> [task]");
        return true;
    }

    private void startScheduler() {
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            LocalDateTime now = LocalDateTime.now();

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