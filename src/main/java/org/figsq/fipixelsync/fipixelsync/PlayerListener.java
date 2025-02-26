package org.figsq.fipixelsync.fipixelsync;

import catserver.api.bukkit.event.ForgeEvent;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.events.EconomyEvent;
import com.pixelmonmod.pixelmon.api.events.LevelUpEvent;
import com.pixelmonmod.pixelmon.api.events.pokemon.*;
import com.pixelmonmod.pixelmon.api.events.quests.AbandonQuestEvent;
import com.pixelmonmod.pixelmon.api.events.quests.FinishQuestEvent;
import com.pixelmonmod.pixelmon.api.events.quests.QuestActionEvent;
import com.pixelmonmod.pixelmon.api.events.storage.ChangeStorageEvent;
import com.pixelmonmod.pixelmon.api.storage.*;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.storage.ReforgedStorageManager;
import com.pixelmonmod.pixelmon.storage.adapters.ReforgedFileAdapter;
import com.pixelmonmod.pixelmon.util.helpers.ReflectionHelper;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

public final class PlayerListener implements Listener {
    public static final PlayerListener instance = new PlayerListener();

    private PlayerListener() {
    }

    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void quit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ReforgedStorageManager manager = (ReforgedStorageManager) Pixelmon.storageManager;
        Map<UUID, PlayerPartyStorage> parties = getParties();
        Map<UUID, PCStorage> pcs = getPcs();
        IStorageSaveAdapter adapter = manager.getSaveAdapter();
        ReforgedFileAdapter reforgedFileAdapter = new ReforgedFileAdapter();
        if (parties.containsKey(uuid)) {
            PlayerPartyStorage storage = parties.get(uuid);
            adapter.save(storage);
            if (Main.pixelmon_save_call) {
                reforgedFileAdapter.save(storage);
            }
        }
        if (pcs.containsKey(uuid)) {
            PCStorage storage = pcs.get(uuid);
            adapter.save(storage);
            if (Main.pixelmon_save_call) {
                reforgedFileAdapter.save(storage);
            }
        }
    }

    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void preLogin(AsyncPlayerPreLoginEvent event){
        UUID uuid = event.getUniqueId();
        getPcs().remove(uuid);
        getParties().remove(uuid);
        ((ReforgedStorageManager) Pixelmon.storageManager).extraStorages.removeIf(storage -> storage.uuid.equals(uuid));
    }

    @SneakyThrows
    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void join(PlayerJoinEvent event) {
        Thread.sleep(100L);
    }


    private static final Field partiesField = ReflectionHelper.findField(ReforgedStorageManager.class, "parties", "parties");
    private static final Field pcsField = ReflectionHelper.findField(ReforgedStorageManager.class, "pcs", "pcs");

    @SneakyThrows
    private static Map<UUID, PlayerPartyStorage> getParties() {
        return (Map<UUID, PlayerPartyStorage>) partiesField.get(Pixelmon.storageManager);
    }

    @SneakyThrows
    private static Map<UUID, PCStorage> getPcs() {
        return (Map<UUID, PCStorage>) pcsField.get(Pixelmon.storageManager);
    }


    @EventHandler
    public void forge(ForgeEvent event) {
        if (!Main.high_frequency_writing) return;

        //宝可梦位置更变
        if (event.getForgeEvent() instanceof ChangeStorageEvent) {
            ChangeStorageEvent e = (ChangeStorageEvent) event.getForgeEvent();
            ReforgedStorageManager manager = (ReforgedStorageManager) Pixelmon.storageManager;
            IStorageSaveAdapter adapter = manager.getSaveAdapter();
            BukkitScheduler scheduler = Bukkit.getScheduler();
            if (e.newStorage == e.oldStorage) {
                if (e.newPosition.equals(e.oldPosition)) return;
                scheduler.runTask(Main.instance, () -> {
                    adapter.save(e.newStorage);
                });
                return;
            }
            scheduler.runTask(Main.instance, () -> {
                adapter.save(e.newStorage);
                adapter.save(e.oldStorage);
            });
            return;
        }
        //使用皇冠
        if (event.getForgeEvent() instanceof BottleCapEvent) {
            BottleCapEvent e = (BottleCapEvent) event.getForgeEvent();
            runSimpleSave(e.getPokemon().getStorage());
            return;
        }
        //努力值变化
        if (event.getForgeEvent() instanceof EVsGainedEvent) {
            EVsGainedEvent e = (EVsGainedEvent) event.getForgeEvent();
            runSimpleSave(e.pokemon.getStorage());
            return;
        }
        //物品修改形态
        if (event.getForgeEvent() instanceof ItemFormChangeEvent) {
            ItemFormChangeEvent e = (ItemFormChangeEvent) event.getForgeEvent();
            runSimpleSave(e.pokemon.getPokemonData().getStorage());
            return;
        }
        //技能修改
        if (event.getForgeEvent() instanceof MovesetEvent) {
            MovesetEvent e = (MovesetEvent) event.getForgeEvent();
            runSimpleSave(e.pokemon.getStorage());
            return;
        }
        //技能变化
        if (event.getForgeEvent() instanceof LevelUpEvent) {
            LevelUpEvent e = (LevelUpEvent) event.getForgeEvent();
            runSimpleSave(e.pokemon.getPokemon().getStorage());
            return;
        }
        //修改显示名
        if (event.getForgeEvent() instanceof SetNicknameEvent) {
            SetNicknameEvent e = (SetNicknameEvent) event.getForgeEvent();
            runSimpleSave(e.pokemon.getStorage());
            return;
        }
        //任务放弃
        if (event.getForgeEvent() instanceof AbandonQuestEvent){
            AbandonQuestEvent e = (AbandonQuestEvent) event.getForgeEvent();
            runSimpleSave(Pixelmon.storageManager.getParty(e.player.func_110124_au()));
            return;
        }
        //任务进度
        if (event.getForgeEvent() instanceof QuestActionEvent){
            QuestActionEvent e = (QuestActionEvent) event.getForgeEvent();
            runSimpleSave(Pixelmon.storageManager.getParty(e.player.func_110124_au()));
            return;
        }
        //任务完成
        if (event.getForgeEvent() instanceof FinishQuestEvent){
            FinishQuestEvent e = (FinishQuestEvent) event.getForgeEvent();
            runSimpleSave(Pixelmon.storageManager.getParty(e.player.func_110124_au()));
            return;
        }
        //玩家钱变化
        if (event.getForgeEvent() instanceof EconomyEvent.PostTransactionEvent) {
            EconomyEvent.PostTransactionEvent e = (EconomyEvent.PostTransactionEvent) event.getForgeEvent();
            runSimpleSave(Pixelmon.storageManager.getParty(e.player.func_110124_au()));
            return;
        }
    }

    public static void runSimpleSave(PokemonStorage storage) {
        IStorageSaveAdapter adapter = Pixelmon.storageManager.getSaveAdapter();
        Bukkit.getScheduler().runTask(Main.instance, () -> adapter.save(storage));
    }
}
