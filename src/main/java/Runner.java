import java.util.concurrent.*;

public class Runner {
    public static void main(String[] args) {
        int nodes = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        System.out.println(String.format("Running with %d nodes", nodes));
        message.numberOfNodes = nodes;
        Thread[] threads = new Thread[nodes];
        String[] params = new String[0];
        for (int i = 0; i < nodes; ++i) {
            int iLocal = i;
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    message.myId.set(iLocal);
                    Main.main(params);
                }
            }, String.format("Node %s", iLocal));
            threads[i].start();
        }
        try {
            for (int i = 0; i < nodes; ++i) {
                threads[i].join(2000 * 60);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
