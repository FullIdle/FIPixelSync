package org.figsq.fipixelsync.fipixelsync.pixel;

import lombok.val;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 用于FIPS的同步接口
 */
public interface IFIPixelSync {
    /**
     * 读取的处理
     */
    CompletableFuture<Void> getReadProcessingFuture();

    default CompletableFuture<Void> safeGetReadProcessingFuture() {
        val future = getReadProcessingFuture();
        if (future == null) return null;
        if (future.isCancelled() || future.isDone()) {
            this.setReadProcessingFuture(null);
            return null;
        }
        return future;
    }

    default boolean isLock() {
        return this.isNeedRead() || isReadLock();
    }

    default boolean isReadLock() {
        return this.safeGetReadProcessingFuture() != null;
    }

    default boolean isSaveWait() {
        return this.safeGetSaveProcessingFuture() != null;
    }

    void setReadProcessingFuture(CompletableFuture<Void> future);

    boolean isNeedRead();
    void setNeedRead(boolean needRead);

    /**
     * 保存的处理
     */
    CompletableFuture<Void> getSaveProcessingFuture();
    default CompletableFuture<Void> safeGetSaveProcessingFuture() {
        val future = getSaveProcessingFuture();
        if (future == null) return null;
        if (future.isCancelled() || future.isDone()) {
            this.setSaveProcessingFuture(null);
            return null;
        }
        return future;
    }
    void setSaveProcessingFuture(CompletableFuture<Void> future);

    /**
     * 强更列表
     */
    List<UUID> getForceUpdateNeedServerList();


    default void forceUpdateReceived(UUID serverUUID) {
        val list = getForceUpdateNeedServerList();
        if (list.isEmpty()) return;
        if (!this.isNeedRead() || this.isReadLock()) list.clear();
        list.remove(serverUUID);
        if (list.isEmpty()) this.forceUpdate();
    }

    /**
     * 强更新
     */
    default void forceUpdate() {
    }
}
