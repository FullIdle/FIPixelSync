package org.figsq.fipixelsync.fipixelsync.comm.messages;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.pixelmonmod.pixelmon.api.storage.PokemonStorage;
import lombok.SneakyThrows;
import lombok.val;
import org.figsq.fipixelsync.fipixelsync.comm.IHandler;
import org.figsq.fipixelsync.fipixelsync.comm.IMessage;
import org.figsq.fipixelsync.fipixelsync.pixel.*;

import java.util.List;
import java.util.UUID;


/**
 * 当玩家数据被正规保存后发布
 * 当接收到 {@link PlayerJoinServerMessage} 时，会返回该包给目标，但如果玩家正在保存，则会在保存后发送
 */
public class PlayerStorageRespondMessage implements IMessage {
    /**
     * 玩家uuid
     */
    public UUID owner;
    /**
     * 是否是Party
     */
    public boolean isParty;
    /**
     * 是否是强制保存来的(通常应该是切服而已)
     */
    public boolean isForce;

    public PlayerStorageRespondMessage() {
    }

    public PlayerStorageRespondMessage(final UUID owner, final boolean isParty, final boolean isForce) {
        this.owner = owner;
        this.isParty = isParty;
        this.isForce = isForce;
    }

    @Override
    public ByteArrayDataOutput encode(ByteArrayDataOutput buffer) {
        buffer.writeLong(this.owner.getMostSignificantBits());
        buffer.writeLong(this.owner.getLeastSignificantBits());
        buffer.writeBoolean(this.isParty);
        buffer.writeBoolean(this.isForce);
        return buffer;
    }

    @SneakyThrows
    @Override
    public IMessage decode(ByteArrayDataInput buffer) {
        this.owner = new UUID(buffer.readLong(), buffer.readLong());
        this.isParty = buffer.readBoolean();
        this.isForce = buffer.readBoolean();
        return this;
    }

    /**
     * 不给自己发包
     */
    @Override
    public boolean canNotifyPublisher() {
        return false;
    }

    public static class Handler implements IHandler<PlayerStorageRespondMessage> {
        public static final Handler INSTANCE = new Handler();

        @Override
        public void handle(UUID sender, PlayerStorageRespondMessage message) {
            val storageManager = FIStorageManager.getInstance();
            val owner = message.owner;
            val isForce = message.isForce;
            if (message.isParty) {
                handle(sender, (FIPlayerPartyStorage) storageManager.getParty(owner), FISaveAdapter.partyWaitMap.get(owner), isForce);
                return;
            }
            handle(sender, (FIPCStorage) storageManager.getPCForPlayer(owner), FISaveAdapter.pcWaitMap.get(owner), isForce);
        }

        public <T extends PokemonStorage & FIStorage> void handle(UUID sender, T storage, List<UUID> checkList, boolean isForce) {
            if (!isForce && (checkList == null || !checkList.remove(sender) || !checkList.isEmpty())) return;
            FIStorageManager.getInstance().getFISaveAdapter().loadStorageData(storage);
        }
    }
}
