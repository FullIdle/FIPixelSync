package org.figsq.fipixelsync.fipixelsync.pixel;

import lombok.val;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface FIStorage extends Function<Throwable, Void> {
    /**
     * 是否是冻结
     *
     * @return 是否是冻结
     */
    boolean isFreeze();

    /**
     * 设置玩家是否冻结
     *
     * @param lock 是否冻结
     */
    void setFreeze(boolean lock);

    /**
     * 获取正在进行保存处理的未来类对象
     * 如果未来对象已经完成{@link CompletableFuture#isDone()}，获取出来的则是最近一次的未来任务
     *
     * @return 正在进行保存处理的未来类对象
     */
    @NotNull
    CompletableFuture<Void> getSavingFuture();

    /**
     * 更新 正在进行保存处理的未来类对象
     *
     * @param savingFuture 正在进行保存处理的未来类对象
     */
    void setSavingFuture(@NotNull CompletableFuture<Void> savingFuture);

    /**
     * 正在进行加载处理的未来类对象
     * 如果未来对象已经完成{@link CompletableFuture#isDone()}，获取出来的则是最近一次的未来任务
     *
     * @return 正在进行加载处理的未来类对象
     */
    @NotNull
    CompletableFuture<Void> getLoadingFuture();

    /**
     * 更新 正在进行加载处理的未来类对象
     *
     * @param loadingFuture 正在进行加载处理的未来类对象
     */
    void setLoadingFuture(@NotNull CompletableFuture<Void> loadingFuture);

    /**
     * 会检查{@link #getSavingFuture()} {@link #getLoadingFuture()} 和 {@link #isFreeze()}
     * 这会影响保存，如果被锁的时候
     *
     * @return 检查多结果后是否正在被处理数据 如果是 {@code ture} 则证明数据正在被操作
     */
    default boolean isLock() {
        return isFreeze() || !getSavingFuture().isDone() || !getLoadingFuture().isDone();
    }

    /**
     * 全局静态的报错处理
     *
     * @param throwable 报错
     * @return 理论不返回
     */
    @Override
    default Void apply(Throwable throwable) {
        throwable.printStackTrace();
        this.setFreeze(true);
        throw new RuntimeException(throwable);
    }

    /**
     * 更新正在加载的下一个任务
     *
     * @param runnable 下一个任务
     * @return 本次任务
     */
    default CompletableFuture<Void> updateLoadingThen(Runnable runnable) {
        val future = getLoadingFuture().thenRun(runnable);
        future.exceptionally(this);
        setLoadingFuture(future);
        return future;
    }

    /**
     * 更新保存时下一个任务
     *
     * @param runnable 下一个任务
     * @return 本次任务
     */
    default CompletableFuture<Void> updateSavingThen(Runnable runnable) {
        val future = getSavingFuture().thenRun(runnable);
        future.exceptionally(this);
        setSavingFuture(future);
        return future;
    }
}
