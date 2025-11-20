package org.figsq.fipixelsync.fipixelsync.comm.messages;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import lombok.val;
import org.figsq.fipixelsync.fipixelsync.comm.CommManager;
import org.figsq.fipixelsync.fipixelsync.comm.IHandler;
import org.figsq.fipixelsync.fipixelsync.comm.IMessage;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPCStorage;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPlayerPartyStorage;
import org.figsq.fipixelsync.fipixelsync.pixel.FISaveAdapter;
import org.figsq.fipixelsync.fipixelsync.pixel.FIStorageManager;

import java.util.UUID;

/**
 * 玩家进入服务器时候给其他服务器通知
 */
public class PlayerJoinServerMessage implements IMessage {
    public UUID owner;

    public PlayerJoinServerMessage() {
    }

    public PlayerJoinServerMessage(final UUID owner) {
        this.owner = owner;
    }

    @Override
    public ByteArrayDataOutput encode(final ByteArrayDataOutput buffer) {
        buffer.writeLong(this.owner.getMostSignificantBits());
        buffer.writeLong(this.owner.getLeastSignificantBits());
        return buffer;
    }

    @Override
    public IMessage decode(final ByteArrayDataInput buffer) {
        this.owner = new UUID(buffer.readLong(), buffer.readLong());
        return this;
    }

    @Override
    public boolean canNotifyPublisher() {
        return false;
    }

    public static class Handler implements IHandler<PlayerJoinServerMessage> {
        public static final Handler INSTANCE = new Handler();

        @Override
        public void handle(UUID sender, PlayerJoinServerMessage message) {
            //冻结(被插件手动冻结了的情况) / 加载(玩家加载顺序错了) / 保存(本身会重发) 排除冻结后，判断islock可以根据这个信息来直接推测
            val owner = message.owner;
            val storageManager = FIStorageManager.getInstance();
            val party = (FIPlayerPartyStorage) storageManager.getParty(owner);
            val pc = (FIPCStorage) storageManager.getPCForPlayer(owner);
            val partyWaitUUIDs = FISaveAdapter.partyWaitMap.get(owner);
            val pcWaitUUIDs = FISaveAdapter.pcWaitMap.get(owner);
            if ((partyWaitUUIDs != null && !partyWaitUUIDs.isEmpty()) || (pcWaitUUIDs != null && !pcWaitUUIDs.isEmpty()))
                return;
            //本服没有等待加载
            if (!party.isLock(true))
                CommManager.publishTo(sender, new PlayerStorageRespondMessage(owner, true, false));
            if (!pc.isLock(true))
                CommManager.publishTo(sender, new PlayerStorageRespondMessage(owner, false, false));
            //如果正在等待加载，那么这个进服请求就是其他服务器正在等待加载，选择不回复
        }
    }
}