import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class message {
    public static int numberOfNodes = 10;

    public static final ThreadLocal<Integer> myId = new ThreadLocal<Integer>();

    private static final ThreadLocal<ConcurrentMap<Integer, ByteArrayOutputStream>> messageAccumulators =
            new ThreadLocal<ConcurrentMap<Integer, ByteArrayOutputStream>>() {
                @Override
                protected ConcurrentMap<Integer, ByteArrayOutputStream> initialValue() {
                    return new ConcurrentHashMap<>();
                }
            };

    private static final ConcurrentMap<Integer, Queue> messageQueues = new ConcurrentHashMap<>();

    private static final ThreadLocal<ConcurrentMap<Integer, ByteArrayInputStream>> messageReaders =
            new ThreadLocal<ConcurrentMap<Integer, ByteArrayInputStream>>() {
                @Override
                protected ConcurrentMap<Integer, ByteArrayInputStream> initialValue() {
                    return new ConcurrentHashMap<>();
                }
            };

    // The number of nodes on which the solution is running.
    public static int NumberOfNodes() {
        return numberOfNodes;
    }

    // The index (in the range [0 .. NumberOfNodes()-1]) of the node on which this
    // process is running.
    public static int MyNodeId() {
        return myId.get();
    }

    // In all the functions below, if "target" or "source" is not in the valid
    // range, the behaviour is undefined.

    // The library internally has a message buffer for each of the nodes in
    // [0 .. NumberOfNodes()-1]. It accumulates the message in such a buffer through
    // the "Put" methods.

    // Append "value" to the message that is being prepared for the node with id
    // "target". The "Int" in PutInt is interpreted as 32 bits, regardless of
    // whether the actual int type will be 32 or 64 bits.
    public static void PutChar(int target, char value) {
        DataOutputStream s = getOutStream(target);
        try {
            s.writeChar(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void PutInt(int target, int value) {
        DataOutputStream s = getOutStream(target);
        try {
            s.writeInt(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void PutLL(int target, long value) {
        DataOutputStream s = getOutStream(target);
        try {
            s.writeLong(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static DataOutputStream getOutStream(int target) {
        return new DataOutputStream(getByteOutStream(target));
    }

    private static ByteArrayOutputStream getByteOutStream(int target) {
        return messageAccumulators.get().computeIfAbsent(target
                , $ -> new ByteArrayOutputStream());
    }

    // Send the message that was accumulated in the appropriate buffer to the
    // "target" instance, and clear the buffer for this instance.
    //
    // This method is non-blocking - that is, it does not wait for the receiver to
    // call "Receive", it returns immediately after sending the message.
    public static void Send(int target) {
        ByteArrayOutputStream s = getByteOutStream(target);
        Queue q = getQueue(target);
        QueueItem qi = new QueueItem();
        qi.source = MyNodeId();
        qi.data = s.toByteArray();
        s.reset();
        q.Enqueue(qi);
    }

    private static Queue getQueue(int target) {
        return messageQueues.computeIfAbsent(target, $ -> new Queue());
    }

    // The library also has a receiving buffer for each instance. When you call
    // "Receive" and retrieve a message from an instance, the buffer tied to this
    // instance is overwritten. You can then retrieve individual parts of the
    // message through the Get* methods. You must retrieve the contents of the
    // message in the order in which they were appended.
    //
    // This method is blocking - if there is no message to receive, it will wait for
    // the message to arrive.
    //
    // You can call Receive(-1) to retrieve a message from any source, or with
    // source in [0 .. NumberOfNodes()-1] to retrieve a message from a particular
    // source.
    //
    // It returns the number of the instance which sent the message (which is equal
    // to source, unless source is -1).
    public static int Receive(int source) {
        Queue q = getQueue(MyNodeId());
        QueueItem qi = source != -1 ? q.Take(source) : q.TakeAny();
        source = qi.source;
        messageReaders.get().put(source, new ByteArrayInputStream(qi.data));
        return source;
    }

    private static DataInputStream getInStream(int source) {
        return new DataInputStream(getByteInStream(source));
    }

    private static ByteArrayInputStream getByteInStream(int source) {
        return messageReaders.get().get(source);
    }

    // Each of these methods returns and consumes one item from the buffer of the
    // appropriate instance. You must call these methods in the order in which the
    // elements were appended to the message (so, for instance, if the message was
    // created with PutChar, PutChar, PutLL, you must call GetChar, GetChar, GetLL
    // in this order).
    // If you call them in different order, or you call a Get* method after
    // consuming all the contents of the buffer, behaviour is undefined.
    // The "Int" in GetInt is interpreted as 32 bits, regardless of whether the
    // actual int type will be 32 or 64 bits.
    public static char GetChar(int source) {
        DataInputStream s = getInStream(source);
        try {
            return s.readChar();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static int GetInt(int source) {
        DataInputStream s = getInStream(source);
        try {
            return s.readInt();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static long GetLL(int source) {
        DataInputStream s = getInStream(source);
        try {
            return s.readLong();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class QueueItem {
        public int source;
        public byte[] data;
    }

    private static class Queue {
        private ReentrantLock lock = new ReentrantLock();
        private Condition notEmpty = lock.newCondition();
        private List<QueueItem> items = new ArrayList<>();

        public void Enqueue(QueueItem item) {
            lock.lock();
            try {
                items.add(item);
                notEmpty.signalAll();
            } finally {
                lock.unlock();
            }
        }

        public QueueItem TakeAny() {
            lock.lock();
            try {
                while (items.isEmpty()) {
                    notEmpty.await();
                }
                QueueItem result = items.get(0);
                items.remove(0);
                return result;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            finally {
                lock.unlock();
            }
        }

        public QueueItem Take(int source) {
            lock.lock();
            try {
                for (;;) {
                    while (items.isEmpty()) {
                        notEmpty.await();
                    }
                    for (int i = 0; i < items.size(); ++i) {
                        QueueItem qi = items.get(i);
                        if (qi.source == source) {
                            items.remove(i);
                            return qi;
                        }
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            finally {
                lock.unlock();
            }
        }
    }
}
