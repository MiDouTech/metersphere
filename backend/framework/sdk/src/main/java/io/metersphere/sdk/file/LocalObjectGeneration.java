package io.metersphere.sdk.file;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/** Durable generations for SDK-managed local objects; reads never create or change metadata. */
final class LocalObjectGeneration {
    private static final ReentrantLock[] STRIPES = new ReentrantLock[128];
    static { for (int i=0;i<STRIPES.length;i++) STRIPES[i]=new ReentrantLock(); }
    @FunctionalInterface interface Mutation<T> { T run() throws Exception; }

    static String current(Path object) throws IOException {
        Path marker=marker(object);
        if (!Files.exists(marker)) return "LEGACY";
        String value=Files.readString(marker,StandardCharsets.US_ASCII);
        if (!value.matches("COMMITTED:[a-f0-9-]{36}")) throw new IOException("Managed object write is incomplete");
        return value;
    }

    static <T> T mutate(Path object, Mutation<T> action) throws Exception {
        object=object.toAbsolutePath().normalize();
        if(!ObjectGenerationScope.managed(object.toString()))return action.run();
        Path marker=marker(object);Files.createDirectories(marker.getParent());
        var stripe=STRIPES[(object.hashCode()&Integer.MAX_VALUE)%STRIPES.length];stripe.lock();
        try (var channel=FileChannel.open(marker.resolveSibling(marker.getFileName()+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
             var lock=channel.lock()) {
            // The durable OS lock serializes writers in other JVMs as well as this one.
            String generation=UUID.randomUUID().toString();
            publish(marker,"WRITING:"+generation);
            T result=action.run();
            publish(marker,"COMMITTED:"+generation);
            return result;
        } finally {stripe.unlock();}
    }

    private static Path marker(Path object) {
        object=object.toAbsolutePath().normalize();
        String fileName=object.getFileName().toString();
        if(System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win"))fileName=fileName.toLowerCase(java.util.Locale.ROOT);
        String name=org.apache.commons.codec.digest.DigestUtils.sha256Hex(fileName);
        return object.getParent().resolve(".ms-object-generations").resolve(name);
    }

    private static void publish(Path marker,String value) throws IOException {
        Path temporary=marker.resolveSibling(marker.getFileName()+"."+UUID.randomUUID()+".tmp");
        try {
            try(var channel=FileChannel.open(temporary,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                ByteBuffer bytes=StandardCharsets.US_ASCII.encode(value);while(bytes.hasRemaining())channel.write(bytes);channel.force(true);
            }
            // No non-atomic fallback: a torn generation must never certify partially written bytes.
            Files.move(temporary,marker,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(temporary);}
    }
}
