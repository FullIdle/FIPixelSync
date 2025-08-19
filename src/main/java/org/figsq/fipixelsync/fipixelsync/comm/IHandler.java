package org.figsq.fipixelsync.fipixelsync.comm;

import java.util.UUID;

public interface IHandler<T extends IMessage> {
    void handle(UUID sender,T message);
}
