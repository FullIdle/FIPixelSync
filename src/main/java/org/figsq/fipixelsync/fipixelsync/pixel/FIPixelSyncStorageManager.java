package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PokemonStorage;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.storage.ReforgedStorageManager;
import lombok.val;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.figsq.fipixelsync.fipixelsync.BukkitListener;
import org.figsq.fipixelsync.fipixelsync.Main;
import org.figsq.fipixelsync.fipixelsync.config.ConfigManager;
import redis.clients.jedis.params.SetParams;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @see ReforgedStorageManager
 */
public class FIPixelSyncStorageManager extends ReforgedStorageManager {
    public static final String PCS_IDENTITY = "FIPixelSync:PCs:";
    public static final String PARTIES_IDENTITY = "FIPixelSync:Parties:";
    public static final Map<UUID, BukkitTask> TASKS = new HashMap<>();

    public static String getPcsIdentity(UUID uuid) {
        return PCS_IDENTITY + uuid.toString();
    }

    public static String getPartiesIdentity(UUID uuid) {
        return PARTIES_IDENTITY + uuid.toString();
    }
    /*static↑*/

    public FIPixelSyncStorageManager() {
        super(PixelUtil.oldManager.getSaveScheduler(), FIPixelSyncSaveAdapter.INSTANCE);
    }

    //预留
    public void close() {}

    /**
     * 这个方法本来是可以给PC用的但仔细一想，pc只有在交互的时候会加载，这个时候，我给玩家上锁，然后将其拦截交互操作，不让打开pc即可（其他插件直接发的另算）
     * 所以参考
     *
     * @param uuid 由于是初始化完毕才会有uuid (pc) 所以需要统一传uuid参数
     * @see org.figsq.fipixelsync.fipixelsync.BukkitListener
     * 这将会异步执行
     */
    public void lazyRead(PokemonStorage storage, UUID uuid, String storageIdentity) {
        System.out.println("§3lazy");
        //套个取消旧任务(一般没有)
        PixelUtil.unsafeCancelTask(TASKS.put(uuid, Bukkit.getScheduler().runTaskAsynchronously(Main.INSTANCE, () -> {
            try (val resource = ConfigManager.redis.getResource()) {
                PixelUtil.playerSetFrozen(uuid, true);
                System.out.println("§3frozen true");
                val identity = PixelUtil.getPlayerLockIdentity(uuid);
                val setParams = SetParams.setParams();
                //原子级操作，不怕直接取消
                if (!resource.set(identity, "1", setParams.ex(10).nx()).equals("OK")) {
                    //已经存在锁了
                    throw new RuntimeException("§c§l玩家正在被其他端操作，请稍后再试");
                }
                System.out.println("§3setex 10s 1 and wait");
                while (resource.get(identity) != null) {} //等待其他端或者 10 秒后 解锁
                System.out.println("§3wait end check player");
                if (Bukkit.getPlayer(uuid) == null) return; //突发问题直接跳过(由其他处理(quit))
                System.out.println("§3player not null get nbt");
                val nbtData = resource.getDel(storageIdentity); //10秒内redis中会有缓存 读取后删除 如果没有视作进服而不是跳转
                if (nbtData != null) {
                    System.out.println("§3read nbt");
                    storage.readFromNBT(JsonToNBT.func_180713_a(nbtData));
                    storage.shouldSendUpdates = true;
                } else {
                    System.out.println("§3no nbt");
                }
                System.out.println("§3frozen false");
                PixelUtil.playerSetFrozen(uuid, false);//在最后解锁，防止读到一半玩家傻逼逼的强制自己给自己退了
                PixelUtil.unsafeCancelTask(TASKS.remove(uuid)); //删除还要关闭的原因是怕有啥我算不到的任务被莫名其妙加进去了
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        })));
    }

    @Override
    public PlayerPartyStorage getParty(UUID uuid) {
        val notData = !this.parties.containsKey(uuid);
        val party = super.getParty(uuid);
        if (notData) lazyRead(party, uuid, getPartiesIdentity(uuid));
        return party;
    }

    @Override
    public void clearAll() {
        super.clearAll();
        try (val resource = ConfigManager.redis.getResource()) {
            val keys = resource.keys(PCS_IDENTITY + "*");
            keys.addAll(resource.keys(PARTIES_IDENTITY + "*"));
            if (!keys.isEmpty()) resource.del(keys.toArray(new String[0]));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Map<UUID, PCStorage> getPCs() {
        return this.pcs;
    }

    public Map<UUID, PlayerPartyStorage> getParties() {
        return this.parties;
    }

    /**
     * 玩家退出
     *
     * @see org.figsq.fipixelsync.fipixelsync.BukkitListener
     */
    public void onQuit(Player player) {
        val uniqueId = player.getUniqueId();
        PixelUtil.unsafeCancelTask(TASKS.remove(uniqueId)); //如果有加载任务直接取消因为这个b要走了
        PixelUtil.playerSetFrozen(uniqueId,false); //不管如何都是要解冻
        val scheduler = Bukkit.getScheduler();
        System.out.println("§3quit");
        //延迟到玩家彻底退出后才开始执行
        System.out.println("§3check forzen and set false");
        if (PixelUtil.playerSetFrozen(uniqueId, false)) return;//跳转失败导致的
        //转到彻底退出后处理
        scheduler.runTaskLater(Main.INSTANCE, ()->{
            if (Bukkit.getPlayer(uniqueId) != null) return;//玩家™的在5Tick时间内又™的回来了
            //不管如何都是要删除这边的缓存的z
            val party = this.parties.remove(uniqueId);
            val pc = this.pcs.remove(uniqueId);
            //保存到mysql内 如果跳转失败，失败的那个服务器在上面判断lockList的时候就结束了，这样可以防止脏数据
            //如果没有 party 数据那直接不用同步了，直接返回
            System.out.println("§3check paty null");
            if (party == null) return; //除非有别的插件去删的，那我不管~ 通常不会没数据的~
            adapter.save(party);
            System.out.println("§3save data");
            if (pc != null) adapter.save(pc);
            //异步处理连接，如果超过10秒，那玩家大概率就丢数据了 以下条件需要在上锁处理比检测快才可以(之后优化)
            scheduler.runTaskAsynchronously(Main.INSTANCE, () -> {
                try (val resource = ConfigManager.redis.getResource()) {
                    System.out.println("§3check nx");
                    val identity = PixelUtil.getPlayerLockIdentity(uniqueId);
                    val startTime = System.currentTimeMillis();
                    //5秒的轮询 (退出不需要像进入那样多，时间可以一些) 期间如果玩家回来了，直接结束轮询
                    boolean cantSet;
                    while ((cantSet = (resource.get(identity) == null && Bukkit.getPlayer(uniqueId) == null)) && System.currentTimeMillis() - startTime < 5000)
                        TimeUnit.MILLISECONDS.sleep(100); //这个越小越快
                    //最大只等5秒，5秒后再多判断一次
                    if (!cantSet)
                        resource.setex(getPartiesIdentity(uniqueId), 10, party.writeToNBT(new NBTTagCompound()).toString());
                    //不管如何都要解锁 如果有被锁的其他机器玩家，则会读取到
                    resource.del(identity);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }, 5L);
    }
}
