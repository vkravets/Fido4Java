package org.fidonet.binkp.common.protocol;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.codec.BinkpLog;
import org.fidonet.binkp.common.codec.TrafficCrypter;
import org.fidonet.binkp.common.events.DisconnectedEvent;
import org.fidonet.binkp.common.io.BinkFrame;
import org.fidonet.binkp.common.io.FilesSender;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Binary duplex streams with bounded writes and a frame-boundary crypto transition. */
public final class StreamSession implements Session, AutoCloseable {
    private static final class Write {
        private final byte[] bytes;
        private final CompletableFuture<Void> done = new CompletableFuture<>();

        private Write(byte[] bytes) {
            this.bytes = bytes;
        }
    }

    private final InputStream input;
    private final OutputStream output;
    private final SessionContext context;
    private final String label;
    private final long timeoutMillis;
    private final Runnable abortTransport;
    private final ArrayBlockingQueue<Write> writes = new ArrayBlockingQueue<>(64);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CountDownLatch finished = new CountDownLatch(1);
    private final ScheduledExecutorService timer;
    private volatile boolean closing;
    private volatile long activity = System.nanoTime();
    private volatile Write lastWrite;
    private volatile FilesSender<Session> sender;
    private TrafficCrypter crypter = new TrafficCrypter();
    private boolean started;

    public StreamSession(InputStream input,
                         OutputStream output,
                         SessionContext context,
                         String label,
                         Duration timeout,
                         Runnable abortTransport) {
        this.input = Objects.requireNonNull(input);
        this.output = Objects.requireNonNull(output);
        this.context = Objects.requireNonNull(context);
        this.label = Objects.requireNonNull(label);
        timeoutMillis = timeout.toMillis();
        if (timeoutMillis < 1) {
            throw new IllegalArgumentException("Stream timeout must be positive");
        }
        this.abortTransport = Objects.requireNonNull(abortTransport);
        timer = Executors.newSingleThreadScheduledExecutor(task -> daemon(task,
                                                                          "binkp-stream-timer"));
    }

    public synchronized void start() throws Exception {
        if (started || closed.get()) {
            throw new IllegalStateException("Stream session cannot be restarted");
        }
        started = true;
        Objects.requireNonNull(context.getServerRole(), "Session role is required");
        context.prepareTransfer();
        context.setState(SessionState.STATE_IDLE);
        timer.scheduleWithFixedDelay(this::checkTimeout,
                                     timeoutMillis,
                                     timeoutMillis,
                                     TimeUnit.MILLISECONDS);
        daemon(this::writeLoop, "binkp-stream-writer").start();
        try {
            BinkpSessionEngine.greet(this, context);
            daemon(this::readLoop, "binkp-stream-reader").start();
        } catch (Exception failure) {
            fail(failure);
            throw failure;
        }
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    @Override
    public synchronized void write(Object message) {
        if (closed.get() || closing) {
            throw new UncheckedIOException(new IOException("Stream session is closed"));
        }
        BinkFrame frame = (BinkFrame) message;
        BinkFrame.toBinkData(frame);
        byte[] bytes = ByteBuffer.allocate(frame.getData().length + 2)
                                 .putShort(frame.getDataInfo())
                                 .put(frame.getData())
                                 .array();
        // Snapshot encryption now: queued M_OK must remain plaintext when CRYPT starts.
        crypter.encrypt(bytes, bytes.length);
        Write write = new Write(bytes);
        if (!writes.offer(write)) {
            IOException failure = new IOException("Binkp stream write queue is full");
            throw new UncheckedIOException(failure);
        }
        lastWrite = write;
        BinkpLog.command(context, label, false, frame);
    }

    private void writeLoop() {
        try {
            while (!closed.get()) {
                Write write = writes.poll(100, TimeUnit.MILLISECONDS);
                if (write == null) {
                    if (closing) {
                        break;
                    }
                    continue;
                }
                writeOne(write);
            }
            finish();
        } catch (Exception failure) {
            fail(failure);
        }
    }

    private void writeOne(Write write) throws IOException {
        try {
            output.write(write.bytes);
            output.flush();
            activity = System.nanoTime();
            write.done.complete(null);
        } catch (IOException failure) {
            write.done.completeExceptionally(failure);
            throw failure;
        }
    }

    private void readLoop() {
        try {
            while (!closed.get() && !closing) {
                BinkFrame frame = readFrame();
                BinkpLog.command(context, label, true, frame);
                BinkpSessionEngine.receive(this, context, frame);
                if (context.getState() == SessionState.STATE_END) {
                    close(false);
                }
            }
        } catch (Exception failure) {
            if (context.getState() == SessionState.STATE_END) {
                close(false);
            } else if (!closed.get() && !closing) {
                fail(failure);
            }
        }
    }

    private BinkFrame readFrame() throws IOException {
        byte[] header = readExactly(2);
        short info = ByteBuffer.wrap(header).getShort();
        return new BinkFrame(info, readExactly(info & 0x7fff));
    }

    private byte[] readExactly(int size) throws IOException {
        byte[] bytes = new byte[size];
        int offset = 0;
        while (offset < size) {
            int count = input.read(bytes, offset, size - offset);
            if (count < 0) {
                throw new EOFException("Binkp stream ended before the exchange completed");
            }
            if (count == 0) {
                int value = input.read();
                if (value < 0) {
                    throw new EOFException("Truncated binkp stream");
                }
                bytes[offset++] = (byte) value;
            } else {
                offset += count;
            }
            activity = System.nanoTime();
        }
        crypter.decrypt(bytes, bytes.length);
        return bytes;
    }

    private void checkTimeout() {
        if (!closed.get()
                && System.nanoTime() - activity >= TimeUnit.MILLISECONDS.toNanos(timeoutMillis)) {
            fail(new IOException("Binkp stream I/O timed out"));
        }
    }

    public void fail(Throwable failure) {
        if (!closed.get() && context.getState() != SessionState.STATE_ERR) {
            context.fail(failure);
        }
        close(true);
    }

    @Override
    public void close(boolean immediate) {
        synchronized (this) {
            closing = true;
        }
        if (immediate || !started) {
            try {
                abortTransport.run();
            } finally {
                finish();
            }
        }
    }

    private void finish() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        timer.shutdownNow();
        IOException failure = new IOException("Binkp stream closed");
        Write pending = lastWrite;
        if (pending != null) {
            pending.done.completeExceptionally(failure);
        }
        Write queued;
        while ((queued = writes.poll()) != null) {
            queued.done.completeExceptionally(failure);
        }
        closeStream(output);
        closeStream(input);
        context.closeReceiveStreams();
        context.sendEvent(new DisconnectedEvent(context));
        finished.countDown();
    }

    private void closeStream(AutoCloseable stream) {
        try {
            stream.close();
        } catch (Exception ignored) {
            // Preserve the original protocol failure and complete the remaining cleanup.
        }
    }

    public boolean awaitClosed(Duration timeout) throws InterruptedException {
        return finished.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void awaitWrites() throws IOException, InterruptedException {
        Write pending = lastWrite;
        if (pending == null) {
            return;
        }
        try {
            pending.done.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException failure) {
            IOException error = new IOException("Unable to flush binkp stream", failure);
            fail(error);
            throw error;
        }
    }

    @Override
    public void close() {
        close(true);
    }

    @Override
    public FilesSender<Session> getFileSender() {
        return sender;
    }

    @Override
    public void setFileSender(FilesSender sender) {
        this.sender = sender;
    }

    @Override
    public TrafficCrypter getTrafficCrypter() {
        return crypter;
    }

    @Override
    public void setTrafficCrypter(TrafficCrypter crypter) {
        this.crypter = Objects.requireNonNull(crypter);
    }
}
