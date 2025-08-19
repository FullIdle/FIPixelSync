package org.figsq.fipixelsync.fipixelsync.comm;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.util.UUID;

/**
 * 感觉叫序列化和反序列化合适一点。。。。
 */
public interface IMessage {
    ByteArrayDataOutput encode(ByteArrayDataOutput buffer);
    /**
     * 返回该对象本身
     */
    IMessage decode(ByteArrayDataInput buffer);

    /**
     * 通知发布者
     */
    default boolean canNotifyPublisher() {
        return true;
    };

    default void handle(UUID sender) {
        CommManager.handleMessage(sender,this);
    }
}
