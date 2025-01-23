package org.figsq.fipixelsync.fipixelsync;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public class CommandBase implements TabExecutor {
    private BukkitTask task;

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof ConsoleCommandSender) {
            if (task != null){
                try {
                    task.cancel();
                } catch (Exception ignored) {
                }
                task = null;
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.kickPlayer("§e[FIPixelSync]: 玩家宝可梦数据重载中...请稍后再尝试进入服务器!");
                }
                sender.sendMessage("§e[FIPixelSync]: 正在重载中...");
                Bukkit.getScheduler().runTask(Main.instance, ()->{
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        return null;
    }
}
