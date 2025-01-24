package org.figsq.fipixelsync.fipixelsync;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.IStorageSaveAdapter;
import com.pixelmonmod.pixelmon.api.storage.PCBox;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.StoragePosition;
import com.pixelmonmod.pixelmon.comm.packetHandlers.clientStorage.newStorage.ClientSet;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.storage.ReforgedStorageManager;
import com.pixelmonmod.pixelmon.util.helpers.ReflectionHelper;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

public final class PlayerListener implements Listener {
    public static final PlayerListener instance = new PlayerListener();

    private PlayerListener(){}

    @EventHandler
    public void quit(PlayerQuitEvent event){
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ReforgedStorageManager manager = (ReforgedStorageManager) Pixelmon.storageManager;
        Map<UUID, PlayerPartyStorage> parties = getParties();
        Map<UUID, PCStorage> pcs = getPcs();
        IStorageSaveAdapter adapter = manager.getSaveAdapter();
        if (parties.containsKey(uuid)) adapter.save(parties.get(uuid));
        if (pcs.containsKey(uuid)) adapter.save(pcs.get(uuid));
        Bukkit.getScheduler().runTask(Main.instance,()->{
            pcs.remove(uuid);
            parties.remove(uuid);
            manager.extraStorages.removeIf(storage -> storage.uuid.equals(uuid));
        });
    }

    @EventHandler
    public void join(PlayerJoinEvent event){
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ReforgedStorageManager manager = (ReforgedStorageManager) Pixelmon.storageManager;
        Map<UUID, PlayerPartyStorage> parties = getParties();
        Map<UUID, PCStorage> pcs = getPcs();
        BukkitScheduler scheduler = Bukkit.getScheduler();
        PlayerPartyStorage old = manager.getParty(uuid);
        //刷新
        scheduler.runTask(Main.instance,()->{
            //删除缓存
            parties.remove(uuid);
            //重获取
            PlayerPartyStorage newParty = manager.getParty(uuid);
            Pokemon oldPoke;
            Pokemon newPoke;
            for (int i = 0; i < 6; i++){
                oldPoke = old.get(i);
                newPoke = newParty.get(i);
                //如果原位置有精灵，新位置精灵与原位置精灵一样，直接跳过
                if (oldPoke != null && oldPoke.equals(newPoke)) continue;
                //刷新位置
                Pixelmon.network.sendTo(new ClientSet(newParty, new StoragePosition(-1, i), newParty.get(i)), newParty.getPlayer());
            }
            //如果加载了pc
            if (pcs.containsKey(uuid)) {
                //删除pc缓存
                pcs.remove(uuid);
                //重获取
                PCStorage pc = manager.getPCForPlayer(newParty.getPlayer());
                //获取最后一次打开的页面
                PCBox box = pc.getBox(pc.getLastBox());
                //刷新(只对正在查看的玩家有用)
                for (int i = 0; i < 30; i++)
                    Pixelmon.network.sendTo(new ClientSet(newParty, new StoragePosition(box.boxNumber, i), box.get(i)), newParty.getPlayer());
            }
        });
    }



    private static final Field partiesField = ReflectionHelper.findField(ReforgedStorageManager.class, "parties", "parties");
    private static final Field pcsField = ReflectionHelper.findField(ReforgedStorageManager.class, "pcs", "pcs");

    @SneakyThrows
    private static Map<UUID, PlayerPartyStorage> getParties(){
        return (Map<UUID, PlayerPartyStorage>) partiesField.get(Pixelmon.storageManager);
    }
    @SneakyThrows
    private static Map<UUID, PCStorage> getPcs(){
        return (Map<UUID, PCStorage>) pcsField.get(Pixelmon.storageManager);
    }
}
