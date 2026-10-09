package com.dripps.voxyserver.network;

import java.util.function.BiConsumer;
import java.util.function.Function;

public record PacketCodec<B, T>(BiConsumer<B, T> writer, Function<B, T> reader) {
    public static <B, T> PacketCodec<B, T> of(BiConsumer<B, T> writer, Function<B, T> reader) {
        return new PacketCodec<>(writer, reader);
    }
    public void encode(B buffer, T payload) { writer.accept(buffer, payload); }
    public T decode(B buffer) { return reader.apply(buffer); }
}
