package org.figsq.fipixelsync.fipixelsync;

import com.google.common.collect.Lists;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.List;

public class CommandBase implements TabExecutor {
    private BukkitTask task;

    public static final List<String> subCmd = Collections.unmodifiableList(
            Lists.newArrayList(
                    "help",
                    "reload",
                    "migrate"
            )
    );

    public static final String[] helpMsg = new String[]{
            "§e/fipixelsync help §f显示本帮助",
            "§e/fipixelsync reload §f重载插件",
            "§e/fipixelsync migrate [迁移类型(pixelmon/更多)] §f迁入数据"
    };

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§c非op不可用!");
            return false;
        }
        if (args.length > 0) {
            String cmdArg = args[0].toLowerCase();
            if (!cmdArg.equals("help") && subCmd.contains(cmdArg)) {
                switch (cmdArg) {
                    case "reload": {
                        if (sender instanceof ConsoleCommandSender) {
                            if (task != null) {
                                try {
                                    task.cancel();
                                } catch (Exception ignored) {
                                }
                                task = null;
                                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                                    onlinePlayer.kickPlayer("§e[FIPixelSync]: 玩家宝可梦数据重载中...请稍后再尝试进入服务器!");
                                }
                                sender.sendMessage("§e[FIPixelSync]: 正在重载中...");
                                Bukkit.getScheduler().runTask(Main.instance, () -> {
                                    Main.instance.reloadConfig();
                                    sender.sendMessage("§a[FIPixelSync]: 重载完成!");
                                });
                                return false;
                            }
                            task = Bukkit.getScheduler().runTaskLater(Main.instance, () -> {
                                task = null;
                            }, 15 * 20L);
                            Main.instance.getLogger().warning("使用该命令会踢出所有玩家!!!15秒内再次输入进行确认!");
                            return false;
                        }
                        sender.sendMessage("§c玩家不可以用该命令!");
                        return false;
                    }
                    case "migrate": {
                        if (sender instanceof ConsoleCommandSender) {

                            if (args.length < 2) {
                                sender.sendMessage("§c请输入迁移类型!");
                                return false;
                            }
                            String typeName = args[1];
                            EnumMigrationType migrateType = EnumMigrationType.findMigrateType(typeName);
                            if (migrateType == null) {
                                sender.sendMessage("§c找不到 " + typeName + " 迁移类型不存在!");
                                return false;
                            }
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                player.kickPlayer("§e[FIPixelSync]: 玩家宝可梦数据迁移中...请稍后再尝试进入服务器!");
                            }
                            try {
                                migrateType.getMigrationRunnable().run();
                            } catch (Exception e) {
                                sender.sendMessage("§c迁移失败!");
                                e.printStackTrace();
                                return false;
                            }
                            sender.sendMessage("§a迁移成功!");
                            return false;
                        }
                        sender.sendMessage("§c玩家不可以用该命令!");
                        return false;
                    }
                }
            }
        }
        sender.sendMessage(helpMsg);
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        return null;
    }
}
